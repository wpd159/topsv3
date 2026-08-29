#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  return 1
}

quote_argument() {
  printf '%q' "$1"
}

validate_arguments() {
  local command="$1"
  shift
  case "${command}" in
    prerequisites)
      [ "$#" -eq 0 ] || fail "prerequisites nao recebe argumentos"
      ;;
    indexnow)
      [ "$#" -eq 1 ] || fail "indexnow exige o SHA-256 do manifesto"
      [[ "$1" =~ ^[0-9a-f]{64}$ ]] || fail "SHA-256 do manifesto invalido"
      ;;
    install)
      [ "$#" -eq 2 ] || fail "install exige o SHA da release e o SHA-256 do pacote"
      [[ "$1" =~ ^[0-9a-f]{40}$ ]] || fail "SHA de release invalido"
      [[ "$2" =~ ^[0-9a-f]{64}$ ]] || fail "SHA-256 do pacote invalido"
      ;;
    deploy|smoke)
      [ "$#" -eq 1 ] || fail "${command} exige o SHA da release"
      [[ "$1" =~ ^[0-9a-f]{40}$ ]] || fail "SHA de release invalido"
      ;;
    *)
      fail "subcomando remoto invalido"
      ;;
  esac
}

main() {
  [ "$#" -ge 3 ] || fail "uso: $0 SCRIPT_REMOTO SHA256 SUBCOMANDO [ARGUMENTOS]"
  local remote_script="$1"
  local expected_sha256="$2"
  local command="$3"
  shift 3

  [[ "${remote_script}" =~ ^\.cache/topsdojob-deploy/([0-9]+)-([0-9]+)-([0-9a-f]{40})/deploy-remoto\.sh$ ]] \
    || fail "caminho do script remoto invalido"
  local staging_release_sha="${BASH_REMATCH[3]}"
  [[ "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]] || fail "SHA-256 do script remoto invalido"
  validate_arguments "${command}" "$@"
  if [[ "${command}" =~ ^(install|deploy|smoke)$ ]]; then
    test "$1" = "${staging_release_sha}" || fail "SHA da release diverge do staging"
  fi

  : "${TOPSDOJOB_PROD_SSH_HOST:?host SSH ausente}"
  : "${TOPSDOJOB_PROD_SSH_USER:?usuario SSH ausente}"
  : "${TOPSDOJOB_PROD_SSH_PORT:?porta SSH ausente}"
  test "${TOPSDOJOB_PROD_SSH_USER}" = "topsdojob"
  [[ "${TOPSDOJOB_PROD_SSH_PORT}" =~ ^[0-9]+$ ]]
  [ "${TOPSDOJOB_PROD_SSH_PORT}" -ge 1 ]
  [ "${TOPSDOJOB_PROD_SSH_PORT}" -le 65535 ]

  local ssh_target="${TOPSDOJOB_PROD_SSH_USER}@${TOPSDOJOB_PROD_SSH_HOST}"
  local ssh_opts=(
    -i "${HOME}/.ssh/topsdojob_production"
    -p "${TOPSDOJOB_PROD_SSH_PORT}"
    -o BatchMode=yes
    -o StrictHostKeyChecking=yes
    -o UserKnownHostsFile="${HOME}/.ssh/known_hosts"
    -o HostKeyAlias="[${TOPSDOJOB_PROD_SSH_HOST}]:${TOPSDOJOB_PROD_SSH_PORT}"
  )
  local script_quoted sha_quoted command_quoted argument argument_quoted
  local remote_command
  script_quoted="$(quote_argument "${remote_script}")"
  sha_quoted="$(quote_argument "${expected_sha256}")"
  command_quoted="$(quote_argument "${command}")"
  remote_command="set -euo pipefail; uid=\$(id -u); script_rel=${script_quoted}; expected=${sha_quoted}; cache=\"\${HOME}/.cache\"; root=\"\${cache}/topsdojob-deploy\"; script=\"\${HOME}/\${script_rel}\"; staging=\"\${script%/*}\"; test -d \"\${HOME}\"; test ! -L \"\${HOME}\"; test \"\$(stat -c '%u' \"\${HOME}\")\" = \"\${uid}\"; test -d \"\${cache}\"; test ! -L \"\${cache}\"; test \"\$(stat -c '%u' \"\${cache}\")\" = \"\${uid}\"; test -z \"\$(find \"\${cache}\" -maxdepth 0 -perm /022 -print -quit)\"; test -d \"\${root}\"; test ! -L \"\${root}\"; test \"\$(stat -c '%u' \"\${root}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${root}\")\" = 700; test -d \"\${staging}\"; test ! -L \"\${staging}\"; test \"\$(stat -c '%u' \"\${staging}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${staging}\")\" = 700; test -f \"\${script}\"; test ! -L \"\${script}\"; test \"\$(stat -c '%u' \"\${script}\")\" = \"\${uid}\"; test \"\$(stat -c '%a' \"\${script}\")\" = 500; actual=\$(sha256sum -- \"\${script}\" | cut -d ' ' -f 1); test \"\${actual}\" = \"\${expected}\"; bash \"\${script}\" ${command_quoted}"
  for argument in "$@"; do
    argument_quoted="$(quote_argument "${argument}")"
    remote_command+=" ${argument_quoted}"
  done
  remote_command+=' </dev/null'

  ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
}

main "$@"
