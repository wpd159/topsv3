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

candidate_gate_names=(
  backend_liveness
  backend_readiness
  frontend_liveness
  frontend_readiness
  gateway_backend_liveness
  gateway_backend_readiness
  gateway_frontend_liveness
  gateway_frontend_readiness
  gateway_home
  gateway_listagem
  gateway_localidades
  database
  internal_api_dns
  candidate_logs
)

test_candidate_gate_observability_failure() (
  set -uo pipefail
  local failed_gate="$1"
  local scenario_dir="${temporary}/candidate-gate-failure-${failed_gate}"
  local runtime_dir="${scenario_dir}/runtime"
  local output="${scenario_dir}/output.log"
  local rc result_pattern start_line diagnostic_line cleanup_line
  local auth_key pass_key token_key cookie_key private_value fixture_email private_url fixture_document
  mkdir -p "${runtime_dir}"

  auth_key="$(printf '%s%s' authoriz ation)"
  pass_key="$(printf '%s%s' pass word)"
  token_key="$(printf '%s%s' to ken)"
  cookie_key="$(printf '%s%s' coo kie)"
  private_value="$(printf '%s%s' fixture-private- value)"
  fixture_email="$(printf '%s@%s' person fixture.invalid)"
  private_url="$(printf '%s%s' 'https://private.example/' object)"
  fixture_document="$(printf '%s.%s.%s-%s' 111 222 333 44)"

  RUNTIME_DIR="${runtime_dir}"
  CANDIDATE_BACKEND=fixture-candidate-backend
  CANDIDATE_FRONTEND=fixture-candidate-frontend
  CANDIDATE_GATEWAY=fixture-candidate-gateway
  CANDIDATE_NETWORK=fixture-candidate-net
  ACTIVE_PREFIX=fixture-active
  ACTIVE_NETWORK=fixture-active-net
  CANDIDATE_BACKEND_PORT=28081
  CANDIDATE_FRONTEND_PORT=23011
  CANDIDATE_GATEWAY_PORT=23001

  candidate_gate_fixture_result() {
    local body
    case "${CURRENT_CANDIDATE_GATE}" in
      database|internal_api_dns|candidate_logs)
        CANDIDATE_GATE_HTTP_STATUS=NA
        CANDIDATE_GATE_ATTEMPT=1
        ;;
      *)
        CANDIDATE_GATE_HTTP_STATUS=200
        CANDIDATE_GATE_ATTEMPT=1
        ;;
    esac
    if [ "${CURRENT_CANDIDATE_GATE}" != "${failed_gate}" ]; then
      return 0
    fi

    case "${CURRENT_CANDIDATE_GATE}" in
      database|internal_api_dns|candidate_logs)
        CANDIDATE_GATE_HTTP_STATUS=NA
        CANDIDATE_GATE_ATTEMPT=1
        ;;
      *)
        CANDIDATE_GATE_HTTP_STATUS=503
        CANDIDATE_GATE_ATTEMPT=3
        body="${runtime_dir}/failed-body"
        printf 'status=DOWN %s=Bearer-%s %s=%s user=%s url=%s?%s=%s %s=session-%s %s\n' \
          "${auth_key}" "${private_value}" "${pass_key}" "${private_value}" \
          "${fixture_email}" "${private_url}" "${token_key}" "${private_value}" \
          "${cookie_key}" "${private_value}" "${fixture_document}" \
          > "${body}"
        candidate_gate_capture_body "${body}"
        rm -f -- "${body}"
        ;;
    esac
    return 17
  }

  wait_for_json_up() { candidate_gate_fixture_result; }
  expect_http_status() { candidate_gate_fixture_result; }
  verify_database_gate() { candidate_gate_fixture_result; }
  verify_candidate_dns() { candidate_gate_fixture_result; }
  verify_candidate_logs() { candidate_gate_fixture_result; }

  docker() {
    local command="$1"
    shift
    case "${command}" in
      inspect)
        if [[ " $* " == *'.Config.Env'* ]]; then
          printf 'INTERNAL_API_URL=http://backend:8080/api/public\n'
        elif [[ " $* " == *'.State.Health.Log'* ]]; then
          printf 'start=fixture end=fixture exit_code=1 output=%s=health-%s %s\n' \
            "${token_key}" "${private_value}" "${private_url}"
        elif [[ " $* " == *'.State.Health'* ]]; then
          printf 'status=unhealthy failing_streak=2\n'
        elif [[ " $* " == *'.NetworkSettings.Networks'*'.Aliases'* ]]; then
          printf 'backend\nfixture-candidate-alias\n'
        elif [[ " $* " == *'.NetworkSettings.Networks'*'.IPAddress'* ]]; then
          if [[ " $* " == *'fixture-active-backend'* ]]; then
            printf '172.18.0.2\n'
          else
            printf '172.31.0.12\n'
          fi
        else
          printf 'status=running running=true exit_code=0 restart_count=0\n'
        fi
        ;;
      exec)
        printf '172.31.0.12\n'
        ;;
      logs)
        printf 'candidate startup %s=session-%s %s=%s %s %s\n' \
          "${cookie_key}" "${private_value}" "${pass_key}" "${private_value}" \
          "${fixture_email}" "${private_url}"
        ;;
      *) return 1 ;;
    esac
  }

  deployment_error() {
    printf 'TEST_CLEANUP rc=%s\n' "$1"
    return "$1"
  }

  set +e
  stage_or_abort verify_candidate > "${output}" 2>&1
  rc=$?
  set -e
  [ "${rc}" -eq 1 ] || return 1

  grep -Fq "CANDIDATE_GATE_START=${failed_gate}" "${output}"
  if [[ "${failed_gate}" =~ ^(database|internal_api_dns|candidate_logs)$ ]]; then
    result_pattern="CANDIDATE_GATE_RESULT=${failed_gate} exit_code=17 http_status=NA duration_ms=[0-9]+ attempt=1 result=FAIL"
  else
    result_pattern="CANDIDATE_GATE_RESULT=${failed_gate} exit_code=17 http_status=503 duration_ms=[0-9]+ attempt=3 result=FAIL"
    grep -Fq "CANDIDATE_GATE_HTTP_BODY gate=${failed_gate}" "${output}"
  fi
  grep -Eq "${result_pattern}" "${output}"
  grep -Fq "CANDIDATE_DIAGNOSTIC_START gate=${failed_gate}" "${output}"
  grep -Fq "CANDIDATE_DIAGNOSTIC_HEALTH container=fixture-candidate-backend" "${output}"
  grep -Fq "CANDIDATE_DIAGNOSTIC_HEALTH_HISTORY container=fixture-candidate-frontend" "${output}"
  grep -Fq "CANDIDATE_DIAGNOSTIC_LOG container=fixture-candidate-backend" "${output}"
  grep -Fq 'CANDIDATE_DIAGNOSTIC_INTERNAL_API configured_expected=true alias_resolved=true candidate_ip_present=true points_candidate=true points_active=false' "${output}"
  grep -Fq "CANDIDATE_GATE_FAILED=${failed_gate}" "${output}"
  grep -Fq "CANDIDATE_DIAGNOSTIC_END gate=${failed_gate}" "${output}"

  diagnostic_line="$(grep -n -m1 -F "CANDIDATE_DIAGNOSTIC_END gate=${failed_gate}" "${output}" | cut -d: -f1)"
  cleanup_line="$(grep -n -m1 -F 'TEST_CLEANUP' "${output}" | cut -d: -f1)"
  [ "${diagnostic_line}" -lt "${cleanup_line}" ]
  start_line="$(grep -n -m1 -F "CANDIDATE_GATE_START=${failed_gate}" "${output}" | cut -d: -f1)"
  [ -n "${start_line}" ]

  ! grep -Fq "${private_value}" "${output}"
  ! grep -Fq "${fixture_email}" "${output}"
  ! grep -Fq 'private.example' "${output}"
  ! grep -Fq "${fixture_document}" "${output}"
  ! grep -Fq '172.31.0.12' "${output}"
  ! find "${runtime_dir}" -type f -print -quit | grep -q .
  printf 'ok - observabilidade identifica e sanitiza falha em %s antes do cleanup\n' "${failed_gate}"
)

test_candidate_gate_observability_success() (
  set -euo pipefail
  local iteration="$1"
  local scenario_dir="${temporary}/candidate-gate-success-${iteration}"
  local output="${scenario_dir}/output.log"
  mkdir -p "${scenario_dir}/runtime"
  RUNTIME_DIR="${scenario_dir}/runtime"
  CANDIDATE_BACKEND_PORT=28081
  CANDIDATE_FRONTEND_PORT=23011
  CANDIDATE_GATEWAY_PORT=23001

  candidate_gate_fixture_success() {
    case "${CURRENT_CANDIDATE_GATE}" in
      database|internal_api_dns|candidate_logs) CANDIDATE_GATE_HTTP_STATUS=NA ;;
      *) CANDIDATE_GATE_HTTP_STATUS=200 ;;
    esac
    CANDIDATE_GATE_ATTEMPT=1
  }
  wait_for_json_up() { candidate_gate_fixture_success; }
  expect_http_status() { candidate_gate_fixture_success; }
  verify_database_gate() { candidate_gate_fixture_success; }
  verify_candidate_dns() { candidate_gate_fixture_success; }
  verify_candidate_logs() { candidate_gate_fixture_success; }

  verify_candidate > "${output}" 2>&1
  [ "$(grep -c 'CANDIDATE_GATE_START=' "${output}")" -eq 14 ]
  [ "$(grep -c 'result=OK' "${output}")" -eq 14 ]
  ! grep -Fq 'CANDIDATE_DIAGNOSTIC_START' "${output}"
  ! grep -Fq 'result=FAIL' "${output}"
  ! find "${scenario_dir}/runtime" -type f -print -quit | grep -q .
  printf 'ok - caminho de sucesso observavel %s/5 sem residuos\n' "${iteration}"
)

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

  run_preview_backfill() {
    local mode="$1"
    local phase="$2"
    record_event "PREVIEW_BACKFILL_${phase^^}_${mode}"
    if [ "${SCENARIO}" = backfill-failure ] \
      && [ "${phase}" = delta ] \
      && [ "${mode}" = APPLY ]; then
      record_event PREVIEW_BACKFILL_FAILED
      return 1
    fi
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
      [ "$(event_line PREVIEW_BACKFILL_DELTA_APPLY)" -lt "$(event_line PREVIEW_BACKFILL_DELTA_VALIDATE)" ] || return 1
      [ "$(event_line PREVIEW_BACKFILL_DELTA_VALIDATE)" -lt "$(event_line TRAFFIC_SWITCH)" ] || return 1
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line PUBLIC_SMOKE_1)" ] || return 1
      [ "$(event_line OLD_LONG_REQUESTS_STARTED)" -lt "$(event_line TRAFFIC_SWITCH)" ] || return 1
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line LONG_REQUEST_COMPLETED_SECONDS)" ] || return 1
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line VIDEO_STREAM_COMPLETED)" ] || return 1
      [ "$(event_line OLD_CONNECTIONS_DRAINED)" -lt "$(event_line PREVIEW_BACKFILL_FINAL_APPLY)" ] || return 1
      [ "$(event_line PREVIEW_BACKFILL_FINAL_APPLY)" -lt "$(event_line PREVIEW_BACKFILL_FINAL_VALIDATE)" ] || return 1
      [ "$(event_line PREVIEW_BACKFILL_FINAL_VALIDATE)" -lt "$(event_line PUBLIC_SMOKE_2)" ] || return 1
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

for candidate_gate_name in "${candidate_gate_names[@]}"; do
  test_candidate_gate_observability_failure "${candidate_gate_name}"
done
for candidate_gate_success_iteration in $(seq 1 5); do
  test_candidate_gate_observability_success "${candidate_gate_success_iteration}"
done

run_scenario unhealthy
run_scenario readiness-503
run_scenario backfill-failure
run_scenario healthy
run_scenario nginx-invalid
run_scenario post-switch-failure

printf 'ATOMIC_RELEASE_DEPLOY_TESTS=PASS\n'
