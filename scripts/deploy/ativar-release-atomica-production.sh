#!/usr/bin/env bash
set -uo pipefail

log() {
  printf 'ATOMIC_DEPLOY %s\n' "$*"
}

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  return 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "comando obrigatorio ausente: $1"
}

state_value() {
  local key="$1"
  local fallback="$2"
  local value=""
  if [ -r "${ACTIVE_STATE_FILE}" ]; then
    value="$(sed -n "s/^${key}=//p" "${ACTIVE_STATE_FILE}" | tail -1)"
  fi
  printf '%s\n' "${value:-${fallback}}"
}

validate_identifier() {
  [[ "$1" =~ ^[a-z0-9][a-z0-9_.-]{0,62}$ ]] \
    || fail "identificador tecnico invalido"
}

validate_port() {
  [[ "$1" =~ ^[0-9]+$ ]] && [ "$1" -ge 1024 ] && [ "$1" -le 65535 ] \
    || fail "porta local invalida"
}

candidate_compose() {
  env \
    TOPSV3_RELEASE_SHA="${RELEASE_SHA}" \
    TOPSV3_APP_PREFIX="${CANDIDATE_PREFIX}" \
    TOPSV3_APP_NETWORK="${CANDIDATE_NETWORK}" \
    TOPSV3_BACKEND_BIND_PORT="${CANDIDATE_BACKEND_PORT}" \
    TOPSV3_FRONTEND_BIND_PORT="${CANDIDATE_FRONTEND_PORT}" \
    TOPSV3_GATEWAY_BIND_PORT="${CANDIDATE_GATEWAY_PORT}" \
    APP_SECURITY_AUTH_TRUSTED_PROXY_CIDRS="${CANDIDATE_TRUSTED_PROXY_CIDRS}" \
    EFI_WEBHOOK_REGISTRATION_ENABLED=false \
    docker compose \
      --env-file "${ENV_FILE}" \
      -f "${COMPOSE_FILE}" \
      -p "${CANDIDATE_PROJECT}" \
      "$@"
}

container_project_is_candidate() {
  local container="$1"
  [ "$(docker inspect "${container}" --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null)" = "${CANDIDATE_PROJECT}" ]
}

wait_for_container_exit() {
  local container="$1"
  local timeout_seconds="$2"
  local started elapsed
  started="$(date +%s)"
  while container_is_running "${container}"; do
    elapsed=$(($(date +%s) - started))
    if [ "${elapsed}" -ge "${timeout_seconds}" ]; then
      return 1
    fi
    sleep "${SHUTDOWN_POLL_SECONDS}"
  done
}

shutdown_container_gracefully() {
  local container="$1"
  local role="$2"
  local stop_pid
  docker inspect "${container}" >/dev/null 2>&1 || return 0
  if ! container_is_running "${container}"; then
    log "CONTAINER_ALREADY_STOPPED role=${role} container=${container}"
    return 0
  fi

  log "CONTAINER_SIGTERM role=${role} container=${container} timeout_seconds=${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}"
  docker stop --signal=TERM --time -1 "${container}" >/dev/null 2>&1 &
  stop_pid=$!
  if ! wait_for_container_exit "${container}" "${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}"; then
    kill -TERM "${stop_pid}" >/dev/null 2>&1 || true
    wait "${stop_pid}" 2>/dev/null || true
    log "GRACEFUL_SHUTDOWN_FAILED role=${role} container=${container} preserved=true"
    return 1
  fi
  wait "${stop_pid}" 2>/dev/null || true
  container_is_running "${container}" && return 1
  log "CONTAINER_GRACEFUL_SHUTDOWN_COMPLETED role=${role} container=${container}"
}

remove_stopped_container() {
  local container="$1"
  docker inspect "${container}" >/dev/null 2>&1 || return 0
  if container_is_running "${container}"; then
    log "CONTAINER_REMOVAL_BLOCKED container=${container} reason=still_running"
    return 1
  fi
  docker rm "${container}" >/dev/null || return 1
}

remove_candidate() {
  local container
  for container in "${CANDIDATE_GATEWAY}" "${CANDIDATE_FRONTEND}" "${CANDIDATE_BACKEND}"; do
    if container_project_is_candidate "${container}"; then
      shutdown_container_gracefully "${container}" candidate || return 1
    fi
  done
  for container in "${CANDIDATE_GATEWAY}" "${CANDIDATE_FRONTEND}" "${CANDIDATE_BACKEND}"; do
    if container_project_is_candidate "${container}"; then
      remove_stopped_container "${container}" || return 1
    fi
  done
  docker network disconnect "${CANDIDATE_NETWORK}" "${POSTGRES_CONTAINER}" >/dev/null 2>&1 || true
  if docker network inspect "${CANDIDATE_NETWORK}" >/dev/null 2>&1; then
    local network_project
    network_project="$(docker network inspect "${CANDIDATE_NETWORK}" --format '{{index .Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
    if [ "${network_project}" = "${CANDIDATE_PROJECT}" ]; then
      docker network rm "${CANDIDATE_NETWORK}" >/dev/null 2>&1 || true
    fi
  fi
}

verify_candidate_job_isolation() {
  local registration_enabled
  registration_enabled="$(docker inspect "${CANDIDATE_BACKEND}" \
    --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | sed -n 's/^EFI_WEBHOOK_REGISTRATION_ENABLED=//p')" || return 1
  [ "${registration_enabled}" = false ] || return 1
  log "CANDIDATE_EFI_WEBHOOK_REGISTRATION_ENABLED=false"
}

wait_for_json_up() {
  local url="$1"
  local attempts="${2:-60}"
  local body
  body="$(mktemp "${RUNTIME_DIR}/probe.XXXXXX")" || return 1
  local attempt code
  for attempt in $(seq 1 "${attempts}"); do
    code="$(curl --silent --show-error --max-time 4 --output "${body}" --write-out '%{http_code}' "${url}" 2>/dev/null || true)"
    if [ "${code}" = 200 ] && grep -q '"status":"UP"' "${body}"; then
      rm -f -- "${body}"
      return 0
    fi
    sleep 2
  done
  rm -f -- "${body}"
  return 1
}

expect_http_status() {
  local url="$1"
  local accepted="$2"
  local code
  code="$(curl --silent --show-error --max-time 8 --output /dev/null --write-out '%{http_code}' "${url}" 2>/dev/null || true)"
  case ",${accepted}," in
    *",${code},"*) return 0 ;;
    *) return 1 ;;
  esac
}

expect_json_up_once() {
  local url="$1"
  local body code
  body="$(mktemp "${RUNTIME_DIR}/probe-once.XXXXXX")" || return 1
  code="$(curl --silent --show-error --max-time 4 --output "${body}" --write-out '%{http_code}' "${url}" 2>/dev/null || true)"
  if [ "${code}" = 200 ] && grep -q '"status":"UP"' "${body}"; then
    rm -f -- "${body}"
    return 0
  fi
  rm -f -- "${body}"
  return 1
}

container_is_running() {
  [ "$(docker inspect "$1" --format '{{.State.Running}}' 2>/dev/null)" = true ]
}

log_runtime_resources() {
  local containers=(
    "${ACTIVE_PREFIX}-backend"
    "${ACTIVE_PREFIX}-frontend"
    "${ACTIVE_PREFIX}-gateway"
    "${CANDIDATE_BACKEND}"
    "${CANDIDATE_FRONTEND}"
    "${CANDIDATE_GATEWAY}"
  )
  local stats name cpu memory
  stats="$(docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}' "${containers[@]}" 2>/dev/null)" || return 1
  while IFS='|' read -r name cpu memory; do
    [ -n "${name}" ] || continue
    log "RESOURCE container=${name} cpu=${cpu} memory=${memory}"
  done <<< "${stats}"
}

verify_coexistence_capacity() {
  local container available_memory_mb load_one cpus
  for container in \
    "${ACTIVE_PREFIX}-backend" \
    "${ACTIVE_PREFIX}-frontend" \
    "${ACTIVE_PREFIX}-gateway" \
    "${CANDIDATE_BACKEND}" \
    "${CANDIDATE_FRONTEND}" \
    "${CANDIDATE_GATEWAY}"; do
    container_is_running "${container}" || return 1
  done

  log_runtime_resources || return 1
  available_memory_mb="$(awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo)"
  load_one="$(awk '{ print $1 }' /proc/loadavg)"
  cpus="$(nproc)"
  [[ "${available_memory_mb}" =~ ^[0-9]+$ ]] || return 1
  [[ "${cpus}" =~ ^[0-9]+$ ]] && [ "${cpus}" -gt 0 ] || return 1
  log "HOST_RESOURCE available_memory_mb=${available_memory_mb} load_one=${load_one} cpus=${cpus}"
  [ "${available_memory_mb}" -ge "${MIN_AVAILABLE_MEMORY_MB}" ] || return 1
  awk -v load="${load_one}" -v cpus="${cpus}" -v limit="${MAX_LOAD_PER_CPU}" \
    'BEGIN { exit !(load <= cpus * limit) }'
}

old_gateway_connection_count() {
  command -v ss >/dev/null 2>&1 || return 2
  local connections
  connections="$(ss -Htn state established \
    "( sport = :${ACTIVE_GATEWAY_PORT} or dport = :${ACTIVE_GATEWAY_PORT} )" 2>/dev/null)" || return 2
  awk 'NF { count++ } END { print count + 0 }' <<< "${connections}"
}

verify_old_release_running() {
  local container
  for container in "${ACTIVE_PREFIX}-backend" "${ACTIVE_PREFIX}-frontend" "${ACTIVE_PREFIX}-gateway"; do
    container_is_running "${container}" || return 1
  done
}

verify_candidate_after_switch() {
  grep -Fq "127.0.0.1:${CANDIDATE_GATEWAY_PORT}" "${HOST_NGINX_SITE}" || return 1
  if [ "${ACTIVE_GATEWAY_PORT}" != "${CANDIDATE_GATEWAY_PORT}" ] \
    && grep -Fq "127.0.0.1:${ACTIVE_GATEWAY_PORT}" "${HOST_NGINX_SITE}"; then
    return 1
  fi
  expect_json_up_once "http://127.0.0.1:${CANDIDATE_BACKEND_PORT}/api/health/readiness" || return 1
  expect_json_up_once "http://127.0.0.1:${CANDIDATE_FRONTEND_PORT}/health/readiness" || return 1
  expect_json_up_once "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/api/health/readiness" || return 1
  expect_json_up_once "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/health/readiness" || return 1
  expect_http_status "${PUBLIC_ORIGIN}/" 200 || return 1
  expect_http_status "${PUBLIC_ORIGIN}/anuncios" 200 || return 1
  verify_candidate_logs
}

start_continuity() {
  CONTINUITY_LOG="$(mktemp "${RUNTIME_DIR}/continuity.XXXXXX")" || return 1
  CONTINUITY_STOP="$(mktemp "${RUNTIME_DIR}/continuity-stop.XXXXXX")" || return 1
  (
    while [ -e "${CONTINUITY_STOP}" ]; do
      for path in / /anuncios; do
        code="$(curl --silent --show-error --max-time 4 --output /dev/null --write-out '%{http_code}' "${PUBLIC_ORIGIN}${path}" 2>/dev/null || true)"
        printf '%s|%s\n' "${path}" "${code:-000}" >> "${CONTINUITY_LOG}"
      done
      sleep 0.25
    done
  ) &
  CONTINUITY_PID=$!
}

stop_continuity() {
  if [ -n "${CONTINUITY_STOP:-}" ]; then
    rm -f -- "${CONTINUITY_STOP}"
  fi
  if [ -n "${CONTINUITY_PID:-}" ]; then
    wait "${CONTINUITY_PID}" 2>/dev/null || true
  fi
}

assert_continuity() {
  local requests failures
  requests="$(wc -l < "${CONTINUITY_LOG}")"
  failures="$(awk -F'|' '$2 != "200" { count++ } END { print count + 0 }' "${CONTINUITY_LOG}")"
  log "CONTINUITY_REQUESTS=${requests}"
  log "CONTINUITY_FAILURES=${failures}"
  [ "${requests}" -gt 0 ] && [ "${failures}" -eq 0 ]
}

prepare_candidate() {
  log "STAGE=CANDIDATE_PREPARE"
  remove_candidate
  candidate_compose config --quiet || return 1
  candidate_compose build backend frontend || return 1
  candidate_compose up --no-start --no-deps backend >/dev/null || return 1
  docker network inspect "${CANDIDATE_NETWORK}" >/dev/null || return 1
  docker network connect --alias postgres "${CANDIDATE_NETWORK}" "${POSTGRES_CONTAINER}" || return 1

  local candidate_subnet
  candidate_subnet="$(docker network inspect "${CANDIDATE_NETWORK}" --format '{{(index .IPAM.Config 0).Subnet}}')" || return 1
  [ -n "${candidate_subnet}" ] || return 1
  CANDIDATE_TRUSTED_PROXY_CIDRS="${candidate_subnet},127.0.0.0/8,::1/128"

  candidate_compose up -d --no-deps --force-recreate backend frontend gateway || return 1
  verify_candidate_job_isolation || return 1
}

verify_database_gate() {
  local row
  docker exec "${POSTGRES_CONTAINER}" psql \
    --no-psqlrc --set=ON_ERROR_STOP=1 \
    -U "${DATABASE_USER}" -d "${DATABASE_NAME}" -At \
    --command 'SELECT 1;' </dev/null | grep -qx '1' || return 1
  row="$(docker exec "${POSTGRES_CONTAINER}" psql \
    --no-psqlrc --set=ON_ERROR_STOP=1 \
    -U "${DATABASE_USER}" -d "${DATABASE_NAME}" -At -F '|' \
    --command "
      SELECT
        COALESCE((
          SELECT version
          FROM flyway_schema_history
          WHERE success AND version ~ '^[0-9]+$'
          ORDER BY version::integer DESC, installed_rank DESC
          LIMIT 1
        ), 'AUSENTE'),
        count(*) FILTER (WHERE NOT success)
      FROM flyway_schema_history;
    " </dev/null)" || return 1
  [ "${row}" = "${EXPECTED_FLYWAY}|0" ]
}

verify_candidate_dns() {
  local resolved_backend candidate_backend_ip active_backend_ip frontend_internal_api
  frontend_internal_api="$(docker inspect "${CANDIDATE_FRONTEND}" --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^INTERNAL_API_URL=//p')" || return 1
  [ "${frontend_internal_api}" = "http://backend:8080/api/public" ] || return 1
  candidate_backend_ip="$(docker inspect "${CANDIDATE_BACKEND}" --format "{{(index .NetworkSettings.Networks \"${CANDIDATE_NETWORK}\").IPAddress}}")" || return 1
  resolved_backend="$(docker exec "${CANDIDATE_FRONTEND}" node -e "require('node:dns').lookup('backend',(e,a)=>{if(e)process.exit(1);process.stdout.write(a)})")" || return 1
  [ -n "${candidate_backend_ip}" ] && [ "${resolved_backend}" = "${candidate_backend_ip}" ] || return 1

  active_backend_ip="$(docker inspect "${ACTIVE_PREFIX}-backend" --format "{{(index .NetworkSettings.Networks \"${ACTIVE_NETWORK}\").IPAddress}}" 2>/dev/null || true)"
  [ -z "${active_backend_ip}" ] || [ "${resolved_backend}" != "${active_backend_ip}" ]
}

verify_candidate_logs() {
  local container
  for container in "${CANDIDATE_BACKEND}" "${CANDIDATE_FRONTEND}" "${CANDIDATE_GATEWAY}"; do
    [ "$(docker inspect "${container}" --format '{{.State.Running}}')" = true ] || return 1
    if docker logs "${container}" 2>&1 | grep -Eqi 'APPLICATION FAILED TO START|OutOfMemoryError|FATAL:|panic:|emerg'; then
      return 1
    fi
  done
}

verify_candidate() {
  log "STAGE=CANDIDATE_GATES"
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_BACKEND_PORT}/api/health/liveness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_BACKEND_PORT}/api/health/readiness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_FRONTEND_PORT}/health/liveness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_FRONTEND_PORT}/health/readiness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/api/health/liveness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/api/health/readiness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/health/liveness" || return 1
  wait_for_json_up "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/health/readiness" || return 1
  expect_http_status "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/" 200 || return 1
  expect_http_status "http://127.0.0.1:${CANDIDATE_GATEWAY_PORT}/anuncios" 200 || return 1
  verify_database_gate || return 1
  verify_candidate_dns || return 1
  verify_candidate_logs || return 1
}

validate_gateway_candidate() {
  log "STAGE=GATEWAY_VALIDATE"
  grep -Fq "127.0.0.1:${ACTIVE_GATEWAY_PORT}" "${HOST_NGINX_SITE}" || return 1
  cp -- "${HOST_NGINX_SITE}" "${GATEWAY_CONFIG_BACKUP}" || return 1
  chmod 600 "${GATEWAY_CONFIG_BACKUP}" || return 1
  sed "s#127.0.0.1:${ACTIVE_GATEWAY_PORT}#127.0.0.1:${CANDIDATE_GATEWAY_PORT}#g" \
    "${GATEWAY_CONFIG_BACKUP}" > "${GATEWAY_CONFIG_CANDIDATE}" || return 1
  grep -Fq "127.0.0.1:${CANDIDATE_GATEWAY_PORT}" "${GATEWAY_CONFIG_CANDIDATE}" || return 1
  GATEWAY_CONFIG_STAGED=1
  cp -- "${GATEWAY_CONFIG_CANDIDATE}" "${HOST_NGINX_SITE}" || return 1
  if ! sudo -n nginx -t >/dev/null 2>&1; then
    restore_staged_gateway_config || true
    return 1
  fi
  cp -- "${GATEWAY_CONFIG_BACKUP}" "${HOST_NGINX_SITE}" || return 1
  sudo -n nginx -t >/dev/null 2>&1 || return 1
  GATEWAY_CONFIG_STAGED=0
  GATEWAY_CONFIG_VALIDATED=1
}

switch_gateway() {
  log "STAGE=TRAFFIC_SWITCH"
  [ "${GATEWAY_CONFIG_VALIDATED}" -eq 1 ] || return 1
  GATEWAY_CONFIG_STAGED=1
  cp -- "${GATEWAY_CONFIG_CANDIDATE}" "${HOST_NGINX_SITE}" || return 1
  sudo -n nginx -t >/dev/null 2>&1 || return 1
  # A falha do comando nao prova que o Nginx deixou de aplicar o reload.
  # A partir deste ponto, todo erro exige rollback explicito antes do cleanup.
  GATEWAY_SWITCHED=1
  sudo -n systemctl reload nginx || return 1
  GATEWAY_CONFIG_STAGED=0
}

smoke_public() {
  log "STAGE=PUBLIC_SMOKE"
  expect_http_status "${PUBLIC_ORIGIN}/" 200 || return 1
  expect_http_status "${PUBLIC_ORIGIN}/api/health" 200 || return 1
  expect_http_status "${PUBLIC_ORIGIN}/anuncios" 200 || return 1
  expect_http_status "${PUBLIC_ORIGIN}/anuncios?busca=deploy-smoke" 200 || return 1
  expect_http_status "${PUBLIC_ORIGIN}/api/public/auth/me" 200,401,403 || return 1
}

drain_window() {
  log "STAGE=POST_SWITCH_MONITOR min_seconds=${DRAIN_MIN_SECONDS} max_seconds=${DRAIN_MAX_SECONDS}"
  local started now elapsed connections connection_check=UNAVAILABLE stable_zero=0
  started="$(date +%s)"

  while true; do
    verify_candidate_after_switch || return 1
    verify_old_release_running || return 1
    verify_coexistence_capacity || return 1

    if connections="$(old_gateway_connection_count)"; then
      connection_check=VERIFIED
      log "OLD_GATEWAY_CONNECTIONS=${connections}"
      if [ "${connections}" -eq 0 ]; then
        stable_zero=$((stable_zero + 1))
      else
        stable_zero=0
      fi
    else
      connection_check=UNAVAILABLE
      stable_zero=0
      log "OLD_GATEWAY_CONNECTIONS=UNAVAILABLE"
    fi

    now="$(date +%s)"
    elapsed=$((now - started))
    log "DRAIN_ELAPSED_SECONDS=${elapsed} connection_check=${connection_check} stable_zero=${stable_zero}"
    if [ "${elapsed}" -ge "${DRAIN_MIN_SECONDS}" ]; then
      if [ "${connection_check}" = VERIFIED ] && [ "${stable_zero}" -ge "${DRAIN_ZERO_STABLE_CHECKS}" ]; then
        log "DRAIN_RESULT=CONNECTIONS_DRAINED"
        return 0
      fi
      if [ "${connection_check}" = UNAVAILABLE ]; then
        log "DRAIN_RESULT=MINIMUM_WINDOW_WITHOUT_CONNECTION_PROBE"
        return 0
      fi
    fi
    if [ "${elapsed}" -ge "${DRAIN_MAX_SECONDS}" ]; then
      log "DRAIN_RESULT=ACTIVE_CONNECTIONS_REMAIN"
      return 1
    fi
    sleep "${DRAIN_POLL_SECONDS}"
  done
}

restore_staged_gateway_config() {
  if [ "${GATEWAY_CONFIG_STAGED}" -eq 1 ] && [ "${GATEWAY_SWITCHED}" -eq 0 ]; then
    cp -- "${GATEWAY_CONFIG_BACKUP}" "${HOST_NGINX_SITE}" || return 1
    sudo -n nginx -t >/dev/null 2>&1 || return 1
    GATEWAY_CONFIG_STAGED=0
  fi
}

restore_gateway() {
  log "STAGE=TRAFFIC_ROLLBACK"
  cp -- "${GATEWAY_CONFIG_BACKUP}" "${HOST_NGINX_SITE}" || return 1
  sudo -n nginx -t >/dev/null 2>&1 || return 1
  sudo -n systemctl reload nginx || return 1
  GATEWAY_SWITCHED=0
  GATEWAY_CONFIG_STAGED=0
}

verify_active_release() {
  wait_for_json_up "http://127.0.0.1:${ACTIVE_BACKEND_PORT}/api/health" 15 || return 1
  expect_http_status "http://127.0.0.1:${ACTIVE_GATEWAY_PORT}/" 200 || return 1
  expect_http_status "${PUBLIC_ORIGIN}/" 200 || return 1
}

write_active_state() {
  local temporary
  temporary="$(mktemp "${RUNTIME_DIR}/active-release.XXXXXX")" || return 1
  {
    printf 'ACTIVE_RELEASE_SHA=%s\n' "${RELEASE_SHA}"
    printf 'ACTIVE_RELEASE_DIR=%s\n' "${RELEASE_DIR}"
    printf 'ACTIVE_PROJECT=%s\n' "${CANDIDATE_PROJECT}"
    printf 'ACTIVE_PREFIX=%s\n' "${CANDIDATE_PREFIX}"
    printf 'ACTIVE_NETWORK=%s\n' "${CANDIDATE_NETWORK}"
    printf 'ACTIVE_BACKEND_PORT=%s\n' "${CANDIDATE_BACKEND_PORT}"
    printf 'ACTIVE_FRONTEND_PORT=%s\n' "${CANDIDATE_FRONTEND_PORT}"
    printf 'ACTIVE_GATEWAY_PORT=%s\n' "${CANDIDATE_GATEWAY_PORT}"
  } > "${temporary}" || return 1
  chmod 600 "${temporary}" || return 1
  mv -f -- "${temporary}" "${ACTIVE_STATE_FILE}" || return 1
  STATE_UPDATED=1
}

update_current_link() {
  local temporary
  temporary="${DEPLOY_ROOT}/.current-${RELEASE_SHA}"
  rm -f -- "${temporary}" || return 1
  ln -s "releases/${RELEASE_SHA}" "${temporary}" || return 1
  mv -Tf -- "${temporary}" "${DEPLOY_ROOT}/current" || return 1
  RELEASE_LINK_UPDATED=1
}

restore_current_link() {
  if [ "${RELEASE_LINK_UPDATED}" -eq 1 ] && [ -d "${ACTIVE_RELEASE_DIR}" ]; then
    local temporary
    temporary="${DEPLOY_ROOT}/.rollback-${RELEASE_SHA}"
    rm -f -- "${temporary}" || true
    ln -s "releases/$(basename "${ACTIVE_RELEASE_DIR}")" "${temporary}" || return 1
    mv -Tf -- "${temporary}" "${DEPLOY_ROOT}/current" || return 1
    RELEASE_LINK_UPDATED=0
  fi
}

restore_active_state() {
  if [ "${STATE_UPDATED}" -eq 1 ]; then
    if [ -r "${ACTIVE_STATE_BACKUP}" ]; then
      cp -- "${ACTIVE_STATE_BACKUP}" "${ACTIVE_STATE_FILE}" || return 1
    else
      rm -f -- "${ACTIVE_STATE_FILE}" || return 1
    fi
    STATE_UPDATED=0
  fi
}

restart_stopped_old_release() {
  local container
  for container in "${ACTIVE_PREFIX}-backend" "${ACTIVE_PREFIX}-frontend" "${ACTIVE_PREFIX}-gateway"; do
    if docker inspect "${container}" >/dev/null 2>&1 && ! container_is_running "${container}"; then
      docker start "${container}" >/dev/null || return 1
    fi
  done
}

shutdown_old_release_gracefully() {
  log "STAGE=OLD_RELEASE_GRACEFUL_SHUTDOWN timeout_seconds=${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}"
  local connections container
  if connections="$(old_gateway_connection_count)"; then
    log "OLD_GATEWAY_CONNECTIONS_BEFORE_SHUTDOWN=${connections}"
    [ "${connections}" -eq 0 ] || return 1
  else
    log "OLD_GATEWAY_CONNECTIONS_BEFORE_SHUTDOWN=UNAVAILABLE"
  fi

  for container in "${ACTIVE_PREFIX}-gateway" "${ACTIVE_PREFIX}-frontend" "${ACTIVE_PREFIX}-backend"; do
    if docker inspect "${container}" >/dev/null 2>&1; then
      if ! shutdown_container_gracefully "${container}" active; then
        restart_stopped_old_release || true
        return 1
      fi
      if container_is_running "${container}"; then
        restart_stopped_old_release || true
        return 1
      fi
    fi
  done
  log "OLD_RELEASE_SHUTDOWN=GRACEFUL"
}

remove_old_runtime() {
  local container flyway_container network_project remaining
  for container in "${ACTIVE_PREFIX}-gateway" "${ACTIVE_PREFIX}-frontend" "${ACTIVE_PREFIX}-backend"; do
    if docker inspect "${container}" >/dev/null 2>&1 && ! container_is_running "${container}"; then
      docker rm "${container}" >/dev/null || return 1
    fi
  done

  flyway_container="${ACTIVE_PREFIX}-flyway"
  if docker inspect "${flyway_container}" >/dev/null 2>&1 \
    && [ "$(docker inspect "${flyway_container}" --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null)" = "${ACTIVE_PROJECT}" ] \
    && ! container_is_running "${flyway_container}"; then
    docker rm "${flyway_container}" >/dev/null || return 1
  fi

  if [ "${ACTIVE_NETWORK}" = topsv3-production-net ]; then
    log "OLD_NETWORK_PRESERVED=CANONICAL_DATABASE_NETWORK"
  elif docker network inspect "${ACTIVE_NETWORK}" >/dev/null 2>&1; then
    docker network disconnect "${ACTIVE_NETWORK}" "${POSTGRES_CONTAINER}" >/dev/null 2>&1 || true
    remaining="$(docker network inspect "${ACTIVE_NETWORK}" --format '{{range .Containers}}{{println .Name}}{{end}}' 2>/dev/null || true)"
    network_project="$(docker network inspect "${ACTIVE_NETWORK}" --format '{{index .Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
    if [ -z "${remaining}" ] && [ "${network_project}" = "${ACTIVE_PROJECT}" ]; then
      docker network rm "${ACTIVE_NETWORK}" >/dev/null || return 1
    elif [ -n "${remaining}" ]; then
      log "WARNING=OLD_NETWORK_PRESERVED reason=attached_containers"
      return 1
    else
      log "WARNING=OLD_NETWORK_PRESERVED reason=ownership_unconfirmed"
      return 1
    fi
  fi
  log "OLD_RELEASE_RUNTIME_RESIDUALS=0"
}

finalize_activation() {
  log "STAGE=FINALIZE"
  shutdown_old_release_gracefully || return 1
  if ! update_current_link || ! write_active_state; then
    restart_stopped_old_release || true
    return 1
  fi
  if ! remove_old_runtime; then
    log "WARNING=OLD_RUNTIME_CLEANUP_DEFERRED"
  fi
  log "ACTIVE_RELEASE=${RELEASE_SHA}"
}

deployment_error() {
  local rc="$1"
  local traffic_restored=1
  stop_continuity
  if [ "${GATEWAY_SWITCHED}" -eq 1 ]; then
    if ! restore_gateway || ! verify_active_release; then
      traffic_restored=0
    fi
  else
    if ! restore_staged_gateway_config; then
      traffic_restored=0
    fi
  fi
  restore_current_link || true
  restore_active_state || true
  if [ "${traffic_restored}" -eq 1 ]; then
    remove_candidate
  else
    log "CRITICAL=TRAFFIC_ROLLBACK_UNCONFIRMED candidate_preserved=true"
  fi
  [ -z "${CONTINUITY_LOG:-}" ] || rm -f -- "${CONTINUITY_LOG}"
  [ -z "${CONTINUITY_STOP:-}" ] || rm -f -- "${CONTINUITY_STOP}"
  return "${rc}"
}

stage_or_abort() {
  local stage="$1"
  if "${stage}"; then
    return 0
  else
    local rc=$?
  fi
  deployment_error "${rc}"
  return "${rc}"
}

atomic_activate() {
  stage_or_abort start_continuity || return $?
  stage_or_abort prepare_candidate || return $?
  stage_or_abort verify_candidate || return $?
  stage_or_abort verify_coexistence_capacity || return $?
  stage_or_abort validate_gateway_candidate || return $?
  stage_or_abort switch_gateway || return $?
  stage_or_abort smoke_public || return $?
  stage_or_abort drain_window || return $?
  stage_or_abort smoke_public || return $?
  stop_continuity
  stage_or_abort assert_continuity || return $?
  stage_or_abort finalize_activation || return $?
  [ -z "${CONTINUITY_LOG:-}" ] || rm -f -- "${CONTINUITY_LOG}"
  [ -z "${CONTINUITY_STOP:-}" ] || rm -f -- "${CONTINUITY_STOP}"
  log "RESULT=SUCCESS"
}

initialize_production() {
  [ "$#" -eq 6 ] || fail "uso: $0 RELEASE_SHA RELEASE_DIR ENV_FILE EXPECTED_FLYWAY DATABASE_USER DATABASE_NAME"
  RELEASE_SHA="$1"
  RELEASE_DIR="$2"
  ENV_FILE="$3"
  EXPECTED_FLYWAY="$4"
  DATABASE_USER="$5"
  DATABASE_NAME="$6"

  [[ "${RELEASE_SHA}" =~ ^[0-9a-f]{40}$ ]] || fail "SHA de release invalido"
  [[ "${EXPECTED_FLYWAY}" =~ ^[0-9]+$ ]] || fail "versao Flyway invalida"
  [ -d "${RELEASE_DIR}" ] || fail "diretorio da release ausente"
  [ -r "${ENV_FILE}" ] || fail "arquivo de ambiente indisponivel"

  DEPLOY_ROOT="${TOPSV3_DEPLOY_ROOT:-/opt/topsv3/production}"
  RUNTIME_DIR="${DEPLOY_ROOT}/runtime"
  ACTIVE_STATE_FILE="${RUNTIME_DIR}/active-release.env"
  HOST_NGINX_SITE="${TOPSV3_HOST_NGINX_SITE:-/etc/nginx/sites-enabled/topsdojob-live}"
  PUBLIC_ORIGIN="${TOPSV3_PUBLIC_ORIGIN:-https://topsdojob.com}"
  POSTGRES_CONTAINER="${TOPSV3_POSTGRES_CONTAINER:-topsv3-production-postgres}"
  DRAIN_MIN_SECONDS="${TOPSV3_DRAIN_MIN_SECONDS:-600}"
  DRAIN_MAX_SECONDS="${TOPSV3_DRAIN_MAX_SECONDS:-1800}"
  DRAIN_POLL_SECONDS="${TOPSV3_DRAIN_POLL_SECONDS:-10}"
  DRAIN_ZERO_STABLE_CHECKS="${TOPSV3_DRAIN_ZERO_STABLE_CHECKS:-3}"
  RELEASE_SHUTDOWN_TIMEOUT_SECONDS="${TOPSV3_RELEASE_SHUTDOWN_TIMEOUT_SECONDS:-${TOPSV3_OLD_SHUTDOWN_TIMEOUT_SECONDS:-300}}"
  SHUTDOWN_POLL_SECONDS="${TOPSV3_SHUTDOWN_POLL_SECONDS:-1}"
  SPRING_SHUTDOWN_PHASE_TIMEOUT_SECONDS=120
  SMTP_CONNECTION_TIMEOUT_SECONDS=5
  SMTP_READ_TIMEOUT_SECONDS=10
  SMTP_WRITE_TIMEOUT_SECONDS=10
  MIN_AVAILABLE_MEMORY_MB="${TOPSV3_MIN_AVAILABLE_MEMORY_MB:-512}"
  MAX_LOAD_PER_CPU="${TOPSV3_MAX_LOAD_PER_CPU:-4.0}"
  COMPOSE_FILE="${RELEASE_DIR}/deploy/production/docker-compose.yml"

  for value in \
    "${DRAIN_MIN_SECONDS}" \
    "${DRAIN_MAX_SECONDS}" \
    "${DRAIN_POLL_SECONDS}" \
    "${DRAIN_ZERO_STABLE_CHECKS}" \
    "${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}" \
    "${SHUTDOWN_POLL_SECONDS}" \
    "${MIN_AVAILABLE_MEMORY_MB}"; do
    [[ "${value}" =~ ^[0-9]+$ ]] && [ "${value}" -gt 0 ] || fail "parametro numerico de drenagem invalido"
  done
  [ "${DRAIN_MAX_SECONDS}" -ge "${DRAIN_MIN_SECONDS}" ] || fail "janela maxima menor que a minima"
  [ "${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}" -gt "${SPRING_SHUTDOWN_PHASE_TIMEOUT_SECONDS}" ] \
    || fail "timeout de shutdown deve superar o ciclo gracioso do Spring"
  [ "${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}" -gt "${SMTP_CONNECTION_TIMEOUT_SECONDS}" ] \
    && [ "${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}" -gt "${SMTP_READ_TIMEOUT_SECONDS}" ] \
    && [ "${RELEASE_SHUTDOWN_TIMEOUT_SECONDS}" -gt "${SMTP_WRITE_TIMEOUT_SECONDS}" ] \
    || fail "timeout de shutdown deve superar os timeouts SMTP"
  [[ "${MAX_LOAD_PER_CPU}" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "limite de carga invalido"
  log "SHUTDOWN_BUDGET release_seconds=${RELEASE_SHUTDOWN_TIMEOUT_SECONDS} spring_phase_seconds=${SPRING_SHUTDOWN_PHASE_TIMEOUT_SECONDS} smtp_connection_seconds=${SMTP_CONNECTION_TIMEOUT_SECONDS} smtp_read_seconds=${SMTP_READ_TIMEOUT_SECONDS} smtp_write_seconds=${SMTP_WRITE_TIMEOUT_SECONDS}"

  install -d -m 0750 "${RUNTIME_DIR}" || return 1
  [ -r "${COMPOSE_FILE}" ] || fail "Compose da release ausente"
  [ -w "${HOST_NGINX_SITE}" ] || fail "configuracao Nginx nao gravavel pelo usuario de deploy"
  require_command docker || return 1
  require_command curl || return 1
  require_command nproc || return 1
  sudo -n nginx -t >/dev/null 2>&1 || fail "nginx -t indisponivel"
  sudo -n -l /usr/bin/systemctl reload nginx >/dev/null 2>&1 \
    || fail "reload gracioso do Nginx nao autorizado"

  ACTIVE_RELEASE_DIR="$(state_value ACTIVE_RELEASE_DIR "$(readlink -f "${DEPLOY_ROOT}/current")")"
  ACTIVE_PROJECT="$(state_value ACTIVE_PROJECT topsv3-production)"
  ACTIVE_PREFIX="$(state_value ACTIVE_PREFIX topsv3-production)"
  ACTIVE_NETWORK="$(state_value ACTIVE_NETWORK topsv3-production-net)"
  ACTIVE_BACKEND_PORT="$(state_value ACTIVE_BACKEND_PORT 28080)"
  ACTIVE_FRONTEND_PORT="$(state_value ACTIVE_FRONTEND_PORT 23010)"
  ACTIVE_GATEWAY_PORT="$(state_value ACTIVE_GATEWAY_PORT 23000)"

  validate_identifier "${ACTIVE_PROJECT}" || return 1
  validate_identifier "${ACTIVE_PREFIX}" || return 1
  validate_identifier "${ACTIVE_NETWORK}" || return 1
  validate_port "${ACTIVE_BACKEND_PORT}" || return 1
  validate_port "${ACTIVE_FRONTEND_PORT}" || return 1
  validate_port "${ACTIVE_GATEWAY_PORT}" || return 1
  [ "${ACTIVE_RELEASE_DIR}" != "${RELEASE_DIR}" ] || fail "release ja esta ativa"

  if [ "${ACTIVE_GATEWAY_PORT}" = 23000 ]; then
    CANDIDATE_BACKEND_PORT=28081
    CANDIDATE_FRONTEND_PORT=23011
    CANDIDATE_GATEWAY_PORT=23001
  else
    CANDIDATE_BACKEND_PORT=28080
    CANDIDATE_FRONTEND_PORT=23010
    CANDIDATE_GATEWAY_PORT=23000
  fi
  CANDIDATE_PROJECT="topsv3-candidate-${RELEASE_SHA:0:12}"
  CANDIDATE_PREFIX="${CANDIDATE_PROJECT}"
  CANDIDATE_NETWORK="${CANDIDATE_PROJECT}-net"
  CANDIDATE_BACKEND="${CANDIDATE_PREFIX}-backend"
  CANDIDATE_FRONTEND="${CANDIDATE_PREFIX}-frontend"
  CANDIDATE_GATEWAY="${CANDIDATE_PREFIX}-gateway"
  CANDIDATE_TRUSTED_PROXY_CIDRS="127.0.0.0/8,::1/128"

  GATEWAY_CONFIG_BACKUP="${RUNTIME_DIR}/gateway-before-${RELEASE_SHA}.conf"
  GATEWAY_CONFIG_CANDIDATE="${RUNTIME_DIR}/gateway-candidate-${RELEASE_SHA}.conf"
  ACTIVE_STATE_BACKUP="${RUNTIME_DIR}/active-release-before-${RELEASE_SHA}.env"
  if [ -r "${ACTIVE_STATE_FILE}" ]; then
    cp -- "${ACTIVE_STATE_FILE}" "${ACTIVE_STATE_BACKUP}" || return 1
    chmod 600 "${ACTIVE_STATE_BACKUP}" || return 1
  else
    rm -f -- "${ACTIVE_STATE_BACKUP}" || return 1
  fi
  GATEWAY_CONFIG_STAGED=0
  GATEWAY_CONFIG_VALIDATED=0
  GATEWAY_SWITCHED=0
  RELEASE_LINK_UPDATED=0
  STATE_UPDATED=0
  CONTINUITY_LOG=""
  CONTINUITY_STOP=""
  CONTINUITY_PID=""
}

main() {
  initialize_production "$@" || exit $?
  atomic_activate || exit $?
}

if [ "${TOPSV3_ATOMIC_DEPLOY_LIBRARY_ONLY:-false}" != true ]; then
  main "$@"
fi
