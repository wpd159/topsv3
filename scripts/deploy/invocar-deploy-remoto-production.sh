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
      [ "$#" -eq 2 ] || fail "indexnow exige manifesto e SHA-256"
      [[ "$1" =~ ^/opt/topsv3/production/incoming/indexnow-[0-9]+-[0-9]+-[0-9a-f]{40}\.key$ ]] \
        || fail "manifesto IndexNow invalido"
      [[ "$2" =~ ^[0-9a-f]{64}$ ]] || fail "SHA-256 do manifesto invalido"
      ;;
    install|deploy|smoke)
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

  [[ "${remote_script}" =~ ^/opt/topsv3/production/incoming/deploy-remoto-[0-9]+-[0-9]+-[0-9a-f]{40}\.sh$ ]] \
    || fail "caminho do script remoto invalido"
  [[ "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]] || fail "SHA-256 do script remoto invalido"
  validate_arguments "${command}" "$@"

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
  remote_command="set -euo pipefail; script=${script_quoted}; expected=${sha_quoted}; test -f \"\${script}\"; actual=\$(sha256sum -- \"\${script}\" | cut -d ' ' -f 1); test \"\${actual}\" = \"\${expected}\"; bash \"\${script}\" ${command_quoted}"
  for argument in "$@"; do
    argument_quoted="$(quote_argument "${argument}")"
    remote_command+=" ${argument_quoted}"
  done
  remote_command+=' </dev/null'

  ssh "${ssh_opts[@]}" "${ssh_target}" "${remote_command}" </dev/null
}

main "$@"
