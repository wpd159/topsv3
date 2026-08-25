#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
temp_dir="$(mktemp -d)"
trap 'rm -rf -- "${temp_dir}"' EXIT

fail() {
  echo "FALHA: $*" >&2
  exit 1
}

logical_commands() {
  awk '
    {
      sub(/\r$/, "")
      line = $0
      if (buffer == "") {
        buffer = line
      } else {
        buffer = buffer " " line
      }
      if (line ~ /\\[[:space:]]*$/) {
        sub(/\\[[:space:]]*$/, "", buffer)
      } else {
        print buffer
        buffer = ""
      }
    }
    END {
      if (buffer != "") {
        print buffer
      }
    }
  ' "$@"
}

is_psql_command() {
  local command="$1"
  [[ "$command" == *"docker exec"* && "$command" == *"psql"* ]] || return 1
  [[ "$command" =~ (^|[[:space:]])-c([[:space:]]|$) || "$command" == *"--command"* ]]
}

has_interactive_exec() {
  local command="$1"
  local before_psql="${command%%psql*}"
  [[ " ${before_psql} " == *" -i "* ]]
}

mapfile -d '' -t deploy_sources < <(
  find "${repo_root}/.github/workflows" "$script_dir" -type f \
    \( -name '*.yml' -o -name '*.yaml' -o -name '*.sh' \) -print0
)
[[ "${#deploy_sources[@]}" -gt 0 ]] || fail "fontes de deploy ausentes"

while IFS= read -r command; do
  if is_psql_command "$command" && has_interactive_exec "$command"; then
    fail "psql com --command ainda usa docker exec interativo"
  fi
  if [[ "$command" == *"docker exec"* ]] && has_interactive_exec "$command"; then
    if [[ "$command" != *"<"* && "$command" != *"| docker exec"* ]]; then
      fail "docker exec interativo sem entrada explicitamente controlada"
    fi
  fi
done < <(logical_commands "${deploy_sources[@]}")

flyway_reader="$(sed -n '/read_flyway_state() {/,/^          }/p' "$workflow")"
[[ -n "$flyway_reader" ]] || fail "read_flyway_state ausente"
[[ "$flyway_reader" != *"docker exec -i"* ]] || fail "read_flyway_state ainda usa -i"
[[ "$flyway_reader" == *"--no-psqlrc"* ]] || fail "read_flyway_state sem --no-psqlrc"
[[ "$flyway_reader" == *"--set=ON_ERROR_STOP=1"* ]] || fail "read_flyway_state sem ON_ERROR_STOP"
[[ "$flyway_reader" == *"--command"* ]] || fail "read_flyway_state sem --command"
[[ "$flyway_reader" == *"</dev/null"* ]] || fail "read_flyway_state sem stdin isolado"

mkdir -p "${temp_dir}/bin"
cat > "${temp_dir}/bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
interactive=false
for argument in "$@"; do
  if [[ "$argument" == "-i" ]]; then
    interactive=true
  fi
done
if [[ "$interactive" == true ]]; then
  cat >/dev/null
fi
if [[ "${FAKE_PSQL_FAIL:-false}" == true ]]; then
  exit 42
fi
FAKE_DOCKER
chmod +x "${temp_dir}/bin/docker"

safe_log="${temp_dir}/safe.log"
PATH="${temp_dir}/bin:${PATH}" bash -s > "$safe_log" <<'SAFE_REMOTE'
set -euo pipefail
printf 'marcador_anterior\n'
docker exec postgres psql --no-psqlrc --set=ON_ERROR_STOP=1 \
  --command 'SELECT 1' </dev/null
printf 'marcador_posterior\n'
SAFE_REMOTE
grep -qx 'marcador_anterior' "$safe_log" || fail "marcador anterior ausente"
grep -qx 'marcador_posterior' "$safe_log" || fail "marcador posterior nao executou"
echo "PASS: marcador_posterior"

interactive_flag="-i"
unsafe_log="${temp_dir}/unsafe-control.log"
PATH="${temp_dir}/bin:${PATH}" INTERACTIVE_FLAG="$interactive_flag" \
  bash -s > "$unsafe_log" <<'UNSAFE_REMOTE'
set -euo pipefail
printf 'controle_anterior\n'
docker exec "${INTERACTIVE_FLAG}" postgres psql --command 'SELECT 1'
printf 'controle_posterior\n'
UNSAFE_REMOTE
grep -qx 'controle_anterior' "$unsafe_log" || fail "controle negativo nao iniciou"
if grep -qx 'controle_posterior' "$unsafe_log"; then
  fail "controle negativo nao reproduziu o consumo do heredoc"
fi
echo "PASS: consumo_indesejado_reproduzido"

failure_log="${temp_dir}/failure.log"
if PATH="${temp_dir}/bin:${PATH}" FAKE_PSQL_FAIL=true \
  bash -s > "$failure_log" 2>&1 <<'FAIL_REMOTE'
set -euo pipefail
printf 'falha_anterior\n'
docker exec postgres psql --no-psqlrc --set=ON_ERROR_STOP=1 \
  --command 'SELECT invalido' </dev/null
printf 'falha_posterior\n'
FAIL_REMOTE
then
  fail "falha simulada do psql nao interrompeu o script"
fi
if grep -qx 'falha_posterior' "$failure_log"; then
  fail "script prosseguiu depois da falha simulada do psql"
fi
echo "PASS: falha_psql_interrompe"

line_number() {
  local needle="$1"
  local occurrence="${2:-first}"
  local matches
  matches="$(grep -nF -- "$needle" "$workflow" | cut -d: -f1)"
  [[ -n "$matches" ]] || fail "marcador de ordem ausente: ${needle}"
  if [[ "$occurrence" == last ]]; then
    printf '%s\n' "$matches" | tail -n 1
  else
    printf '%s\n' "$matches" | head -n 1
  fi
}

backup_line="$(line_number 'bash "${backup_producer}"')"
flyway_line="$(line_number 'flyway migrate </dev/null')"
flyway_gate_line="$(line_number 'bash "${flyway_gate}" after')"
startup_line="$(line_number 'application_started=1')"
health_line="$(line_number 'test "${healthy}" -eq 1')"
switch_line="$(line_number 'mv -Tf "${current_link}"')"

(( backup_line < flyway_line )) || fail "backup nao antecede Flyway"
echo "PASS: backup_antes_flyway"
(( flyway_line < flyway_gate_line && flyway_gate_line < startup_line )) \
  || fail "Flyway validado fora da ordem de startup"
echo "PASS: flyway_antes_startup"
(( health_line < switch_line )) || fail "troca da release antecede health/readiness"
grep -Fq 'api/health/readiness' "$workflow" || fail "readiness ausente"
echo "PASS: health_antes_troca"

echo "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
