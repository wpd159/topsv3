#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
operation_helper="${script_dir}/proteger-operacao-production.sh"
[[ -f "$operation_helper" ]] || { echo "FALHA: helper de operacao ausente" >&2; exit 1; }
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

flyway_reader="$(awk '
  { sub(/\r$/, "") }
  /^[[:space:]]*read_flyway_state\(\) \{$/ {
    match($0, /^[[:space:]]*/)
    indentation = substr($0, 1, RLENGTH)
    reading = 1
  }
  reading {
    print
    if ($0 ~ "^" indentation "}[[:space:]]*$") exit
  }
' "$workflow")"
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

reader_failure_log="${temp_dir}/reader-failure.log"
reader_status=0
{
  printf '%s\n' 'set -euo pipefail' 'db=fixture' 'user=fixture' "$flyway_reader"
  printf '%s\n' 'flyway_before_state="$(read_flyway_state 053)"'
  printf '%s\n' 'printf "leitura_flyway_prosseguiu\n"'
} | PATH="${temp_dir}/bin:${PATH}" FAKE_PSQL_FAIL=true bash -s > "$reader_failure_log" 2>&1 \
  || reader_status=$?
[[ "$reader_status" -eq 42 ]] || fail "funcao Flyway real nao preservou o erro SQL 42"
if grep -qx 'leitura_flyway_prosseguiu' "$reader_failure_log"; then
  fail "atribuicao da leitura Flyway real prosseguiu apos erro SQL"
fi
grep -Fq 'flyway_before_state="$(read_flyway_state "${expected_flyway}")"' "$workflow" \
  || fail "leitura Flyway anterior nao propaga falha da funcao"
grep -Fq 'flyway_after_state="$(read_flyway_state "${expected_flyway}")"' "$workflow" \
  || fail "leitura Flyway posterior nao propaga falha da funcao"
echo "PASS: funcao_flyway_real_propaga_erro"

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
begin_line="$(line_number 'op_begin "${deploy_root}" "${release_sha}"')"
baseline_line="$(line_number 'op_smoke "${previous_sha}"')"
expected_indexnow_line="$(line_number 'op_expect_indexnow "${indexnow_key}"')"
indexnow_line="$(line_number 'op_run mutating docker run --rm -i --pull never')"
build_line="$(line_number 'op_run mutating "${compose[@]}" build backend frontend')"
expected_candidate_line="$(line_number 'op_expect_candidate')"
startup_phase_line="$(line_number 'op_phase ACTIVATING')"
startup_line="$(line_number 'op_run mutating "${compose[@]}" up -d --no-deps --force-recreate backend frontend gateway')"
health_line="$(line_number 'op_smoke "${release_sha}"')"
switch_line="$(line_number 'mv -Tf "${current_link}"')"
final_runtime_line="$(line_number "grep -qx 'SEARCH_INDEXING_MODE=public'")"
final_logs_line="$(line_number 'IMPORTACAO_.*(INICIO|EXECUTADA)')"
finish_line="$(line_number '          op_finish')"

(( begin_line < baseline_line && baseline_line < expected_indexnow_line && expected_indexnow_line < indexnow_line && indexnow_line < backup_line )) \
  || fail "baseline ou primeira mutacao de configuracao fora da posse compartilhada"
echo "PASS: indexnow_sob_posse"
(( build_line < expected_candidate_line && expected_candidate_line < startup_phase_line )) \
  || fail "identidades candidatas nao foram fixadas apos build e antes da ativacao"
echo "PASS: identidades_antes_ativacao"
(( backup_line < flyway_line )) || fail "backup nao antecede Flyway"
echo "PASS: backup_antes_flyway"
(( flyway_line < flyway_gate_line && flyway_gate_line < startup_phase_line && startup_phase_line < startup_line )) \
  || fail "Flyway validado fora da ordem de startup"
echo "PASS: flyway_antes_startup"
(( startup_line < health_line && health_line < switch_line )) || fail "troca da release antecede health/readiness"
grep -Fq 'api/health/readiness' "$operation_helper" || fail "readiness ausente do smoke compartilhado"
echo "PASS: health_antes_troca"
(( switch_line < final_runtime_line && final_runtime_line < finish_line && final_logs_line < finish_line )) \
  || fail "checks finais fora da operacao protegida"
[[ "$(grep -Ec '^[[:space:]]+op_begin[[:space:]]' "$workflow")" -eq 1 ]] \
  || fail "mais de uma entrada de operacao no workflow"
[[ "$(grep -Ec '^[[:space:]]+op_finish[[:space:]]*$' "$workflow")" -eq 1 ]] \
  || fail "conclusao protegida ausente ou duplicada"
if grep -Eq '^[[:space:]]+- name: (Smoke production release|Synchronize IndexNow key in production runtime)[[:space:]]*$' "$workflow"; then
  fail "smoke critico ou mutacao IndexNow ainda fora da operacao protegida"
fi
echo "PASS: smoke_final_sob_posse"
grep -Fq 'tar -tzf "${ARCHIVE}" | grep -Fx '\''./scripts/deploy/proteger-operacao-production.sh'\''' "$workflow" \
  || fail "empacotamento nao exige o helper compartilhado"
echo "PASS: helper_no_empacotamento"

echo "CONTRACT_SCOPE=STATIC_ORDER_AND_STDIN_REQUIRES_OPERATIONAL_GATES"
echo "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
