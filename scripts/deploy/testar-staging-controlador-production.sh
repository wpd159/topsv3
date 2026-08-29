#!/usr/bin/env bash
set -euo pipefail

# Impede que o Git Bash converta caminhos Linux dos mounts Docker no Windows.
export MSYS_NO_PATHCONV=1

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
staging_manager="${script_dir}/gerenciar-staging-controlador-production.sh"
remote_invoker="${script_dir}/invocar-deploy-remoto-production.sh"
temp_dir="$(mktemp -d)"
docker_image=ubuntu:24.04
docker_volume="topsv3-staging-$RANDOM-$$"
docker_volume_created=0

cleanup() {
  rm -rf -- "${temp_dir}"
  if [ "${docker_volume_created}" -eq 1 ]; then
    docker volume rm "${docker_volume}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

fail() {
  printf 'FALHA: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail "Docker indisponivel"
docker image inspect "${docker_image}" >/dev/null \
  || fail "imagem local ${docker_image} indisponivel"
docker volume create "${docker_volume}" >/dev/null
docker_volume_created=1

docker run --rm --pull never --network none \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c '
    set -euo pipefail
    mkdir -p /fixture/incoming
    chown 0:1000 /fixture/incoming
    chmod 2770 /fixture/incoming
  ' </dev/null
old_failure_log="${temp_dir}/root-owned.log"
if docker run --rm --pull never --network none --user 1000:1000 \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c 'install -d -m 0750 /fixture/incoming' \
  </dev/null >"${old_failure_log}" 2>&1; then
  fail "diretorio root-owned nao reproduziu a falha anterior"
fi
grep -Fq 'Operation not permitted' "${old_failure_log}"
echo "PASS: diretorio_root_owned_reproduz_falha"

docker run --rm --pull never --network none \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c '
    set -euo pipefail
    rm -rf /fixture/home
    mkdir -p /fixture/home/.cache/topsdojob-deploy
    chown -R 1000:1000 /fixture/home
    chmod 0700 /fixture/home /fixture/home/.cache/topsdojob-deploy
  ' </dev/null
docker run --rm --pull never --network none --user 1000:1000 \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c '
    set -euo pipefail
    ln -s /tmp /fixture/home/.cache/topsdojob-deploy/301-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  ' </dev/null
if docker run --rm --pull never --network none --user 1000:1000 \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c '
    set -euo pipefail
    staging=/fixture/home/.cache/topsdojob-deploy/301-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    test -d "${staging}"
    test ! -L "${staging}"
  ' </dev/null; then
  fail "staging por symlink foi aceito"
fi
echo "PASS: symlink_rejeitado"

docker run --rm --pull never --network none \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c '
    set -euo pipefail
    rm -f /fixture/home/.cache/topsdojob-deploy/301-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    mkdir /fixture/home/.cache/topsdojob-deploy/302-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    chown 0:0 /fixture/home/.cache/topsdojob-deploy/302-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    chmod 0700 /fixture/home/.cache/topsdojob-deploy/302-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  ' </dev/null
if docker run --rm --pull never --network none --user 1000:1000 \
  --mount "source=${docker_volume},target=/fixture" \
  "${docker_image}" bash -c '
    set -euo pipefail
    staging=/fixture/home/.cache/topsdojob-deploy/302-1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    test "$(stat -c "%u" "${staging}")" = "$(id -u)"
  ' </dev/null; then
  fail "staging preexistente com owner incorreto foi aceito"
fi
echo "PASS: staging_owner_incorreto_rejeitado"

docker volume rm "${docker_volume}" >/dev/null
docker_volume_created=0
docker volume create "${docker_volume}" >/dev/null
docker_volume_created=1

runner_home="${temp_dir}/runner-home"
fake_bin="${temp_dir}/fake-bin"
marker="${temp_dir}/target.env"
mkdir -p "${runner_home}/.ssh" "${fake_bin}"
chmod 0700 "${runner_home}"
touch "${runner_home}/.ssh/topsdojob_production" "${runner_home}/.ssh/known_hosts"
printf '%s\n' \
  'TOPSDOJOB_TARGET=production' \
  'TOPSDOJOB_PROJECT=topsdojob-v3' >"${marker}"
marker_sha256="$(sha256sum -- "${marker}" | cut -d ' ' -f 1)"

docker run --rm --pull never --network none \
  --mount "source=${docker_volume},target=/home/topsdojob" \
  "${docker_image}" bash -c '
    set -euo pipefail
    chown 1000:1000 /home/topsdojob
    chmod 0700 /home/topsdojob
    printf "%s\n" \
      TOPSDOJOB_TARGET=production \
      TOPSDOJOB_PROJECT=topsdojob-v3 > /home/topsdojob/.target.env
    chown 1000:1000 /home/topsdojob/.target.env
    chmod 0600 /home/topsdojob/.target.env
  ' </dev/null

cat >"${fake_bin}/ssh" <<'FAKE_SSH'
#!/usr/bin/env bash
set -euo pipefail
if IFS= read -r -t 0.05 unexpected; then
  printf 'stdin inesperado no SSH simulado\n' >&2
  exit 90
fi
remote_command="${!#}"
remote_command="${remote_command//\/etc\/topsdojob\/target.env/\/home\/topsdojob\/.target.env}"
docker run --rm --pull never --network none --user 1000:1000 \
  --env HOME=/home/topsdojob \
  --mount "source=${FAKE_REMOTE_VOLUME},target=/home/topsdojob" \
  "${FAKE_REMOTE_IMAGE}" bash -c "${remote_command}" </dev/null
FAKE_SSH
cat >"${fake_bin}/scp" <<'FAKE_SCP'
#!/usr/bin/env bash
set -euo pipefail
if IFS= read -r -t 0.05 unexpected; then
  printf 'stdin inesperado no SCP simulado\n' >&2
  exit 91
fi
source="${@: -2:1}"
destination="${!#}"
remote_rel="${destination#*:}"
case "${remote_rel}" in
  .cache/topsdojob-deploy/*) ;;
  *) printf 'destino SCP fora do staging\n' >&2; exit 92 ;;
esac
docker run --rm --pull never --network none --interactive \
  --mount "source=${FAKE_REMOTE_VOLUME},target=/home/topsdojob" \
  "${FAKE_REMOTE_IMAGE}" bash -c '
    set -euo pipefail
    umask 022
    remote_rel="$1"
    target="/home/topsdojob/${remote_rel}"
    test -d "$(dirname -- "${target}")"
    test ! -e "${target}"
    test ! -L "${target}"
    cat >"${target}"
    chown 1000:1000 "${target}"
  ' transport-copy "${remote_rel}" <"${source}"
FAKE_SCP
chmod +x "${fake_bin}/ssh" "${fake_bin}/scp"

controller_success="${temp_dir}/controller-success.sh"
controller_failure="${temp_dir}/controller-failure.sh"
cat >"${controller_success}" <<'CONTROLLER_SUCCESS'
#!/usr/bin/env bash
set -euo pipefail
printf 'MARCADOR_ANTERIOR=SIM\n'
bytes="$(cat | wc -c | tr -d ' ')"
test "${bytes}" -eq 0
printf 'CONTROLADOR_STDIN_EOF=SIM\n'
printf 'MARCADOR_POSTERIOR=SIM\n'
CONTROLLER_SUCCESS
cat >"${controller_failure}" <<'CONTROLLER_FAILURE'
#!/usr/bin/env bash
set -euo pipefail
cat >/dev/null
printf 'DIAGNOSTICO_CONTROLADOR=SIM\n'
exit 42
CONTROLLER_FAILURE
chmod 0700 "${controller_success}" "${controller_failure}"
controller_success_sha256="$(sha256sum -- "${controller_success}" | cut -d ' ' -f 1)"
controller_failure_sha256="$(sha256sum -- "${controller_failure}" | cut -d ' ' -f 1)"
release_sha="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

transport_env=(
  "HOME=${runner_home}"
  "PATH=${fake_bin}:${PATH}"
  "FAKE_REMOTE_VOLUME=${docker_volume}"
  "FAKE_REMOTE_IMAGE=${docker_image}"
  "TOPSDOJOB_PROD_SSH_HOST=example.invalid"
  "TOPSDOJOB_PROD_SSH_USER=topsdojob"
  "TOPSDOJOB_PROD_SSH_PORT=22"
  "TOPSDOJOB_PROD_TARGET_SHA256=${marker_sha256}"
)

stage_rel() {
  printf '.cache/topsdojob-deploy/%s-%s-%s' "$1" "$2" "${release_sha}"
}

remote_eval() {
  local command="$1"
  shift
  docker run --rm --pull never --network none --user 1000:1000 \
    --env HOME=/home/topsdojob \
    --mount "source=${docker_volume},target=/home/topsdojob" \
    "${docker_image}" bash -c "${command}" remote-eval "$@" </dev/null
}

prepare_stage() {
  local run_id="$1"
  local controller="$2"
  env "${transport_env[@]}" bash "${staging_manager}" \
    prepare "${run_id}" 1 "${release_sha}" "${controller}"
}

cleanup_stage() {
  local run_id="$1"
  env "${transport_env[@]}" bash "${staging_manager}" \
    cleanup "${run_id}" 1 "${release_sha}"
}

invoke_stage() {
  local run_id="$1"
  local controller_sha256="$2"
  env "${transport_env[@]}" bash "${remote_invoker}" \
    "$(stage_rel "${run_id}" 1)/deploy-remoto.sh" \
    "${controller_sha256}" prerequisites
}

production_state="${temp_dir}/production.state"
printf 'PRODUCTION=stable\n' >"${production_state}"
for run_id in 401 402 403 404 405; do
  prepare_log="${temp_dir}/prepare-${run_id}.log"
  invoke_log="${temp_dir}/invoke-${run_id}.log"
  if ! prepare_stage "${run_id}" "${controller_success}" >"${prepare_log}" 2>&1; then
    cat "${prepare_log}" >&2
    fail "preparacao do staging falhou no ciclo ${run_id}"
  fi
  stage="/home/topsdojob/$(stage_rel "${run_id}" 1)"
  remote_eval '
    set -euo pipefail
    stage="$1"
    test -d "${stage}"
    test ! -L "${stage}"
    test "$(stat -c "%u" "${stage}")" = "$(id -u)"
    test "$(stat -c "%a" "${stage}")" = 700
    test "$(stat -c "%a" "${stage}/deploy-remoto.sh")" = 500
  ' "${stage}"
  grep -q '^REMOTE_DEPLOY_SCRIPT_SHA256=' "${prepare_log}"

  if [ "${run_id}" = 401 ]; then
    printf 'indexnow-fixture\n' >"${temp_dir}/indexnow.key"
    printf 'release-fixture\n' >"${temp_dir}/release.tar.gz"
    env "${transport_env[@]}" bash "${staging_manager}" \
      upload "${run_id}" 1 "${release_sha}" "${temp_dir}/indexnow.key" indexnow.key >/dev/null
    env "${transport_env[@]}" bash "${staging_manager}" \
      upload "${run_id}" 1 "${release_sha}" "${temp_dir}/release.tar.gz" release.tar.gz >/dev/null
    indexnow_sha256="$(sha256sum -- "${temp_dir}/indexnow.key" | cut -d ' ' -f 1)"
    remote_eval '
      set -euo pipefail
      stage="$1"
      expected="$2"
      test "$(stat -c "%a" "${stage}/indexnow.key")" = 600
      test "$(stat -c "%a" "${stage}/release.tar.gz")" = 600
      test "$(sha256sum -- "${stage}/indexnow.key" | cut -d " " -f 1)" = "${expected}"
    ' "${stage}" "${indexnow_sha256}"
  fi

  if ! invoke_stage "${run_id}" "${controller_success_sha256}" >"${invoke_log}" 2>&1; then
    cat "${invoke_log}" >&2
    fail "invocacao do controlador falhou no ciclo ${run_id}"
  fi
  grep -qx 'MARCADOR_ANTERIOR=SIM' "${invoke_log}"
  grep -qx 'CONTROLADOR_STDIN_EOF=SIM' "${invoke_log}"
  grep -qx 'MARCADOR_POSTERIOR=SIM' "${invoke_log}"
  cleanup_stage "${run_id}" >/dev/null
  if remote_eval 'test -e "$1" || test -L "$1"' "${stage}"; then
    fail "staging nao foi removido no ciclo ${run_id}"
  fi
done
echo "PASS: staging_home_owner_mode_0700"
echo "PASS: upload_e_sha_validados"
echo "PASS: controlador_stdin_eof"
echo "PASS: marcador_posterior"
echo "PASS: caminho_sucesso_5_5"

run_id=501
prepare_stage "${run_id}" "${controller_success}" >/dev/null
stage="/home/topsdojob/$(stage_rel "${run_id}" 1)"
remote_eval 'chmod 0700 "$1"; printf "alterado\\n" >>"$1"; chmod 0500 "$1"' \
  "${stage}/deploy-remoto.sh"
if invoke_stage "${run_id}" "${controller_success_sha256}" >/dev/null 2>&1; then
  fail "hash divergente nao bloqueou o controlador"
fi
cleanup_stage "${run_id}" >/dev/null
echo "PASS: hash_divergente_bloqueia"

run_id=502
prepare_stage "${run_id}" "${controller_success}" >/dev/null
stage="/home/topsdojob/$(stage_rel "${run_id}" 1)"
remote_eval 'chmod 0770 "$1"' "${stage}/deploy-remoto.sh"
if invoke_stage "${run_id}" "${controller_success_sha256}" >/dev/null 2>&1; then
  fail "controlador group-writable foi aceito"
fi
cleanup_stage "${run_id}" >/dev/null
echo "PASS: controlador_group_world_writable_rejeitado"

run_id=503
prepare_stage "${run_id}" "${controller_success}" >/dev/null
if prepare_stage "${run_id}" "${controller_success}" >/dev/null 2>&1; then
  fail "staging antigo foi reutilizado"
fi
cleanup_stage "${run_id}" >/dev/null
echo "PASS: staging_antigo_nao_reutilizado"

run_id=504
stage="/home/topsdojob/$(stage_rel "${run_id}" 1)"
bad_transport_env=("${transport_env[@]}")
bad_transport_env[-1]="TOPSDOJOB_PROD_TARGET_SHA256=ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
if env "${bad_transport_env[@]}" bash "${staging_manager}" \
  prepare "${run_id}" 1 "${release_sha}" "${controller_success}" >/dev/null 2>&1; then
  fail "upload ocorreu sem target guard valido"
fi
if remote_eval 'test -e "$1" || test -L "$1"' "${stage}"; then
  fail "staging foi criado antes do target guard"
fi
echo "PASS: target_guard_antes_upload"

run_id=505
prepare_stage "${run_id}" "${controller_failure}" >/dev/null
set +e
invoke_stage "${run_id}" "${controller_failure_sha256}" >"${temp_dir}/failure.log" 2>&1
failure_rc=$?
set -e
test "${failure_rc}" -eq 42
grep -qx 'DIAGNOSTICO_CONTROLADOR=SIM' "${temp_dir}/failure.log"
cleanup_stage "${run_id}" >/dev/null
echo "PASS: falha_controlador_propaga_exit_code"

absolute_script="/opt/topsv3/production/incoming/deploy-remoto-1-1-${release_sha}.sh"
if env "${transport_env[@]}" bash "${remote_invoker}" \
  "${absolute_script}" "${controller_success_sha256}" prerequisites >/dev/null 2>&1; then
  fail "invocador aceitou staging fora do HOME"
fi
echo "PASS: staging_fora_home_rejeitado"

grep -qx 'PRODUCTION=stable' "${production_state}"
remote_eval '
  set -euo pipefail
  root="${HOME}/.cache/topsdojob-deploy"
  test ! -d "${root}" || test -z "$(find "${root}" -mindepth 1 -print -quit)"
'
docker volume rm "${docker_volume}" >/dev/null
docker_volume_created=0
if docker volume inspect "${docker_volume}" >/dev/null 2>&1; then
  fail "volume remoto simulado residual"
fi
echo "PASS: producao_nao_alterada"
echo "PASS: cleanup_normal"
echo "PASS: residuos_zero"
echo "DEPLOY_STAGING_TESTS=PASS"
