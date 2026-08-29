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
  [[ "${value}" =~ ^-?[0-9]+$ ]] || fail "resultado numerico ausente: ${key}"
  printf '%s\n' "${value}"
}

cleanup_job() {
  if [ -n "${JOB_CONTAINER:-}" ] && docker inspect "${JOB_CONTAINER}" >/dev/null 2>&1; then
    if [ "$(docker inspect "${JOB_CONTAINER}" --format '{{.State.Running}}')" = true ]; then
      docker stop --time 180 "${JOB_CONTAINER}" >/dev/null || {
        log "CLEANUP=DEFERRED reason=graceful_shutdown_failed"
        return 1
      }
    fi
    docker rm "${JOB_CONTAINER}" >/dev/null || return 1
  fi
}

on_exit() {
  local rc=$?
  rm -f -- "${RAW_OUTPUT:-}"
  if ! cleanup_job; then
    rc=1
  fi
  exit "${rc}"
}

run_backfill() {
  local apply_confirmed=false
  [ "${MODE}" = APPLY ] && apply_confirmed=true
  set +e
  env \
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
      -p "${COMPOSE_PROJECT}" \
      run --rm -T --no-deps \
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
    return 1
  fi
}

validate_result() {
  local eligible r2_available missing inconsistent unproven
  local db_updated db_unchanged batches
  local db_available db_unknown db_pending db_inconsistent
  eligible="$(result_value eligible)"
  r2_available="$(result_value r2_available)"
  missing="$(result_value missing)"
  inconsistent="$(result_value inconsistent)"
  unproven="$(result_value unproven)"
  [ "${missing}" -eq 0 ] \
    && [ "${inconsistent}" -eq 0 ] \
    && [ "${unproven}" -eq 0 ] \
    && [ "${r2_available}" -eq "${eligible}" ] \
    || fail "inventario read-only de previews reprovado"

  if [ "${MODE}" != PLAN ]; then
    db_updated="$(result_value db_updated)"
    db_unchanged="$(result_value db_unchanged)"
    batches="$(result_value batches)"
    db_available="$(result_value db_available)"
    db_unknown="$(result_value db_unknown)"
    db_pending="$(result_value db_pending)"
    db_inconsistent="$(result_value db_inconsistent)"
    [ "${db_available}" -eq "${eligible}" ] \
      && [ "${db_unknown}" -eq 0 ] \
      && [ "${db_pending}" -eq 0 ] \
      && [ "${db_inconsistent}" -eq 0 ] \
      || fail "persistencia de previews reprovada"
  fi

  log "RESULT phase=${PHASE} mode=${MODE} status=OK eligible=${eligible} r2_available=${r2_available} missing=${missing} inconsistent=${inconsistent} unproven=${unproven}"
  if [ "${MODE}" != PLAN ]; then
    log "DATABASE phase=${PHASE} mode=${MODE} updated=${db_updated} unchanged=${db_unchanged} batches=${batches} available=${db_available} unknown=${db_unknown} pending=${db_pending} inconsistent=${db_inconsistent}"
  fi
}

main() {
  [ "$#" -eq 9 ] || fail "uso: $0 MODE PHASE RELEASE_SHA RELEASE_DIR ENV_FILE PROJECT PREFIX NETWORK REPORT_DIR"
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

  [[ "${MODE}" =~ ^(PLAN|APPLY|VALIDATE)$ ]] || fail "modo invalido"
  [[ "${PHASE}" =~ ^(initial|delta|final)$ ]] || fail "fase invalida"
  [[ "${RELEASE_SHA}" =~ ^[0-9a-f]{40}$ ]] || fail "SHA invalido"
  [[ "${BATCH_SIZE}" =~ ^[0-9]+$ ]] \
    && [ "${BATCH_SIZE}" -ge 1 ] \
    && [ "${BATCH_SIZE}" -le 1000 ] \
    || fail "lote invalido"
  validate_identifier "${COMPOSE_PROJECT}"
  validate_identifier "${APP_PREFIX}"
  validate_identifier "${APP_NETWORK}"
  [ -d "${RELEASE_DIR}" ] || fail "release ausente"
  [ -r "${ENV_FILE}" ] || fail "ambiente indisponivel"
  COMPOSE_FILE="${RELEASE_DIR}/deploy/production/docker-compose.yml"
  [ -r "${COMPOSE_FILE}" ] || fail "Compose ausente"
  REPORT_DIR="$(readlink -m "${REPORT_DIR}")"
  RELEASE_DIR="$(readlink -f "${RELEASE_DIR}")"
  case "${REPORT_DIR}/" in
    "${RELEASE_DIR}/"*) fail "relatorio deve permanecer fora da release" ;;
  esac
  install -d -m 0750 "${REPORT_DIR}"

  local mode_lower
  mode_lower="${MODE,,}"
  JOB_CONTAINER="topsv3-preview-${RELEASE_SHA:0:12}-${PHASE}-${mode_lower}"
  docker inspect "${JOB_CONTAINER}" >/dev/null 2>&1 \
    && fail "container de backfill preexistente"
  CONTAINER_REPORT_PATH="/run/topsv3-preview-backfill/${PHASE}-${mode_lower}.tsv"
  RAW_OUTPUT="$(mktemp)"
  RESULT_LINE=""
  trap on_exit EXIT

  run_backfill
  validate_result
  log "R2_MUTATIONS=0 DB_SCOPE=preview_columns_only REPORT=external_sanitized"
}

main "$@"
