#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  return 1
}

validate_identity() {
  local run_id="$1"
  local run_attempt="$2"
  local release_sha="$3"

  [[ "${run_id}" =~ ^[0-9]+$ ]] || fail "run id invalido"
  [[ "${run_attempt}" =~ ^[0-9]+$ ]] || fail "tentativa invalida"
  [[ "${release_sha}" =~ ^[0-9a-f]{40}$ ]] || fail "SHA de release invalido"
}

validate_environment() {
  : "${TOPSDOJOB_PROD_SSH_HOST:?host SSH ausente}"
  : "${TOPSDOJOB_PROD_SSH_USER:?usuario SSH ausente}"
  : "${TOPSDOJOB_PROD_SSH_PORT:?porta SSH ausente}"
  : "${TOPSDOJOB_PROD_TARGET_SHA256:?SHA do marcador ausente}"
  test "${TOPSDOJOB_PROD_SSH_USER}" = "topsdojob"
  [[ "${TOPSDOJOB_PROD_SSH_PORT}" =~ ^[0-9]+$ ]]
  [ "${TOPSDOJOB_PROD_SSH_PORT}" -ge 1 ]
  [ "${TOPSDOJOB_PROD_SSH_PORT}" -le 65535 ]
  [[ "${TOPSDOJOB_PROD_TARGET_SHA256}" =~ ^[0-9a-f]{64}$ ]]
}

remote_guard() {
  printf '%s' "marker=/etc/topsdojob/target.env; test -r \"\${marker}\"; actual_marker=\$(sha256sum -- \"\${marker}\" | cut -d ' ' -f 1); test \"\${actual_marker}\" = '${TOPSDOJOB_PROD_TARGET_SHA256}'; grep -qx 'TOPSDOJOB_TARGET=production' \"\${marker}\"; grep -qx 'TOPSDOJOB_PROJECT=topsdojob-v3' \"\${marker}\""
}

remote_staging_validation() {
  local staging_rel="$1"
  printf '%s' "uid=\$(id -u); cache=\"\${HOME}/.cache\"; root=\"\${cache}/topsdojob-deploy\"; staging=\"\${HOME}/${staging_rel}\"; test -d \"\${HOME}\"; test ! -L \"\${HOME}\"; test \"\$(stat -c '%u' \"\${HOME}\")\" = \"\${uid}\"; test -d \"\${cache}\"; test ! -L \"\${cache}\"; test \"\$(stat -c '%u' \"\${cache}\")\" = \"\${uid}\"; test -z \"\$(find \"\${cache}\" -maxdepth 0 -perm /022 -print -quit)\"; test -d \"\${root}\"; test ! -L \"\${root}\"; test \"\$(stat -c '%u' \"\${root}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${root}\")\" = 700; test -d \"\${staging}\"; test ! -L \"\${staging}\"; test \"\$(stat -c '%u' \"\${staging}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${staging}\")\" = 700"
}

main() {
  [ "$#" -ge 4 ] || fail "uso: $0 prepare|upload|cleanup RUN_ID TENTATIVA SHA [ARQUIVO] [NOME]"
  local action="$1"
  local run_id="$2"
  local run_attempt="$3"
  local release_sha="$4"
  shift 4

  validate_identity "${run_id}" "${run_attempt}" "${release_sha}"
  validate_environment

  local staging_rel=".cache/topsdojob-deploy/${run_id}-${run_attempt}-${release_sha}"
  local ssh_target="${TOPSDOJOB_PROD_SSH_USER}@${TOPSDOJOB_PROD_SSH_HOST}"
  local ssh_opts=(
    -i "${HOME}/.ssh/topsdojob_production"
    -p "${TOPSDOJOB_PROD_SSH_PORT}"
    -o BatchMode=yes
    -o StrictHostKeyChecking=yes
    -o UserKnownHostsFile="${HOME}/.ssh/known_hosts"
    -o HostKeyAlias="[${TOPSDOJOB_PROD_SSH_HOST}]:${TOPSDOJOB_PROD_SSH_PORT}"
  )
  local scp_opts=(
    -i "${HOME}/.ssh/topsdojob_production"
    -P "${TOPSDOJOB_PROD_SSH_PORT}"
    -o BatchMode=yes
    -o StrictHostKeyChecking=yes
    -o UserKnownHostsFile="${HOME}/.ssh/known_hosts"
    -o HostKeyAlias="[${TOPSDOJOB_PROD_SSH_HOST}]:${TOPSDOJOB_PROD_SSH_PORT}"
  )
  local guard staging_validation remote_command
  guard="$(remote_guard)"
  staging_validation="$(remote_staging_validation "${staging_rel}")"

  case "${action}" in
    prepare)
      [ "$#" -eq 1 ] || fail "prepare exige o controlador local"
      local source="$1"
      local remote_name="deploy-remoto.sh"
      local remote_rel="${staging_rel}/${remote_name}"
      local expected_sha256
      test -f "${source}"
      test ! -L "${source}"
      expected_sha256="$(sha256sum -- "${source}" | cut -d ' ' -f 1)"
      [[ "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]]

      remote_command="set -euo pipefail; umask 077; ${guard}; uid=\$(id -u); cache=\"\${HOME}/.cache\"; root=\"\${cache}/topsdojob-deploy\"; staging=\"\${HOME}/${staging_rel}\"; test -d \"\${HOME}\"; test ! -L \"\${HOME}\"; test \"\$(stat -c '%u' \"\${HOME}\")\" = \"\${uid}\"; if [ ! -e \"\${cache}\" ] && [ ! -L \"\${cache}\" ]; then mkdir -m 0700 -- \"\${cache}\"; fi; test -d \"\${cache}\"; test ! -L \"\${cache}\"; test \"\$(stat -c '%u' \"\${cache}\")\" = \"\${uid}\"; test -z \"\$(find \"\${cache}\" -maxdepth 0 -perm /022 -print -quit)\"; if [ ! -e \"\${root}\" ] && [ ! -L \"\${root}\" ]; then mkdir -m 0700 -- \"\${root}\"; fi; test -d \"\${root}\"; test ! -L \"\${root}\"; test \"\$(stat -c '%u' \"\${root}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${root}\")\" = 700; test ! -e \"\${staging}\"; test ! -L \"\${staging}\"; mkdir -m 0700 -- \"\${staging}\"; test \"\$(stat -c '%u' \"\${staging}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${staging}\")\" = 700; printf '%s\\n' 'REMOTE_DEPLOY_STAGING=READY'"
      ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
      scp "${scp_opts[@]}" "${source}" "${ssh_target}:${remote_rel}" </dev/null
      remote_command="set -euo pipefail; ${guard}; ${staging_validation}; file=\"\${HOME}/${remote_rel}\"; test -f \"\${file}\"; test ! -L \"\${file}\"; test \"\$(stat -c '%u' \"\${file}\")\" = \"\${uid}\"; test -z \"\$(find \"\${file}\" -maxdepth 0 -perm /022 -print -quit)\"; actual=\$(sha256sum -- \"\${file}\" | cut -d ' ' -f 1); test \"\${actual}\" = '${expected_sha256}'; chmod 0500 \"\${file}\"; test \"\$(stat -c '%a' \"\${file}\")\" = 500; printf '%s\\n' 'REMOTE_DEPLOY_CONTROLLER=VERIFIED'"
      ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
      printf 'REMOTE_DEPLOY_SCRIPT_SHA256=%s\n' "${expected_sha256}"
      ;;
    upload)
      [ "$#" -eq 2 ] || fail "upload exige arquivo local e nome remoto"
      local source="$1"
      local remote_name="$2"
      case "${remote_name}" in
        indexnow.key|release.tar.gz) ;;
        *) fail "nome remoto fora da lista permitida" ;;
      esac
      local remote_rel="${staging_rel}/${remote_name}"
      local expected_sha256
      test -f "${source}"
      test ! -L "${source}"
      expected_sha256="$(sha256sum -- "${source}" | cut -d ' ' -f 1)"
      [[ "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]]

      remote_command="set -euo pipefail; ${guard}; ${staging_validation}; file=\"\${HOME}/${remote_rel}\"; test ! -e \"\${file}\"; test ! -L \"\${file}\""
      ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
      scp "${scp_opts[@]}" "${source}" "${ssh_target}:${remote_rel}" </dev/null
      remote_command="set -euo pipefail; ${guard}; ${staging_validation}; file=\"\${HOME}/${remote_rel}\"; test -f \"\${file}\"; test ! -L \"\${file}\"; test \"\$(stat -c '%u' \"\${file}\")\" = \"\${uid}\"; test -z \"\$(find \"\${file}\" -maxdepth 0 -perm /022 -print -quit)\"; actual=\$(sha256sum -- \"\${file}\" | cut -d ' ' -f 1); test \"\${actual}\" = '${expected_sha256}'; chmod 0600 \"\${file}\"; test \"\$(stat -c '%a' \"\${file}\")\" = 600; printf '%s\\n' 'REMOTE_STAGING_FILE=VERIFIED'"
      ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
      printf 'REMOTE_STAGING_FILE_SHA256=%s\n' "${expected_sha256}"
      ;;
    cleanup)
      [ "$#" -eq 0 ] || fail "cleanup nao recebe argumentos"
      remote_command="set -euo pipefail; ${guard}; uid=\$(id -u); cache=\"\${HOME}/.cache\"; root=\"\${cache}/topsdojob-deploy\"; staging=\"\${HOME}/${staging_rel}\"; if [ ! -e \"\${staging}\" ] && [ ! -L \"\${staging}\" ]; then printf '%s\\n' 'REMOTE_DEPLOY_STAGING_CLEANUP=ABSENT'; exit 0; fi; ${staging_validation}; for name in deploy-remoto.sh indexnow.key release.tar.gz; do file=\"\${staging}/\${name}\"; if [ -e \"\${file}\" ] || [ -L \"\${file}\" ]; then test -f \"\${file}\"; test ! -L \"\${file}\"; test \"\$(stat -c '%u' \"\${file}\")\" = \"\${uid}\"; rm -f -- \"\${file}\"; fi; done; rmdir -- \"\${staging}\"; rmdir -- \"\${root}\" 2>/dev/null || true; printf '%s\\n' 'REMOTE_DEPLOY_STAGING_CLEANUP=OK'"
      ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
      ;;
    *)
      fail "acao de staging invalida"
      ;;
  esac
}

main "$@"
