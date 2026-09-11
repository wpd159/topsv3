#!/usr/bin/env bash
set -euo pipefail

log() {
  printf 'PREVIEW_BACKFILL %s\n' "$*"
}

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  return 1
}

sanitize_diagnostic() {
  LC_ALL=C sed -E \
    -e 's#([[:alpha:]][[:alnum:]+.-]*://)[^[:space:]"<>]+#<redacted-url>#g' \
    -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/<redacted-email>/g' \
    -e 's/[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}/<redacted-document>/g' \
    -e 's/(\+?55[[:space:]]*)?\(?[0-9]{2}\)?[[:space:]-]*[0-9]{4,5}-?[0-9]{4}/<redacted-phone>/g' \
    -e 's/([0-9]{1,3}\.){3}[0-9]{1,3}/<redacted-ip>/g' \
    -e 's/[0-9A-Fa-f]{0,4}(:[0-9A-Fa-f]{0,4}){2,}/<redacted-ip>/g' \
    -e 's/((authorization|cookie|set-cookie|token|secret|password|passwd|api[_-]?key|access[_-]?key|session|database|username|user|host|port|url)[[:space:]]*[:=][[:space:]]*)[^[:space:],;}"<]+/\1<redacted>/Ig' \
    -e 's/([A-Z][A-Z0-9_]{2,}[[:space:]]*=[[:space:]]*)[^[:space:],;}"<]+/\1<redacted>/g' \
    -e 's/[A-Za-z0-9+_\/=.-]{40,}/<redacted-value>/g'
}

validate_identifier() {
  [[ "$1" =~ ^[a-z0-9][a-z0-9_.-]{0,62}$ ]] \
    || fail "identificador tecnico invalido"
}

result_value() {
  local key="$1"
  local value
  value="$(tr ' ' '\n' <<< "${RESULT_LINE}" | sed -n "s/^${key}=//p" | tail -1)"
  [[ "${value}" =~ ^[0-9]+$ ]] || { fail "resultado numerico ausente: ${key}"; return 1; }
  printf '%s\n' "${value}"
}

cleanup_job() {
  local container_id actual_owner
  if [ -n "${JOB_CONTAINER:-}" ]; then
    # An inspect error is not proof that the owned job disappeared.
    docker inspect "${JOB_CONTAINER}" >/dev/null 2>&1 || return 1
    container_id="$(docker inspect "${JOB_CONTAINER}" --format '{{.Id}}')" || return 1
    [[ "${container_id}" =~ ^[a-f0-9]{64}$ ]] || return 1
    actual_owner="$(docker inspect "${container_id}" --format '{{index .Config.Labels "topsv3.preview.operation"}}')" || return 1
    if [ -z "${JOB_OWNER:-}" ] || [ "${actual_owner}" != "${JOB_OWNER}" ]; then
      log "CLEANUP=DEFERRED reason=ownership_unproven"
      return 1
    fi
    [ "${JOB_VERIFIED:-0}" -eq 1 ] \
      && [ "$(docker inspect "${container_id}" --format '{{.State.Running}}')" = false ] \
      || { log "CLEANUP=DEFERRED reason=terminal_success_unproven"; return 1; }
    [ "$(docker inspect "${container_id}" --format '{{index .Config.Labels "topsv3.preview.operation"}}')" = "${JOB_OWNER}" ] || return 1
    docker rm "${container_id}" >/dev/null || return 1
  else
    return 1
  fi
}

on_exit() {
  local rc=$?
  rm -f -- "${RAW_OUTPUT:-}"
  if [ "${JOB_VERIFIED:-0}" -eq 1 ] && [ "${rc}" -eq 0 ]; then
    if cleanup_job; then
      if [ -n "${PENDING_FILE:-}" ]; then
        [ "$(head -1 -- "${PENDING_FILE}")" = "${JOB_OWNER}" ] \
          && rm -- "${PENDING_FILE}" || rc=1
      fi
    else
      rc=1
    fi
  elif [ -n "${PENDING_FILE:-}" ]; then
    log "CLEANUP=DEFERRED reason=operation_requires_investigation retry_allowed=false"
  fi
  exit "${rc}"
}

standalone_lock() {
  # Reuse the exact mutex of activation/recovery, not a caller-selected lock.
  local root state result existing
  root="$(dirname -- "$(dirname -- "${RELEASE_DIR}")")"
  [ "${root}" = /opt/topsv3/production ] \
    && [ "${RELEASE_DIR}" = "${root}/releases/${RELEASE_SHA}" ] \
    && [ "$(cat "${RELEASE_DIR}/.release-sha")" = "${RELEASE_SHA}" ] \
    || { fail "identidade da release independente divergente"; return 1; }
  source "${RELEASE_DIR}/scripts/deploy/proteger-operacao-production.sh"
  _op_lock "${root}" || return $?
  state="${OP_ROOT}/operations/active.state"
  if [ -e "${state}" ]; then
    _op_validate_state "${state}" || { fail "journal de deploy invalido"; return 1; }
    result="$(_op_read result "${state}")" || return 1
    case "${result}" in COMPLETED|ROLLED_BACK|ABORTED|RECONCILED) ;;
      *) fail "operacao de deploy pendente"; return 1 ;;
    esac
  fi
  [ "$(readlink -f -- "${ENV_FILE}")" = "${OP_SECRETS}/production.env" ] \
    && [ "${COMPOSE_PROJECT}" = topsv3-production ] \
    && [ "${APP_PREFIX}" = topsv3-production ] \
    && [ "${APP_NETWORK}" = topsv3-production-net ] \
    || { fail "destino independente nao canonico"; return 1; }
  existing="$(docker ps --all --quiet --filter label=topsv3.preview.operation)" || return 1
  [ -z "${existing}" ] || { fail "job de previews preexistente; apuracao obrigatoria"; return 1; }
}

run_backfill() {
  local apply_confirmed=false
  if [ "${MODE}" = APPLY ]; then
    [ "${TOPSV3_PREVIEW_BACKFILL_CONFIRM:-}" = "APPLY:${RELEASE_SHA}" ] || return 1
    apply_confirmed=true
  fi
  local -a runner=() ownership=(--label "topsv3.preview.operation=${JOB_OWNER}")
  local -a pin_arguments=() pinned_compose=(-f "${REPORT_DIR}/pinned-backfill.compose.yml")
  if [ "${OP_ACTIVE:-0}" -eq 1 ]; then
    declare -F op_run >/dev/null || { fail "supervisor da operacao ausente"; return 1; }
    runner=(op_run mutating)
  else
    pin_arguments=("${TOPSV3_PREVIEW_BACKFILL_IMAGE_ID}")
  fi
  python3 "${RELEASE_DIR}/scripts/deploy/validar-transicao-previews-runtime.py" \
    pin "${REPORT_DIR}" "${RELEASE_SHA}" "${COMPOSE_FILE}" "${ENV_FILE}" \
    "${COMPOSE_PROJECT}" "${APP_PREFIX}" "${APP_NETWORK}" "${pin_arguments[@]}" || return $?
  if [ "${OP_ACTIVE:-0}" -ne 1 ]; then
    PENDING_FILE="${OP_ROOT}/operations/preview-backfill.pending"
    # Persist intent before Docker can detach. Keep it on ALL failed outcomes;
    # neither a new report directory nor a lost CLI authorizes a second APPLY.
    (umask 077; set -o noclobber; printf '%s\n' "${JOB_OWNER}" "${RELEASE_SHA}" "${MODE}" "${REPORT_DIR}" > "${PENDING_FILE}") || return 1
    sync -f "${PENDING_FILE}" || return 1
  fi
  set +e
  "${runner[@]}" env \
    TOPSV3_RELEASE_SHA="${RELEASE_SHA}" \
    TOPSV3_APP_PREFIX="${APP_PREFIX}" \
    TOPSV3_APP_NETWORK="${APP_NETWORK}" \
    EFI_ENABLED=false \
    EFI_RECONCILIATION_ENABLED=false \
    EFI_WEBHOOK_REGISTRATION_ENABLED=false \
    OUTBOX_EMAIL_ENABLED=false \
    docker compose \
      --env-file "${ENV_FILE}" \
      -f "${COMPOSE_FILE}" \
      "${pinned_compose[@]}" \
      -p "${COMPOSE_PROJECT}" \
      run -T --no-deps \
      --pull never \
      "${ownership[@]}" \
      --name "${JOB_CONTAINER}" \
      -e EFI_ENABLED=false \
      -e EFI_RECONCILIATION_ENABLED=false \
      -e EFI_WEBHOOK_REGISTRATION_ENABLED=false \
      -e OUTBOX_EMAIL_ENABLED=false \
      -e SPRING_TASK_SCHEDULING_ENABLED=false \
      --volume "${REPORT_DIR}:/run/topsv3-preview-backfill" \
      backend \
      --app.bootstrap=restricted-media-preview-backfill \
      --spring.main.web-application-type=none \
      --spring.main.banner-mode=off \
      --logging.level.root=WARN \
      --app.fixture.admin-provision.enabled=false \
      --app.fixture.stories.enabled=false \
      --app.fixture.owner-credential.enabled=false \
      --app.fixture.auth-smoke.enabled=false \
      --app.restricted-media-preview-reconciliation.enabled=true \
      --app.restricted-media-preview-reconciliation.mode="${MODE}" \
      --app.restricted-media-preview-reconciliation.apply-confirmed="${apply_confirmed}" \
      --app.restricted-media-preview-reconciliation.batch-size="${BATCH_SIZE}" \
      --app.restricted-media-preview-reconciliation.report-path="${CONTAINER_REPORT_PATH}" \
      </dev/null >"${RAW_OUTPUT}" 2>&1
  local rc=$?
  set -e
  RESULT_LINE="$(grep '^RESTRICTED_MEDIA_PREVIEW_RECONCILIATION_RESULT ' "${RAW_OUTPUT}" | tail -1 || true)"
  if [ "${rc}" -ne 0 ] || [ -z "${RESULT_LINE}" ]; then
    log "RESULT phase=${PHASE} mode=${MODE} status=FAIL exit_code=${rc}"
    tail -40 "${RAW_OUTPUT}" | sanitize_diagnostic | while IFS= read -r line; do
      [ -n "${line}" ] && log "DIAGNOSTIC line=$(printf '%s' "${line}" | tr '"' "'" | cut -c1-1024)"
    done
    if [ "${rc}" -ne 0 ]; then return "${rc}"; fi
    return 1
  fi
}

validate_result() {
  local eligible r2_available missing inconsistent unproven
  local db_updated db_unchanged batches
  local db_available db_unknown db_pending db_inconsistent
  eligible="$(result_value eligible)" || return 1
  r2_available="$(result_value r2_available)" || return 1
  missing="$(result_value missing)" || return 1
  inconsistent="$(result_value inconsistent)" || return 1
  unproven="$(result_value unproven)" || return 1
  [ "${missing}" -eq 0 ] \
    && [ "${inconsistent}" -eq 0 ] \
    && [ "${unproven}" -eq 0 ] \
    && [ "${r2_available}" -eq "${eligible}" ] \
    || { fail "inventario read-only de previews reprovado"; return 1; }

  if [ "${MODE}" != PLAN ]; then
    db_updated="$(result_value db_updated)" || return 1
    db_unchanged="$(result_value db_unchanged)" || return 1
    batches="$(result_value batches)" || return 1
    db_available="$(result_value db_available)" || return 1
    db_unknown="$(result_value db_unknown)" || return 1
    db_pending="$(result_value db_pending)" || return 1
    db_inconsistent="$(result_value db_inconsistent)" || return 1
    [ "${db_available}" -eq "${eligible}" ] \
      && [ "${db_unknown}" -eq 0 ] \
      && [ "${db_pending}" -eq 0 ] \
      && [ "${db_inconsistent}" -eq 0 ] \
      || { fail "persistencia de previews reprovada"; return 1; }
  fi

  log "RESULT phase=${PHASE} mode=${MODE} status=OK eligible=${eligible} r2_available=${r2_available} missing=${missing} inconsistent=${inconsistent} unproven=${unproven}"
  if [ "${MODE}" != PLAN ]; then
    log "DATABASE phase=${PHASE} mode=${MODE} updated=${db_updated} unchanged=${db_unchanged} batches=${batches} available=${db_available} unknown=${db_unknown} pending=${db_pending} inconsistent=${db_inconsistent}"
  fi
}

preview_backfill_main() {
  [ "$#" -eq 9 ] || { fail "uso: $0 MODE PHASE RELEASE_SHA RELEASE_DIR ENV_FILE PROJECT PREFIX NETWORK REPORT_DIR"; return 2; }
  MODE="${1^^}"
  PHASE="${2,,}"
  RELEASE_SHA="$3"
  RELEASE_DIR="$4"
  ENV_FILE="$5"
  COMPOSE_PROJECT="$6"
  APP_PREFIX="$7"
  APP_NETWORK="$8"
  REPORT_DIR="$9"
  BATCH_SIZE="${TOPSV3_PREVIEW_BACKFILL_BATCH_SIZE:-200}"

  [[ "${MODE}" =~ ^(PLAN|APPLY|VALIDATE)$ ]] || { fail "modo invalido"; return 2; }
  if [ "${MODE}" = APPLY ]; then
    [ "${OP_ACTIVE:-0}" -ne 1 ] \
      && [ "${TOPSV3_PREVIEW_BACKFILL_CONFIRM:-}" = "APPLY:${RELEASE_SHA}" ] \
      || { fail "APPLY exige entrada independente e confirmacao explicita do SHA"; return 1; }
  fi
  if [ "${OP_ACTIVE:-0}" -ne 1 ]; then
    [[ "${TOPSV3_PREVIEW_BACKFILL_IMAGE_ID:-}" =~ ^sha256:[0-9a-f]{64}$ ]] \
      || { fail "identidade fisica avaliada da imagem obrigatoria"; return 1; }
  fi
  [[ "${PHASE}" =~ ^(initial|delta|final)$ ]] || { fail "fase invalida"; return 2; }
  [[ "${RELEASE_SHA}" =~ ^[0-9a-f]{40}$ ]] || { fail "SHA invalido"; return 2; }
  [[ "${BATCH_SIZE}" =~ ^[0-9]+$ ]] \
    && [ "${BATCH_SIZE}" -ge 1 ] \
    && [ "${BATCH_SIZE}" -le 1000 ] \
    || { fail "lote invalido"; return 2; }
  validate_identifier "${COMPOSE_PROJECT}" || return 2
  validate_identifier "${APP_PREFIX}" || return 2
  validate_identifier "${APP_NETWORK}" || return 2
  [ -d "${RELEASE_DIR}" ] || { fail "release ausente"; return 2; }
  [ -r "${ENV_FILE}" ] || { fail "ambiente indisponivel"; return 2; }
  COMPOSE_FILE="${RELEASE_DIR}/deploy/production/docker-compose.yml"
  [ -r "${COMPOSE_FILE}" ] || { fail "Compose ausente"; return 2; }
  REPORT_DIR="$(readlink -m "${REPORT_DIR}")" || return 1
  RELEASE_DIR="$(readlink -f "${RELEASE_DIR}")" || return 1
  case "${REPORT_DIR}/" in
    "${RELEASE_DIR}/"*) fail "relatorio deve permanecer fora da release"; return 2 ;;
  esac
  install -d -m 0700 "${REPORT_DIR}" || return 1
  JOB_VERIFIED=0 PENDING_FILE=
  if [ "${OP_ACTIVE:-0}" -ne 1 ]; then
    trap on_exit EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    trap 'exit 129' HUP
    standalone_lock || return $?
  fi

  local mode_lower
  mode_lower="${MODE,,}"
  JOB_CONTAINER="topsv3-preview-${RELEASE_SHA:0:12}-${PHASE}-${mode_lower}"
  if [ "${OP_ACTIVE:-0}" -eq 1 ]; then
    JOB_OWNER="${OP_ID}"
  else
    JOB_OWNER="$(python3 -c 'import uuid; print(uuid.uuid4())')" || return 1
  fi
  [[ "${JOB_OWNER}" =~ ^[a-f0-9-]{36}$ ]] || { fail "identidade de ownership invalida"; return 1; }
  if docker inspect "${JOB_CONTAINER}" >/dev/null 2>&1; then
    fail "container de backfill preexistente"; return 1
  fi
  CONTAINER_REPORT_PATH="/run/topsv3-preview-backfill/${PHASE}-${mode_lower}.tsv"
  [ ! -e "${REPORT_DIR}/${PHASE}-${mode_lower}.tsv" ] \
    && [ ! -e "${REPORT_DIR}/${PHASE}-${mode_lower}.tsv.state.jsonl" ] \
    || { fail "evidencia de backfill preexistente; retomada exige apuracao"; return 1; }
  RAW_OUTPUT="$(mktemp)" || return 1
  RESULT_LINE=""
  local rc=0
  run_backfill || rc=$?
  # Observe even a failed/ambiguous Docker response. Never overwrite original rc
  # or interpret an after-commit failure as a transaction rollback.
  python3 "${RELEASE_DIR}/scripts/deploy/validar-transicao-previews-runtime.py" \
    outcome "${REPORT_DIR}" "${JOB_CONTAINER}" "${JOB_OWNER}" "${MODE}" \
    "${PHASE}-${mode_lower}.tsv.state.jsonl" || { [ "${rc}" -ne 0 ] || rc=1; }
  [ "${rc}" -eq 0 ] || return "${rc}"
  validate_result || return $?
  python3 "${RELEASE_DIR}/scripts/deploy/validar-transicao-previews-runtime.py" \
    terminal "${REPORT_DIR}" "${JOB_CONTAINER}" "${JOB_OWNER}" "${MODE}" \
    "${PHASE}-${mode_lower}.tsv.state.jsonl" || return $?
  JOB_VERIFIED=1
  # Supervised readonly jobs stay available for the existing public gate.
  rm -f -- "${RAW_OUTPUT}"
  log "R2_MUTATIONS=0 DB_SCOPE=preview_columns_only REPORT=external_sanitized"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  preview_backfill_main "$@"
fi
