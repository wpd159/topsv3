#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
backfill_helper="${repo_root}/scripts/deploy/executar-backfill-previews-production.sh"
backfill_test="${repo_root}/scripts/deploy/testar-backfill-previews-production.sh"
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
  local before_client="${command%%psql*}"
  [[ " ${before_client} " == *" -i "* ]]
}

mapfile -d '' -t deploy_sources < <(
  find "${repo_root}/.github/workflows" "${script_dir}" -type f \
    \( -name '*.yml' -o -name '*.yaml' -o -name '*.sh' \) \
    ! -name 'testar-*' -print0
)
[[ "${#deploy_sources[@]}" -gt 0 ]] || fail "fontes de deploy ausentes"

while IFS= read -r command; do
  if is_psql_command "$command"; then
    has_interactive_exec "$command" \
      && fail "psql com --command ainda usa docker exec interativo"
    [[ "$command" == *"</dev/null"* ]] \
      || fail "psql com --command sem stdin fechado"
  fi
  if [[ "$command" == *"docker exec"* ]] && has_interactive_exec "$command"; then
    if [[ "$command" != *"<"* && "$command" != *"| docker exec"* ]]; then
      fail "docker exec interativo sem entrada explicitamente controlada"
    fi
  fi
  if [[ "$command" == *"docker compose"* && "$command" == *" run "* ]]; then
    if [[ "$command" != *"</dev/null"* && "$command" != *"| docker compose"* ]]; then
      fail "docker compose run herda o stdin geral"
    fi
  fi
  if [[ "$command" == *'ssh "${SSH_OPTS[@]}" "${SSH_TARGET}"'* \
      || "$command" == *'ssh "${ssh_opts[@]}" "${ssh_target}"'* ]]; then
    if [[ "$command" != *"</dev/null"* && "$command" != *"<<"* && "$command" != *"| ssh"* ]]; then
      fail "sessao SSH sem entrada explicitamente controlada"
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

grep -Fq '</dev/null >"${RAW_OUTPUT}"' "${backfill_helper}"
grep -Fq 'ARCHIVE_SHA256="$(sha256sum -- "${ARCHIVE}"' "${workflow}"
grep -Fq 'test "$(sha256sum -- "${incoming}"' "${workflow}"
echo "PASS: transporte_release_com_sha"

target_line="$(grep -nF '      - name: Validate canonical production target' "${workflow}" | tail -1 | cut -d: -f1)"
indexnow_line="$(grep -nF '      - name: Synchronize IndexNow key in production runtime' "${workflow}" | cut -d: -f1)"
release_upload_line="$(grep -nF '      - name: Upload immutable release' "${workflow}" | cut -d: -f1)"
(( target_line < indexnow_line && target_line < release_upload_line )) \
  || fail "target guard nao antecede todas as mutacoes remotas"
echo "PASS: target_guard_antes_upload"

mkdir -p "${temp_dir}/fixtures"
cat > "${temp_dir}/fixtures/read-until-eof.sh" <<'CHILD'
#!/usr/bin/env bash
set -euo pipefail
bytes="$(cat | wc -c | tr -d ' ')"
test "${bytes}" -eq 0
printf 'CHILD_STDIN_EOF=SIM\n'
CHILD
cat > "${temp_dir}/fixtures/remote-success.sh" <<'REMOTE_SUCCESS'
#!/usr/bin/env bash
set -euo pipefail
: "${CHILD_READER:?}"
printf 'marcador_anterior\n'
"${CHILD_READER}"
printf 'marcador_posterior\n'
REMOTE_SUCCESS
cat > "${temp_dir}/fixtures/child-failure.sh" <<'CHILD_FAILURE'
#!/usr/bin/env bash
set -euo pipefail
cat >/dev/null
printf 'DIAGNOSTICO_PRESERVADO=SIM\n'
exit 42
CHILD_FAILURE
cat > "${temp_dir}/fixtures/remote-failure.sh" <<'REMOTE_FAILURE'
#!/usr/bin/env bash
set -euo pipefail
: "${CHILD_FAILURE:?}"
printf 'falha_anterior\n'
"${CHILD_FAILURE}"
printf 'falha_posterior\n'
REMOTE_FAILURE
chmod +x "${temp_dir}/fixtures/"*.sh

success_root="${temp_dir}/success-runs"
mkdir -p "${success_root}"
for run in $(seq 1 5); do
  run_dir="${success_root}/${run}"
  mkdir -p "${run_dir}"
  CHILD_READER="${temp_dir}/fixtures/read-until-eof.sh" \
    bash "${temp_dir}/fixtures/remote-success.sh" </dev/null > "${run_dir}/output.log"
  grep -qx 'marcador_anterior' "${run_dir}/output.log"
  grep -qx 'CHILD_STDIN_EOF=SIM' "${run_dir}/output.log"
  grep -qx 'marcador_posterior' "${run_dir}/output.log"
  rm -rf -- "${run_dir}"
done
test -z "$(find "${success_root}" -mindepth 1 -print -quit)"
echo "PASS: caminho_sucesso_5_5"
echo "PASS: processo_filho_recebe_eof"
echo "PASS: marcador_posterior"

production_state="${temp_dir}/production.state"
failure_log="${temp_dir}/failure.log"
printf 'PRODUCTION=stable\n' > "${production_state}"
if CHILD_FAILURE="${temp_dir}/fixtures/child-failure.sh" \
  bash "${temp_dir}/fixtures/remote-failure.sh" </dev/null > "${failure_log}" 2>&1; then
  fail "falha do filho nao interrompeu o script"
fi
grep -qx 'PRODUCTION=stable' "${production_state}"
grep -qx 'DIAGNOSTICO_PRESERVADO=SIM' "${failure_log}"
if grep -qx 'falha_posterior' "${failure_log}"; then
  fail "script prosseguiu depois da falha do filho"
fi
echo "PASS: falha_filho_interrompe"
echo "PASS: producao_e_diagnostico_preservados"

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
  local source="$2"
  local occurrence="${3:-first}"
  local matches
  matches="$(grep -nF -- "$needle" "$source" | cut -d: -f1)"
  [[ -n "${matches}" ]] || fail "marcador de ordem ausente: ${needle}"
  if [ "${occurrence}" = last ]; then
    printf '%s\n' "${matches}" | tail -1
  else
    printf '%s\n' "${matches}" | head -1
  fi
}

backup_line="$(line_number 'bash "${backup_producer}"' "${workflow}")"
flyway_line="$(line_number 'flyway migrate </dev/null' "${workflow}")"
flyway_gate_line="$(line_number 'bash "${flyway_gate}" after' "${workflow}")"
startup_line="$(line_number 'application_started=1' "${workflow}")"
health_line="$(line_number 'test "${healthy}" -eq 1' "${workflow}")"
switch_line="$(line_number 'mv -Tf "${current_link}"' "${workflow}")"

(( backup_line < flyway_line )) || fail "backup nao antecede Flyway"
(( flyway_line < flyway_gate_line && flyway_gate_line < startup_line )) \
  || fail "Flyway validado fora da ordem de startup"
(( health_line < switch_line )) || fail "troca da release antecede health/readiness"
grep -Fq 'api/health/readiness' "${workflow}" || fail "readiness ausente"
grep -Fq 'run_fixture apply_idempotent APPLY 0' "${backfill_test}"
grep -Fq 'updated=0 unchanged=1344' "${backfill_test}"
echo "PASS: backup_antes_flyway"
echo "PASS: flyway_antes_startup"
echo "PASS: health_antes_troca"
echo "PASS: apply_idempotente_sem_delta"

if find "${temp_dir}" -maxdepth 1 -name 'transport-residual-*' -print -quit | grep -q .; then
  fail "recurso temporario residual"
fi
echo "PASS: residuos_zero"
echo "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
