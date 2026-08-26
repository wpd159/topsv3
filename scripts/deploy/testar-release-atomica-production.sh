#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
helper="${root}/scripts/deploy/ativar-release-atomica-production.sh"
fixture="${root}/scripts/deploy/fixture-release-atomica.mjs"
workflow="${root}/.github/workflows/deploy-production.yml"
compose="${root}/deploy/production/docker-compose.yml"
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
        sleep 0.002
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
    record_event DRAIN
    sleep 0.05
  }

  finalize_activation() {
    record_event FINALIZE
    [ "${SMOKE_COUNT}" -ge 2 ] || return 1
    proxy_control stop-old
    record_event OLD_STOPPED
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
      [ "$(event_line TRAFFIC_SWITCH)" -lt "$(event_line PUBLIC_SMOKE_1)" ] || return 1
      [ "$(event_line PUBLIC_SMOKE_2)" -lt "$(event_line OLD_STOPPED)" ] || return 1
      ;;
    post-switch-failure)
      [ "${rc}" -ne 0 ] || return 1
      [ "$(release_header)" = old ] || return 1
      grep -Fq TRAFFIC_ROLLBACK "${EVENT_LOG}" || return 1
      ! grep -Fq OLD_STOPPED "${EVENT_LOG}" || return 1
      ;;
    *)
      [ "${rc}" -ne 0 ] || return 1
      [ "$(release_header)" = old ] || return 1
      ! grep -Fq TRAFFIC_SWITCH "${EVENT_LOG}" || return 1
      ! grep -Fq OLD_STOPPED "${EVENT_LOG}" || return 1
      grep -Fq CANDIDATE_REMOVED "${EVENT_LOG}" || return 1
      ;;
  esac

  if awk -F'|' '$2 == "500" || $2 == "502" || $2 == "504" || $2 == "000" { found = 1 } END { exit found ? 0 : 1 }' "${HARNESS_STATUS_LOG}"; then
    return 1
  fi
  printf 'ok - %s: 200 Home + 40 listagem, zero 500/502/504\n' "${SCENARIO}"
)

run_scenario unhealthy
run_scenario readiness-503
run_scenario healthy
run_scenario nginx-invalid
run_scenario post-switch-failure

printf 'ATOMIC_RELEASE_DEPLOY_TESTS=PASS\n'
