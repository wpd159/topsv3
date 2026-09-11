#!/usr/bin/env bash
# Sourced by the existing supervised deployment, never a second deploy entrypoint.
# Source 451 has no proven graceful drain and MUST fail the preflight below.

op_preview_preflight() {
  [ "${OP_ACTIVE:-0}" -eq 1 ] && [ "${OP_SNAPSHOT_READY:-0}" -eq 1 ] || return 2
  command -v python3 >/dev/null && command -v timeout >/dev/null || return 1
  OP_PREVIEW_HELPER="${OP_ROOT}/releases/${OP_CANDIDATE}/scripts/deploy/validar-transicao-previews-runtime.py"
  OP_PREVIEW_DIR="${OP_DIR}/previews"
  [ ! -e "${OP_PREVIEW_DIR}" ] || return 2
  mkdir -m 0700 -- "${OP_PREVIEW_DIR}" || return 1
  mkdir -m 0700 -- "${OP_PREVIEW_DIR}/preflight" || return 1
  op_phase PREVIEW_PREFLIGHT || return $?
  op_run readonly python3 "${OP_PREVIEW_HELPER}" preflight \
    "${OP_PREVIEW_DIR}/preflight" "${OP_PREVIOUS_SHA}"
}

op_preview_drain_and_reconcile() {
  [ "${OP_ACTIVE:-0}" -eq 1 ] && [ -f "${OP_PREVIEW_DIR}/preflight/preview-source.json" ] || return 2
  local release="${OP_ROOT}/releases/${OP_CANDIDATE}" since source_id step mode phase report
  local -a jobs=()
  mkdir -m 0700 -- "${OP_PREVIEW_DIR}/final" || return 1
  # Repeat the effective-runtime and external-producer checks after long builds.
  op_run readonly python3 "${OP_PREVIEW_HELPER}" preflight \
    "${OP_PREVIEW_DIR}/final" "${OP_PREVIOUS_SHA}" || return $?
  source_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' \
    "${OP_PREVIEW_DIR}/final/preview-source.json")" || return 1
  [[ "${source_id}" =~ ^[a-f0-9]{64}$ ]] || return 1
  since="$(date -u +%Y-%m-%dT%H:%M:%S.%NZ)" || return 1
  op_phase PREVIEW_DRAINING || return $?
  # No SIGKILL fallback. A timeout only terminates the Docker client; op_run
  # records ambiguous daemon state and forbids concurrent automatic recovery.
  op_run mutating timeout --signal=TERM 150s docker stop --time=-1 "${source_id}" </dev/null || return $?
  op_run readonly python3 "${OP_PREVIEW_HELPER}" stopped \
    "${OP_PREVIEW_DIR}/final" "${since}" || return $?

  source "${release}/scripts/deploy/executar-backfill-previews-production.sh"
  for step in PLAN:delta APPLY:delta VALIDATE:final; do
    mode="${step%:*}" phase="${step#*:}"
    report="${OP_PREVIEW_DIR}/${mode,,}"
    mkdir -m 0700 -- "${report}" || return 1
    op_phase "PREVIEW_${mode}" || return $?
    # Same shell owner: helper delegates the actual Docker command to op_run.
    # APPLY is never replayed here, including after a post-commit validation error.
    preview_backfill_main "${mode}" "${phase}" "${OP_CANDIDATE}" "${release}" \
      "${OP_SECRETS}/production.env" topsv3-production topsv3-production \
      topsv3-production-net "${report}" || return $?
    jobs+=("${report}/container-terminal.json")
  done
  op_phase PREVIEW_PUBLIC_CONTRACT || return $?
  op_run readonly python3 "${OP_PREVIEW_HELPER}" public "${OP_PREVIEW_DIR}" \
    "${release}/scripts/deploy/validar-previews-publicos-production.sql" \
    "${JOB_CONTAINER}" || return $?
  # Recheck absence of original/extra producers immediately before activation.
  mkdir -m 0700 -- "${OP_PREVIEW_DIR}/accepted" || return 1
  cp -- "${OP_PREVIEW_DIR}/final/preview-source.json" "${OP_PREVIEW_DIR}/accepted/preview-source.json" || return 1
  op_run readonly python3 "${OP_PREVIEW_HELPER}" stopped \
    "${OP_PREVIEW_DIR}/accepted" "${since}" || return $?
  for step in "${jobs[@]}"; do
    # Terminal success plus exact operation label were proved before this cleanup.
    source_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "${step}")" || return 1
    [[ "${source_id}" =~ ^[a-f0-9]{64}$ ]] || return 1
    [ "$(docker inspect "${source_id}" --format '{{index .Config.Labels "topsv3.preview.operation"}}')" = "${OP_ID}" ] || return 1
    [ "$(docker inspect "${source_id}" --format '{{.State.Running}}')" = false ] || return 1
    op_run mutating docker rm "${source_id}" </dev/null || return $?
  done
  printf 'PREVIEW_TRANSITION_READY=PASS\n'
}
