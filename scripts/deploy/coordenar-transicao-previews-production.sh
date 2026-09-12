#!/usr/bin/env bash
# Sourced by the existing supervised deployment, never a deploy/APPLY entrypoint.
# Regularization is separately confirmed. Publication only observes fresh data.

op_preview_cleanup_validate() {
  local report="$1" proof decision identifier
  # Failed VALIDATE is still a readonly job. A success receipt is not required
  # for cleanup, but exact ownership, image, mode and terminal state are.
  op_run readonly python3 "${OP_PREVIEW_HELPER}" cleanup-proof \
    "${report}" "${JOB_CONTAINER}" "${OP_ID}" || return $?
  proof="$(python3 -c 'import json,sys; p=json.load(open(sys.argv[1])); print(p["decision"]+"|"+p.get("id",""))' \
    "${report}/cleanup-proof.json")" || return 1
  decision="${proof%%|*}" identifier="${proof#*|}"
  case "${decision}" in
    ABSENT) [ -z "${identifier}" ] || return 1 ;;
    REMOVE)
      [[ "${identifier}" =~ ^[a-f0-9]{64}$ ]] || return 1
      op_run mutating docker rm "${identifier}" </dev/null || return $?
      ;;
    *) return 1 ;;
  esac
  printf 'PREVIEW_VALIDATE_CLEANUP=PASS decision=%s\n' "${decision}"
}

op_preview_validate_before_activation() {
  [ "${OP_ACTIVE:-0}" -eq 1 ] && [ "${OP_SNAPSHOT_READY:-0}" -eq 1 ] \
    && [ "${OP_PHASE:-}" = BUILDING ] || return 2
  [ "${OP_AMBIGUOUS:-0}" -eq 0 ] && [ "${OP_CANCEL_RC:-0}" -eq 0 ] \
    && [ -z "${OP_CHILD_PID:-}" ] || return 1
  [ ! -e "${OP_ROOT}/operations/preview-backfill.pending" ] \
    && [ ! -L "${OP_ROOT}/operations/preview-backfill.pending" ] || return 76
  local release="${OP_ROOT}/releases/${OP_CANDIDATE}" report rc=0 cleanup_rc=0
  [ -f "${OP_DIR}/candidate.images.tsv" ] || return 2
  OP_PREVIEW_HELPER="${release}/scripts/deploy/validar-transicao-previews-runtime.py"
  OP_PREVIEW_DIR="${OP_DIR}/previews"
  [ ! -e "${OP_PREVIEW_DIR}" ] && [ ! -L "${OP_PREVIEW_DIR}" ] || return 2
  mkdir -m 0700 -- "${OP_PREVIEW_DIR}" || return 1
  report="${OP_PREVIEW_DIR}/validate"
  mkdir -m 0700 -- "${report}" || return 1
  # Initialize the exact expected name even if the helper fails before spawn.
  JOB_CONTAINER="topsv3-preview-${OP_CANDIDATE:0:12}-final-validate"
  source "${release}/scripts/deploy/executar-backfill-previews-production.sh"
  op_phase PREVIEW_VALIDATE || return $?
  # The existing helper pins candidate.images.tsv and supervises Docker through
  # op_run. No PLAN/APPLY retry, producer drainage or service stop occurs here.
  preview_backfill_main VALIDATE final "${OP_CANDIDATE}" "${release}" \
    "${OP_SECRETS}/production.env" topsv3-production topsv3-production \
    topsv3-production-net "${report}" || rc=$?
  if [ "${rc}" -eq 0 ]; then
    op_phase PREVIEW_PUBLIC_CONTRACT || rc=$?
    if [ "${rc}" -eq 0 ]; then
      op_run readonly python3 "${OP_PREVIEW_HELPER}" public "${OP_PREVIEW_DIR}" \
        "${release}/scripts/deploy/validar-previews-publicos-production.sql" \
        "${JOB_CONTAINER}" || rc=$?
    fi
  fi
  # Always try owned-terminal cleanup, including a rejected metadata/public gate.
  # Never clear supervisor ambiguity or replace the original validation error.
  op_preview_cleanup_validate "${report}" || cleanup_rc=$?
  if [ "${cleanup_rc}" -ne 0 ]; then
    OP_AMBIGUOUS=1
    _op_state || printf 'PREVIEW_VALIDATE_CLEANUP=UNKNOWN journal_unavailable=true\n' >&2
    printf 'PREVIEW_VALIDATE_CLEANUP=DEFERRED rc=%s\n' "${cleanup_rc}" >&2
    [ "${rc}" -ne 0 ] || rc="${cleanup_rc}"
  fi
  [ "${rc}" -eq 0 ] || return "${rc}"
  [ "${OP_AMBIGUOUS:-0}" -eq 0 ] && [ "${OP_CANCEL_RC:-0}" -eq 0 ] \
    && [ -z "${OP_CHILD_PID:-}" ] || return 1
  printf 'PREVIEW_TRANSITION_READY=PASS readonly=true\n'
}
