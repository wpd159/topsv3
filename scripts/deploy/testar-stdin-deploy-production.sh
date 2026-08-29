#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
remote_deploy="${repo_root}/scripts/deploy/executar-deploy-remoto-production.sh"
remote_invoker="${repo_root}/scripts/deploy/invocar-deploy-remoto-production.sh"
staging_manager="${repo_root}/scripts/deploy/gerenciar-staging-controlador-production.sh"
staging_test="${repo_root}/scripts/deploy/testar-staging-controlador-production.sh"
backfill_helper="${repo_root}/scripts/deploy/executar-backfill-previews-production.sh"
backfill_test="${repo_root}/scripts/deploy/testar-backfill-previews-production.sh"
atomic_helper="${repo_root}/scripts/deploy/ativar-release-atomica-production.sh"
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

if grep -Eqs 's[s]h[^[:cntrl:]]*b[a]sh[[:space:]]+-s' "${deploy_sources[@]}"; then
  fail "execucao remota ainda transmite script pelo stdin"
fi
if grep -Eqs 's[s]h[^[:cntrl:]]*<<' "${deploy_sources[@]}"; then
  fail "heredoc executavel via SSH ainda presente"
fi
if grep -Eqs '[|][[:space:]]*ssh[[:space:]]' "${deploy_sources[@]}"; then
  fail "pipe geral para SSH ainda presente"
fi

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
    [[ "$command" == *"</dev/null"* ]] || fail "sessao SSH sem stdin fechado"
  fi
done < <(logical_commands "${deploy_sources[@]}")

grep -Fq 'Upload verified remote deploy controller' "${workflow}"
grep -Fq 'REMOTE_DEPLOY_SCRIPT_SHA256=' "${workflow}"
grep -Fq 'gerenciar-staging-controlador-production.sh' "${workflow}"
grep -Fq 'prepare "${GITHUB_RUN_ID}" "${GITHUB_RUN_ATTEMPT}" "${GITHUB_SHA}"' "${workflow}"
grep -Fq 'upload "${GITHUB_RUN_ID}" "${GITHUB_RUN_ATTEMPT}" "${GITHUB_SHA}"' "${workflow}"
grep -Fq 'cleanup "${GITHUB_RUN_ID}" "${GITHUB_RUN_ATTEMPT}" "${GITHUB_SHA}"' "${workflow}"
grep -Fq '.cache/topsdojob-deploy/' "${workflow}"
if grep -Fq 'scp ' "${workflow}"; then
  fail "workflow ainda faz upload fora do helper de staging"
fi
if grep -Fq '/opt/topsv3/production/incoming/deploy-remoto-' "${workflow}" "${remote_invoker}"; then
  fail "controlador ainda usa o diretorio root-owned anterior"
fi
grep -Fq 'umask 077' "${staging_manager}"
grep -Fq 'mkdir -m 0700' "${staging_manager}"
grep -Fq 'test ! -L' "${staging_manager}"
grep -Fq 'scp "${scp_opts[@]}" "${source}" "${ssh_target}:${remote_rel}" </dev/null' "${staging_manager}"
grep -Fq 'bash ./scripts/deploy/invocar-deploy-remoto-production.sh' "${workflow}"
grep -Fq 'actual=\$(sha256sum -- \"\${script}\"' "${remote_invoker}"
grep -Fq 'bash \"\${script}\"' "${remote_invoker}"
grep -Fq "remote_command+=' </dev/null'" "${remote_invoker}"
grep -Fq 'assert_stdin_closed' "${remote_deploy}"
grep -Fq '</dev/null >"${RAW_OUTPUT}"' "${backfill_helper}"
echo "PASS: transporte_remoto_em_arquivo_com_sha"

target_line="$(grep -nF '      - name: Validate canonical production target' "${workflow}" | tail -1 | cut -d: -f1)"
controller_upload_line="$(grep -nF '      - name: Upload verified remote deploy controller' "${workflow}" | cut -d: -f1)"
release_upload_line="$(grep -nF '      - name: Upload immutable release' "${workflow}" | cut -d: -f1)"
(( target_line < controller_upload_line && controller_upload_line < release_upload_line )) \
  || fail "target guard nao antecede todos os uploads"
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

mkdir -p "${temp_dir}/fake-bin" "${temp_dir}/fake-home/.ssh"
touch "${temp_dir}/fake-home/.ssh/topsdojob_production" "${temp_dir}/fake-home/.ssh/known_hosts"
cat > "${temp_dir}/fake-bin/s""sh" <<'FAKE_SSH'
#!/usr/bin/env bash
set -euo pipefail
if IFS= read -r -t 0.05 unexpected; then
  printf 'stdin inesperado no invocador\n' >&2
  exit 9
fi
printf '%s\n' "$*" > "${SSH_CAPTURE}"
printf 'INVOKER_STDIN_EOF=SIM\n' >> "${SSH_CAPTURE}"
FAKE_SSH
chmod +x "${temp_dir}/fake-bin/s""sh"
sha64="$(printf 'a%.0s' {1..64})"
release40="$(printf 'b%.0s' {1..40})"
remote_path=".cache/topsdojob-deploy/123-1-${release40}/deploy-remoto.sh"
SSH_CAPTURE="${temp_dir}/ssh.capture" \
TOPSDOJOB_PROD_SSH_HOST=example.invalid \
TOPSDOJOB_PROD_SSH_USER=topsdojob \
TOPSDOJOB_PROD_SSH_PORT=22 \
HOME="${temp_dir}/fake-home" \
PATH="${temp_dir}/fake-bin:${PATH}" \
  bash "${remote_invoker}" "${remote_path}" "${sha64}" prerequisites
grep -Fq 'sha256sum -- "${script}"' "${temp_dir}/ssh.capture"
grep -Fq '</dev/null' "${temp_dir}/ssh.capture"
grep -Fq 'INVOKER_STDIN_EOF=SIM' "${temp_dir}/ssh.capture"
echo "PASS: invocador_fecha_stdin_e_valida_sha"

bash "${staging_test}"

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

backup_line="$(line_number 'bash "${backup_producer}"' "${remote_deploy}")"
flyway_line="$(line_number 'flyway migrate </dev/null' "${remote_deploy}")"
flyway_gate_line="$(line_number 'bash "${flyway_gate}" after' "${remote_deploy}")"
initial_line="$(line_number 'for preview_backfill_mode in PLAN APPLY VALIDATE; do' "${remote_deploy}")"
after_child_line="$(line_number 'REMOTE_DEPLOY_MARKER=after_initial_backfill' "${remote_deploy}")"
startup_line="$(line_number 'bash "${atomic_activator}"' "${remote_deploy}")"
candidate_gate_line="$(line_number 'stage_or_abort verify_candidate' "${atomic_helper}" last)"
delta_apply_line="$(line_number 'stage_or_abort run_preview_backfill APPLY delta' "${atomic_helper}")"
switch_line="$(line_number 'stage_or_abort switch_gateway' "${atomic_helper}")"

(( backup_line < flyway_line )) || fail "backup nao antecede Flyway"
(( flyway_line < flyway_gate_line && flyway_gate_line < initial_line )) \
  || fail "Flyway ou backfill inicial fora de ordem"
(( initial_line < after_child_line && after_child_line < startup_line )) \
  || fail "comandos posteriores ao backfill nao sao alcancaveis"
(( candidate_gate_line < delta_apply_line && delta_apply_line < switch_line )) \
  || fail "candidate gates, delta e switch fora de ordem"
grep -Fq 'run_fixture apply_idempotent APPLY 0' "${backfill_test}"
grep -Fq 'updated=0 unchanged=1344' "${backfill_test}"
echo "PASS: backup_antes_flyway"
echo "PASS: plan_apply_validate_e_candidate_gates_alcancados"
echo "PASS: apply_idempotente_sem_delta"

if find "${temp_dir}" -maxdepth 1 -name 'transport-residual-*' -print -quit | grep -q .; then
  fail "recurso temporario residual"
fi
echo "PASS: residuos_zero"
echo "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
