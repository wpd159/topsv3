#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
helper="${root}/scripts/deploy/ativar-release-atomica-production.sh"
fixture="${root}/scripts/deploy/fixture-release-atomica.mjs"
workflow="${root}/.github/workflows/deploy-production.yml"
compose="${root}/deploy/production/docker-compose.yml"
outbox_repository="${root}/backend/src/main/java/br/com/topsdojob/v3/persistence/repository/OutboxEventoRepository.java"
efi_concurrency_test="${root}/backend/src/test/java/br/com/topsdojob/v3/application/publico/pagamento/EfiPagamentoPostgres17ConcurrencyIntegrationTest.java"
temporary="$(mktemp -d)"
fixture_state="${temporary}/fixture.port"
fixture_log="${temporary}/fixture.log"

cleanup() {
  if [ -n "${fixture_pid:-}" ]; then
    kill "${fixture_pid}" >/dev/null 2>&1 || true
    wait "${fixture_pid}" 2>/dev/null || true
  fi
  rm -rf -- "${temporary}"
}
trap cleanup EXIT

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  exit 1
}

grep -Fq 'TOPSV3_APP_PREFIX' "${compose}" || fail "prefixo de containers nao parametrizado"
grep -Fq 'TOPSV3_APP_NETWORK' "${compose}" || fail "rede da candidata nao parametrizada"
grep -Fq 'TOPSV3_BACKEND_BIND_PORT' "${compose}" || fail "porta do backend nao parametrizada"
grep -Fq 'TOPSV3_FRONTEND_BIND_PORT' "${compose}" || fail "porta do frontend nao parametrizada"
grep -Fq 'TOPSV3_GATEWAY_BIND_PORT' "${compose}" || fail "porta do gateway nao parametrizada"
grep -Fq 'verify_candidate_dns' "${helper}" || fail "prova DNS da candidata ausente"
grep -Fq 's/^INTERNAL_API_URL=//p' "${helper}" || fail "leitura da API interna candidata ausente"
grep -Fq 'http://backend:8080/api/public' "${helper}" || fail "API interna candidata nao validada"
grep -Fq 'systemctl reload nginx' "${helper}" || fail "reload gracioso ausente"
grep -Fq 'EFI_WEBHOOK_REGISTRATION_ENABLED=false' "${helper}" || fail "registro Efi nao neutralizado na candidata"
grep -Fq 'verify_candidate_job_isolation' "${helper}" || fail "flag Efi efetiva da candidata nao validada"
if grep -Fq 'candidate_compose create --no-deps' "${helper}"; then
  fail "ativador ainda usa compose create --no-deps, incompativel com Compose 5.5"
fi
grep -Fq 'candidate_compose up --no-start --no-deps backend' "${helper}" \
  || fail "criacao parada e sem dependencias da candidata ausente"
if grep -Fq 'docker rm -f' "${helper}"; then
  fail "ativador ainda contem remocao forcada de container"
fi
grep -Fq 'docker stop --signal=TERM --time -1' "${helper}" || fail "SIGTERM sem SIGKILL automatico ausente"
grep -Fq 'CONTAINER_REMOVAL_BLOCKED' "${helper}" || fail "remocao de container ativo nao esta bloqueada"
grep -Fq 'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: 120s' "${compose}" || fail "timeout gracioso Spring nao configurado"
grep -Fq 'SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION: "true"' "${compose}" || fail "scheduler nao aguarda tarefa em andamento"
grep -Fq 'OUTBOX_SMTP_CONNECTION_TIMEOUT_MS: "5000"' "${compose}" || fail "timeout de conexao SMTP nao fixado"
grep -Fq 'OUTBOX_SMTP_TIMEOUT_MS: "10000"' "${compose}" || fail "timeout de leitura SMTP nao fixado"
grep -Fq 'OUTBOX_SMTP_WRITE_TIMEOUT_MS: "10000"' "${compose}" || fail "timeout de escrita SMTP nao fixado"
grep -Fqi 'for update skip locked' "${outbox_repository}" || fail "claim distribuido da outbox ausente"
grep -Fq 'duasInstanciasDoSchedulerProcessamMesmoPagamentoSemDuplicarCredito' "${efi_concurrency_test}" \
  || fail "prova concorrente da reconciliacao Efi ausente"
grep -Fq 'testar-release-atomica-production.sh' "${workflow}" || fail "harness ausente do workflow"
if grep -Fq 'up -d --no-deps --force-recreate backend frontend gateway' "${workflow}"; then
  fail "workflow ainda recria a release ativa"
fi

node "${fixture}" "${fixture_state}" "${fixture_log}" &
fixture_pid=$!
for _ in $(seq 1 100); do
  [ -s "${fixture_state}" ] && break
  sleep 0.05
done
[ -s "${fixture_state}" ] || fail "fixture HTTP nao iniciou"
fixture_port="$(cat "${fixture_state}")"
fixture_origin="http://127.0.0.1:${fixture_port}"

export TOPSV3_ATOMIC_DEPLOY_LIBRARY_ONLY=true
# shellcheck source=ativar-release-atomica-production.sh
source "${helper}"

proxy_control() {
  curl -fsS -X POST "${fixture_origin}/__control/$1" -o /dev/null
}

release_header() {
  curl -fsSI "${fixture_origin}/" | tr -d '\r' | sed -n 's/^x-release: //p'
}

event_line() {
  grep -n -m1 -F "$1" "${EVENT_LOG}" | cut -d: -f1
}

test_candidate_efi_effective_configuration() (
  CANDIDATE_BACKEND=fixture-candidate-backend
  docker() {
    if [ "$1" = inspect ]; then
      printf 'EFI_WEBHOOK_REGISTRATION_ENABLED=false\n'
      return 0
    fi
    return 1
  }
  verify_candidate_job_isolation
)

test_candidate_compose55_creation_sequence() (
  set -euo pipefail
  local_events="${temporary}/compose55-candidate.events"
  : > "${local_events}"

  CANDIDATE_NETWORK=fixture-candidate-net
  POSTGRES_CONTAINER=fixture-production-postgres
  CANDIDATE_TRUSTED_PROXY_CIDRS=""

  remove_candidate() {
    printf 'REMOVE_CANDIDATE\n' >> "${local_events}"
  }

  candidate_compose() {
    printf 'COMPOSE|%s\n' "$*" >> "${local_events}"
  }

  docker() {
    printf 'DOCKER|%s\n' "$*" >> "${local_events}"
    if [ "$1" = network ] && [ "$2" = inspect ] && [[ " $* " == *" --format "* ]]; then
      printf '172.31.255.0/24\n'
    fi
  }

  verify_candidate_job_isolation() {
    printf 'JOB_ISOLATION_OK\n' >> "${local_events}"
  }

  prepare_candidate

  grep -Fxq 'COMPOSE|up --no-start --no-deps backend' "${local_events}"
  ! grep -Fq 'COMPOSE|create ' "${local_events}"
  grep -Fxq 'DOCKER|network connect --alias postgres fixture-candidate-net fixture-production-postgres' "${local_events}"
  grep -Fxq 'COMPOSE|up -d --no-deps --force-recreate backend frontend gateway' "${local_events}"

  create_line="$(grep -n -m1 -F 'COMPOSE|up --no-start --no-deps backend' "${local_events}" | cut -d: -f1)"
  connect_line="$(grep -n -m1 -F 'DOCKER|network connect --alias postgres' "${local_events}" | cut -d: -f1)"
  start_line="$(grep -n -m1 -F 'COMPOSE|up -d --no-deps --force-recreate backend frontend gateway' "${local_events}" | cut -d: -f1)"
  [ "${create_line}" -lt "${connect_line}" ]
  [ "${connect_line}" -lt "${start_line}" ]
  printf 'ok - Compose 5.5 cria candidata parada, conecta banco e inicia somente os servicos candidatos\n'
)

test_slow_smtp_graceful_shutdown() (
  set -euo pipefail
  local_state="${temporary}/smtp-container.state"
  local_events="${temporary}/smtp-graceful.events"
  smtp_committed="${temporary}/smtp-committed"
  printf 'running\n' > "${local_state}"
  : > "${local_events}"

  docker() {
    local command="$1"
    shift
    case "${command}" in
      inspect)
        if [[ " $* " == *" --format "* ]]; then
          if grep -qx running "${local_state}"; then printf 'true\n'; else printf 'false\n'; fi
        fi
        return 0
        ;;
      stop)
        printf 'SIGTERM\n' >> "${local_events}"
        while [ ! -e "${smtp_committed}" ]; do sleep 0.02; done
        printf 'stopped\n' > "${local_state}"
        printf 'CONTAINER_STOPPED\n' >> "${local_events}"
        ;;
      rm)
        ! grep -qx running "${local_state}" || return 1
        printf 'CONTAINER_REMOVED\n' >> "${local_events}"
        ;;
      *) return 1 ;;
    esac
  }

  (
    printf 'SMTP_STARTED\n' >> "${local_events}"
    sleep 0.10
    printf 'SMTP_ACCEPTED\n' >> "${local_events}"
    sleep 0.25
    printf 'SMTP_COMMITTED\n' >> "${local_events}"
    touch "${smtp_committed}"
  ) &
  smtp_pid=$!
  for _ in $(seq 1 100); do
    grep -Fq SMTP_STARTED "${local_events}" && break
    sleep 0.01
  done

  RELEASE_SHUTDOWN_TIMEOUT_SECONDS=5
  SHUTDOWN_POLL_SECONDS=0.02
  shutdown_container_gracefully fixture-smtp-backend candidate
  remove_stopped_container fixture-smtp-backend
  wait "${smtp_pid}"

  term_line="$(grep -n -m1 SIGTERM "${local_events}" | cut -d: -f1)"
  accepted_line="$(grep -n -m1 SMTP_ACCEPTED "${local_events}" | cut -d: -f1)"
  committed_line="$(grep -n -m1 SMTP_COMMITTED "${local_events}" | cut -d: -f1)"
  removed_line="$(grep -n -m1 CONTAINER_REMOVED "${local_events}" | cut -d: -f1)"
  [ "${term_line}" -lt "${accepted_line}" ]
  [ "${accepted_line}" -lt "${committed_line}" ]
  [ "${committed_line}" -lt "${removed_line}" ]
  printf 'ok - SMTP lento concluiu aceite e commit antes da remocao normal\n'
)

test_failed_graceful_shutdown_preserves_container() (
  set -euo pipefail
  local_events="${temporary}/smtp-timeout.events"
  : > "${local_events}"

  docker() {
    local command="$1"
    shift
    case "${command}" in
      inspect)
        if [[ " $* " == *" --format "* ]]; then printf 'true\n'; fi
        return 0
        ;;
      stop)
        printf 'SIGTERM\n' >> "${local_events}"
        while true; do sleep 0.05; done
        ;;
      rm)
        printf 'UNSAFE_REMOVE\n' >> "${local_events}"
        return 1
        ;;
      *) return 1 ;;
    esac
  }

  RELEASE_SHUTDOWN_TIMEOUT_SECONDS=1
  SHUTDOWN_POLL_SECONDS=0.05
  if shutdown_container_gracefully fixture-stuck-backend candidate; then
    return 1
  fi
  ! grep -Fq UNSAFE_REMOVE "${local_events}"
  grep -Fq SIGTERM "${local_events}"
  printf 'ok - falha graciosa preservou container sem remocao forcada\n'
)

test_candidate_efi_effective_configuration
test_candidate_compose55_creation_sequence
test_slow_smtp_graceful_shutdown
test_failed_graceful_shutdown_preserves_container

run_scenario() (
  set -u
  SCENARIO="$1"
  EVENT_LOG="${temporary}/${SCENARIO}.events"
  HARNESS_STATUS_LOG="${temporary}/${SCENARIO}.status"
  : > "${EVENT_LOG}"
  : > "${HARNESS_STATUS_LOG}"
  : > "${fixture_log}"
  proxy_control old
  proxy_control start-old
  proxy_control start-candidate

  GATEWAY_SWITCHED=0
  GATEWAY_CONFIG_STAGED=0
  GATEWAY_CONFIG_VALIDATED=0
  RELEASE_LINK_UPDATED=0
  STATE_UPDATED=0
  CONTINUITY_LOG=""
  CONTINUITY_STOP=""
  CONTINUITY_PID=""
  CANDIDATE_READY=0
  SMOKE_COUNT=0
  LONG_PID=""
  STREAM_PID=""
  LONG_STARTED_AT=0

  scenario_cleanup() {
    local pid
    for pid in "${LONG_PID:-}" "${STREAM_PID:-}"; do
      if [ -n "${pid}" ]; then
        kill "${pid}" >/dev/null 2>&1 || true
        wait "${pid}" 2>/dev/null || true
      fi
    done
  }
  trap scenario_cleanup EXIT

  record_event() {
    printf '%s\n' "$1" >> "${EVENT_LOG}"
  }

  start_continuity() {
    record_event CONTINUITY_START
    (
      local code path
      for request in $(seq 1 200); do
        code="$(curl -sS --max-time 2 -o /dev/null -w '%{http_code}' "${fixture_origin}/" || printf 000)"
        printf 'HOME|%s\n' "${code}" >> "${HARNESS_STATUS_LOG}"
        if [ $((request % 5)) -eq 0 ]; then
          path="/anuncios?busca=atomic-${request}"
          code="$(curl -sS --max-time 2 -o /dev/null -w '%{http_code}' "${fixture_origin}${path}" || printf 000)"
          printf 'LISTING|%s\n' "${code}" >> "${HARNESS_STATUS_LOG}"
        fi
        if [ "${SCENARIO}" = healthy ]; then
          sleep 0.08
        else
          sleep 0.002
        fi
      done
    ) &
    CONTINUITY_PID=$!
  }

  stop_continuity() {
    if [ -n "${CONTINUITY_PID:-}" ]; then
      wait "${CONTINUITY_PID}" || return 1
      CONTINUITY_PID=""
    fi
  }

  assert_continuity() {
    local home_requests listing_requests failures
    home_requests="$(awk -F'|' '$1 == "HOME" { count++ } END { print count + 0 }' "${HARNESS_STATUS_LOG}")"
    listing_requests="$(awk -F'|' '$1 == "LISTING" { count++ } END { print count + 0 }' "${HARNESS_STATUS_LOG}")"
    failures="$(awk -F'|' '$2 != "200" { count++ } END { print count + 0 }' "${HARNESS_STATUS_LOG}")"
    [ "${home_requests}" -eq 200 ] || return 1
    [ "${listing_requests}" -eq 40 ] || return 1
    [ "${failures}" -eq 0 ] || return 1
    record_event "CONTINUITY_200=${home_requests}"
    record_event "LISTING_200=${listing_requests}"
  }

  prepare_candidate() {
    record_event CANDIDATE_PREPARE
    sleep 0.05
  }

  verify_coexistence_capacity() {
    record_event COEXISTENCE_CAPACITY_OK
  }

  active_old_requests() {
    curl -fsS "${fixture_origin}/__active/old"
  }

  old_runtime_residuals() {
    curl -fsS "${fixture_origin}/__residual/old"
  }

  start_long_old_requests() {
    LONG_HEADERS="${temporary}/${SCENARIO}.long.headers"
    LONG_BODY="${temporary}/${SCENARIO}.long.body"
    LONG_STATUS="${temporary}/${SCENARIO}.long.status"
    STREAM_HEADERS="${temporary}/${SCENARIO}.stream.headers"
    STREAM_BODY="${temporary}/${SCENARIO}.stream.body"
    STREAM_STATUS="${temporary}/${SCENARIO}.stream.status"
    LONG_STARTED_AT="$(date +%s)"

    curl --silent --show-error --max-time 30 \
      --dump-header "${LONG_HEADERS}" \
      --output "${LONG_BODY}" \
      --write-out '%{http_code}' \
      "${fixture_origin}/__long?ms=16500" > "${LONG_STATUS}" &
    LONG_PID=$!
    curl --silent --show-error --max-time 30 \
      --dump-header "${STREAM_HEADERS}" \
      --output "${STREAM_BODY}" \
      --write-out '%{http_code}' \
      "${fixture_origin}/__stream?chunks=17&interval=1000" > "${STREAM_STATUS}" &
    STREAM_PID=$!

    local active=0
    for _ in $(seq 1 200); do
      active="$(active_old_requests)"
      [ "${active}" -ge 2 ] && break
      sleep 0.02
    done
    [ "${active}" -ge 2 ] || return 1
    record_event OLD_LONG_REQUESTS_STARTED
  }

  verify_candidate() {
    record_event CANDIDATE_GATES
    sleep 0.05
    case "${SCENARIO}" in
      unhealthy) record_event WIRING_BROKEN; return 1 ;;
      readiness-503) record_event READINESS_503; return 1 ;;
    esac
    CANDIDATE_READY=1
    record_event CANDIDATE_READY
  }

  validate_gateway_candidate() {
    record_event NGINX_VALIDATE
    GATEWAY_CONFIG_STAGED=1
    if [ "${SCENARIO}" = nginx-invalid ]; then
      record_event NGINX_INVALID
      return 1
    fi
    GATEWAY_CONFIG_STAGED=0
    GATEWAY_CONFIG_VALIDATED=1
    record_event NGINX_VALID
    if [ "${SCENARIO}" = healthy ]; then
      start_long_old_requests || return 1
    fi
  }

  switch_gateway() {
    [ "${CANDIDATE_READY}" -eq 1 ] || return 1
    [ "${GATEWAY_CONFIG_VALIDATED}" -eq 1 ] || return 1
    record_event TRAFFIC_SWITCH
    proxy_control candidate
    GATEWAY_SWITCHED=1
  }

  smoke_public() {
    SMOKE_COUNT=$((SMOKE_COUNT + 1))
    record_event "PUBLIC_SMOKE_${SMOKE_COUNT}"
    if [ "${SCENARIO}" = post-switch-failure ] && [ "${SMOKE_COUNT}" -eq 1 ]; then
      record_event PUBLIC_SMOKE_FAILED
      return 1
    fi
    [ "$(release_header)" = candidate ]
  }

  drain_window() {
    record_event POST_SWITCH_MONITOR
    if [ "${SCENARIO}" != healthy ]; then
      sleep 0.05
      return 0
    fi

    [ "$(release_header)" = candidate ] || return 1
    [ "$(active_old_requests)" -ge 2 ] || return 1
    record_event OLD_RELEASE_RUNNING_DURING_MONITOR
    record_event NEW_REQUESTS_CANDIDATE_ONLY

    wait "${LONG_PID}" || return 1
    LONG_PID=""
    wait "${STREAM_PID}" || return 1
    STREAM_PID=""
    local elapsed
    elapsed=$(($(date +%s) - LONG_STARTED_AT))
    [ "${elapsed}" -ge 16 ] || return 1
    [ "$(cat "${LONG_STATUS}")" = 200 ] || return 1
    [ "$(cat "${STREAM_STATUS}")" = 200 ] || return 1
    tr -d '\r' < "${LONG_HEADERS}" | grep -Fqi 'x-release: old' || return 1
    tr -d '\r' < "${STREAM_HEADERS}" | grep -Fqi 'x-release: old' || return 1
    grep -Fq 'old-long' "${LONG_BODY}" || return 1
    grep -Fq 'old-video-chunk-17' "${STREAM_BODY}" || return 1
    [ "$(active_old_requests)" -eq 0 ] || return 1
    printf 'LONG|200\nSTREAM|200\n' >> "${HARNESS_STATUS_LOG}"
    record_event "LONG_REQUEST_COMPLETED_SECONDS=${elapsed}"
    record_event VIDEO_STREAM_COMPLETED
    record_event OLD_CONNECTIONS_DRAINED
  }

  finalize_activation() {
    record_event FINALIZE
    [ "${SMOKE_COUNT}" -ge 2 ] || return 1
    [ "$(active_old_requests)" -eq 0 ] || return 1
    record_event OLD_GRACEFUL_SHUTDOWN_STARTED
    proxy_control stop-old
    record_event OLD_GRACEFUL_SHUTDOWN_COMPLETED
    proxy_control remove-old-runtime
    [ "$(old_runtime_residuals)" -eq 0 ] || return 1
    record_event OLD_RUNTIME_RESIDUALS_0
  }

  restore_staged_gateway_config() {
    record_event STAGED_CONFIG_RESTORED
    GATEWAY_CONFIG_STAGED=0
  }

  restore_gateway() {
    record_event TRAFFIC_ROLLBACK
    proxy_control old
    GATEWAY_SWITCHED=0
    GATEWAY_CONFIG_STAGED=0
  }

  verify_active_release() {
    record_event OLD_RELEASE_VERIFIED
    [ "$(release_header)" = old ]
  }

  remove_candidate() {
    record_event CANDIDATE_REMOVED
    proxy_control remove-candidate
  }

  restore_current_link() { record_event CURRENT_LINK_PRESERVED; }
  restore_active_state() { record_event ACTIVE_STATE_PRESERVED; }

  set +e
  atomic_activate
  rc=$?
  set -e
  stop_continuity
  assert_continuity

  case "${SCENARIO}" in
    healthy)
      [ "${rc}" -eq 0 ] || return 1
      [ "$(release_header)" = candidate ] || return 1
      [ "$(event_line CANDIDATE_READY)" -lt "$(event_line TRAFFIC_SWITCH)" ] || return 1
      [ "$(event_line COEXISTENCE_CAPACITY_OK)" -lt "$(event_line TRAFFIC_SWITCH)" ] || return 1
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line PUBLIC_SMOKE_1)" ] || return 1
      [ "$(event_line OLD_LONG_REQUESTS_STARTED)" -lt "$(event_line TRAFFIC_SWITCH)" ] || return 1
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line LONG_REQUEST_COMPLETED_SECONDS)" ] || return 1
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line VIDEO_STREAM_COMPLETED)" ] || return 1
      [ "$(event_line PUBLIC_SMOKE_2)" -lt "$(event_line OLD_GRACEFUL_SHUTDOWN_STARTED)" ] || return 1
      [ "$(event_line OLD_CONNECTIONS_DRAINED)" -lt "$(event_line OLD_GRACEFUL_SHUTDOWN_STARTED)" ] || return 1
      grep -Fq NEW_REQUESTS_CANDIDATE_ONLY "${EVENT_LOG}" || return 1
      grep -Fq OLD_RELEASE_RUNNING_DURING_MONITOR "${EVENT_LOG}" || return 1
      grep -Fq OLD_RUNTIME_RESIDUALS_0 "${EVENT_LOG}" || return 1
      ;;
    post-switch-failure)
      [ "${rc}" -ne 0 ] || return 1
      [ "$(release_header)" = old ] || return 1
      grep -Fq TRAFFIC_ROLLBACK "${EVENT_LOG}" || return 1
       ! grep -Fq OLD_GRACEFUL_SHUTDOWN_COMPLETED "${EVENT_LOG}" || return 1
      ;;
    *)
      [ "${rc}" -ne 0 ] || return 1
      [ "$(release_header)" = old ] || return 1
      ! grep -Fq TRAFFIC_SWITCH "${EVENT_LOG}" || return 1
       ! grep -Fq OLD_GRACEFUL_SHUTDOWN_COMPLETED "${EVENT_LOG}" || return 1
      grep -Fq CANDIDATE_REMOVED "${EVENT_LOG}" || return 1
      ;;
  esac

  if awk -F'|' '$2 == "500" || $2 == "502" || $2 == "504" || $2 == "000" { found = 1 } END { exit found ? 0 : 1 }' "${HARNESS_STATUS_LOG}"; then
    return 1
  fi
  if [ "${SCENARIO}" = healthy ]; then
    printf 'ok - %s: request >15s + stream preservados, 200 Home + 40 listagem, zero 500/502/504\n' "${SCENARIO}"
  else
    printf 'ok - %s: 200 Home + 40 listagem, zero 500/502/504\n' "${SCENARIO}"
  fi
)

run_scenario unhealthy
run_scenario readiness-503
run_scenario healthy
run_scenario nginx-invalid
run_scenario post-switch-failure

printf 'ATOMIC_RELEASE_DEPLOY_TESTS=PASS\n'
