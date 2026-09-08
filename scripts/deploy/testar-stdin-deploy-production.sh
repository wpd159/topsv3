#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
operation_helper="${script_dir}/proteger-operacao-production.sh"
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
indexnow_line="$(grep -nF '          op_phase CONFIGURING' "${workflow}" | cut -d: -f1)"
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
startup_line="$(line_number 'op_phase ACTIVATING' "${workflow}")"
health_line="$(line_number 'op_smoke "${release_sha}"' "${workflow}")"
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
begin_line="$(line_number 'op_begin "${deploy_root}" "${release_sha}"' "${workflow}")"
baseline_line="$(line_number 'op_smoke "${previous_sha}"' "${workflow}")"
expected_indexnow_line="$(line_number 'op_expect_indexnow "${indexnow_key}"' "${workflow}")"
indexnow_line="$(line_number 'op_run mutating docker run --rm -i --pull never' "${workflow}")"
build_line="$(line_number 'op_run mutating "${compose[@]}" build backend frontend' "${workflow}")"
expected_candidate_line="$(line_number 'op_expect_candidate' "${workflow}")"
startup_phase_line="$(line_number 'op_phase ACTIVATING' "${workflow}")"
startup_line="$(line_number 'op_run mutating "${compose[@]}" up -d --no-deps --force-recreate backend frontend gateway' "${workflow}")"
health_line="$(line_number 'op_smoke "${release_sha}"' "${workflow}")"
switch_line="$(line_number 'mv -Tf "${current_link}"' "${workflow}")"
final_runtime_line="$(line_number "grep -qx 'SEARCH_INDEXING_MODE=public'" "${workflow}")"
final_logs_line="$(line_number 'IMPORTACAO_.*(INICIO|EXECUTADA)' "${workflow}")"
finish_line="$(line_number '          op_finish' "${workflow}")"

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


# Execute the actual versioned guards against exclusively local boundaries.
extract_run() {
  awk -v wanted="$1" '
    /^      - name: / { selected = ($0 == "      - name: " wanted); running=0 }
    selected && /^        run: \|$/ {running=1; next}
    running && /^          / {print substr($0,11)}
  ' "$workflow"
}
guard_dir="${temp_dir}/guards"
mkdir -p "${guard_dir}/bin"
extract_run 'Validate preflight inputs and secrets' > "${guard_dir}/inputs.sh"
extract_run 'Validate canonical production target' > "${guard_dir}/target.sh"
[[ -s "${guard_dir}/inputs.sh" && -s "${guard_dir}/target.sh" ]] || fail 'guards reais ausentes'
export REQUESTED_MODE=deploy DEPLOY_SHA=1111111111111111111111111111111111111111
export GITHUB_SHA="$DEPLOY_SHA" CONFIRMATION=DEPLOY_PRODUCTION
export TOPSDOJOB_PROD_SSH_HOST=fixture.invalid TOPSDOJOB_PROD_SSH_USER=topsdojob TOPSDOJOB_PROD_SSH_PORT=22
export TOPSDOJOB_PROD_SSH_IDENTITY=CHANGE_ME TOPSDOJOB_PROD_SSH_HOST_KEY=CHANGE_ME
export TOPSDOJOB_PROD_TARGET_SHA256=1111111111111111111111111111111111111111111111111111111111111111
bash "${guard_dir}/inputs.sh"
if DEPLOY_SHA=2222222222222222222222222222222222222222 bash "${guard_dir}/inputs.sh"; then fail 'SHA divergente passou pelo preflight'; fi
if CONFIRMATION=NOT_AUTHORIZED bash "${guard_dir}/inputs.sh"; then fail 'confirmacao incorreta passou pelo preflight'; fi
if TOPSDOJOB_PROD_SSH_USER=not_the_deploy_user bash "${guard_dir}/inputs.sh"; then fail 'usuario fora do target passou pelo preflight'; fi
echo 'PASS: guards_reais_sha_confirmacao_usuario'
cat > "${guard_dir}/bin/ssh" <<'TARGET_BOUNDARY'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *sha256sum* ]]; then
  printf '%s\n' "$GUARD_TEST_TARGET_HASH"
else
  [[ "$GUARD_TEST_MARKER" == production ]] || exit 42
fi
TARGET_BOUNDARY
chmod +x "${guard_dir}/bin/ssh"
export GUARD_TEST_TARGET_HASH="$TOPSDOJOB_PROD_TARGET_SHA256" GUARD_TEST_MARKER=production
PATH="${guard_dir}/bin:${PATH}" bash "${guard_dir}/target.sh"
if PATH="${guard_dir}/bin:${PATH}" GUARD_TEST_TARGET_HASH=2222222222222222222222222222222222222222222222222222222222222222 bash "${guard_dir}/target.sh"; then fail 'target hash divergente aceito'; fi
if PATH="${guard_dir}/bin:${PATH}" GUARD_TEST_MARKER=other bash "${guard_dir}/target.sh"; then fail 'marcador canonico divergente aceito'; fi
echo 'PASS: guard_real_target_canonico'

upload_body="$(awk '
  /^      - name: Upload immutable release$/ {selected=1}
  selected && /<<\047REMOTE\047/ {reading=1; next}
  reading && /^          REMOTE$/ {exit}
  reading {print substr($0,11)}
' "$workflow")"
[[ "$upload_body" == *'sha256sum -- "${incoming}"'* ]] || fail 'guard remoto de checksum ausente'
fixture_root="${guard_dir}/production"
mkdir -p "${fixture_root}/incoming" "${fixture_root}/releases" "${guard_dir}/payload"
printf 'synthetic payload\n' > "${guard_dir}/payload/value"
tar -czf "${fixture_root}/incoming/${DEPLOY_SHA}.tar.gz" -C "${guard_dir}/payload" .
printf '%s\n' "${upload_body//\/opt\/topsv3\/production/$fixture_root}" > "${guard_dir}/upload.sh"
archive_hash="$(sha256sum "${fixture_root}/incoming/${DEPLOY_SHA}.tar.gz" | cut -d ' ' -f 1)"
if bash "${guard_dir}/upload.sh" "$DEPLOY_SHA" 2222222222222222222222222222222222222222222222222222222222222222; then fail 'checksum incorreto foi extraido'; fi
[[ ! -e "${fixture_root}/releases/${DEPLOY_SHA}" && ! -e "${fixture_root}/releases/.${DEPLOY_SHA}.incoming" ]] || fail 'checksum falho realizou extracao'
if bash "${guard_dir}/upload.sh" invalid_sha "$archive_hash"; then fail 'SHA invalido criou release'; fi
bash "${guard_dir}/upload.sh" "$DEPLOY_SHA" "$archive_hash"
[[ "$(cat "${fixture_root}/releases/${DEPLOY_SHA}/.release-sha")" == "$DEPLOY_SHA" ]] || fail 'identidade final divergente'
[[ "$(cat "${fixture_root}/releases/${DEPLOY_SHA}/value")" == 'synthetic payload' ]] || fail 'payload integral nao foi extraido'
chmod -R u+w "${fixture_root}/releases/${DEPLOY_SHA}"
echo 'PASS: checksum_real_antes_extracao_e_sha_final'
echo "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
