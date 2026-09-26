#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
helper="${script_dir}/proteger-operacao-production.sh"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
fail() { printf 'FALHA_CONTAINER_OPERACAO: %s\n' "$*" >&2; exit 1; }
scenarios=(startup_callback startup_callback_health_failure http_failure final_failure success recovery_late recovery_absent benign_success benign_mixed_error recovery_benign_mixed_error frontend_down gateway_down recovery_new_frontend_down)
if (( $# > 0 )); then
  [[ $# == 2 && "$1" == --scenario ]] || fail 'uso: testar-operacao-containers-production.sh [--scenario NOME]'
  case "$2" in
    http_failure|final_failure|success|recovery_late|recovery_absent|benign_success|benign_mixed_error|recovery_benign_mixed_error|frontend_down|gateway_down|recovery_new_frontend_down|startup_callback|startup_callback_health_failure) scenarios=("$2") ;;
    *) fail 'cenario desconhecido' ;;
  esac
fi
[[ "$(uname -s)" == Linux ]] || fail 'execute em Linux real'
[[ "$EUID" -ne 0 ]] || fail 'execute como usuario nao-root para provar snapshot 0600 de UID nao-zero'
for executable in docker curl bash flock setsid timeout sha256sum python3; do command -v "$executable" >/dev/null || fail "ferramenta ausente: $executable"; done
real_docker="$(command -v docker)"
real_curl="$(command -v curl)"
"$real_docker" compose version >/dev/null
[[ "$("$real_docker" version --format '{{.Server.Os}}')" == linux ]] || fail 'Docker Linux necessario'
for base in node:22.13.1-alpine postgres:17.10-alpine nginx:1.27-alpine; do
  if ! "$real_docker" image inspect "$base" >/dev/null 2>&1; then "$real_docker" pull "$base"; fi
done
operation_owner_id="$(cat /proc/sys/kernel/random/uuid)"
prefix="topsv3-operation-${operation_owner_id:0:12}"
work_base="$(readlink -e "${OPERATION_TEST_WORK_ROOT:-${TMPDIR:-/tmp}}")"
work_dir="$(mktemp -d "${work_base}/tops-operation-containers.XXXXXX")"
resources="${work_dir}/resources"
network="${prefix}-net"
legacy_sha=a60b1e74978017a5bba1577f58804933b347c790
new_base_sha=cccccccccccccccccccccccccccccccccccccccc
previous_sha="$legacy_sha"
candidate_sha=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
fixture_flyway_version="$(bash "${script_dir}/validar-gate-flyway-production.sh" expected "${repo_root}/backend/src/main/resources/db/migration")"
export TEST_FLYWAY_VERSION="$fixture_flyway_version"
previous_key=INDEXNOW-SYNTHETIC-PREVIOUS
candidate_key=INDEXNOW-SYNTHETIC-CANDIDATE
attached=0
source_observer_checked=0
touch "$resources"
record() { printf '%s|%s|%s\n' "$1" "$2" "${3:-$operation_owner_id}" >> "$resources"; }
cleanup() {
  local original_rc=$? cleanup_rc=0 final_rc kind identifier owner actual id
  trap - EXIT
  if [[ "$attached" -eq 1 ]]; then
    "$real_docker" network disconnect "$network" "$OPERATION_TEST_CONTROLLER" >/dev/null 2>&1 || cleanup_rc=1
  fi
  while IFS='|' read -r kind identifier owner; do
    case "$kind" in
      container|image) actual="$("$real_docker" "$kind" inspect --format '{{index .Config.Labels "topsv3.operation.test"}}' "$identifier" 2>/dev/null)" || continue ;;
      volume|network) actual="$("$real_docker" "$kind" inspect --format '{{index .Labels "topsv3.operation.test"}}' "$identifier" 2>/dev/null)" || continue ;;
      *) cleanup_rc=1; continue ;;
    esac
    if [[ "$actual" != "$owner" ]]; then
      echo 'FALHA_CLEANUP: ownership divergente; recurso preservado' >&2
      cleanup_rc=1
      continue
    fi
    id="$("$real_docker" "$kind" inspect --format '{{.Id}}' "$identifier" 2>/dev/null)" || id="$identifier"
    case "$kind" in
      container) "$real_docker" rm -f "$id" >/dev/null ;;
      image) "$real_docker" image rm -f "$id" >/dev/null ;;
      volume) "$real_docker" volume rm "$identifier" >/dev/null ;;
      network) "$real_docker" network rm "$id" >/dev/null ;;
    esac || cleanup_rc=1
  done < <(tac "$resources")
  if [[ "$original_rc" -ne 0 ]]; then
    printf 'RECOVERY_FAILURE_EVIDENCE_BEGIN original_exit=%s\n' "$original_rc" >&2
    if [[ -f "${work_dir}/last-operation.log" ]]; then
      grep '^RECOVERY_WINDOW ' "${work_dir}/last-operation.log" >&2 || true
      tail -n 100 "${work_dir}/last-operation.log" >&2
    fi
    if [[ -n "${TEST_EVENTS:-}" && -f "${TEST_EVENTS}" ]]; then
      grep -E '^(RESTORE_UP|PROBE_TRACE).*' "${TEST_EVENTS}" >&2 || true
    fi
    if [[ -n "${test_root:-}" && -f "${test_root}/operations/active.state" ]]; then
      sed -n -E '/^(phase|result|original_rc|ambiguous|mutated)=/p' "${test_root}/operations/active.state" >&2
    fi
    printf 'RECOVERY_FAILURE_EVIDENCE_END original_exit=%s\n' "$original_rc" >&2
  fi
  [[ "$work_dir" == "${work_base}/tops-operation-containers."* ]] || exit 1
  rm -rf -- "$work_dir" || cleanup_rc=1
  final_rc="$original_rc"
  [[ "$final_rc" -ne 0 ]] || final_rc="$cleanup_rc"
  printf 'CONTAINER_TEST_CLEANUP_DETAIL original_exit=%s cleanup_exit=%s final_exit=%s\n' \
    "$original_rc" "$cleanup_rc" "$final_rc"
  printf 'CONTAINER_TEST_CLEANUP exit=%s owned_resources_only=true\n' "$final_rc"
  exit "$final_rc"
}
trap cleanup EXIT

record network "$network"
"$real_docker" network create --internal --label "topsv3.operation.test=${operation_owner_id}" "$network" >/dev/null
if [[ -n "${OPERATION_TEST_CONTROLLER:-}" ]]; then
  # Optional only for a local Docker-in-Docker-client controller. Ubuntu CI runs
  # this script directly and reaches its private bridge IPs without this branch.
  [[ -n "$("$real_docker" inspect --format '{{index .Config.Labels "topsv3.operation.test"}}' "$OPERATION_TEST_CONTROLLER")" ]] || fail 'controller nao rotulado'
  "$real_docker" network disconnect none "$OPERATION_TEST_CONTROLLER" >/dev/null 2>&1 || true
  "$real_docker" network connect "$network" "$OPERATION_TEST_CONTROLLER"
  attached=1
fi
sentinel="${prefix}-sentinel"
record container "$sentinel" "sentinel-${operation_owner_id}"
sentinel_id="$("$real_docker" create --name "$sentinel" --network none --label "topsv3.operation.test=sentinel-${operation_owner_id}" node:22.13.1-alpine node -e 'process.exit(0)')"
pg_volume="${prefix}-postgres-data"
record volume "$pg_volume"
"$real_docker" volume create --label "topsv3.operation.test=${operation_owner_id}" "$pg_volume" >/dev/null
record container "${prefix}-postgres"
postgres_id="$("$real_docker" run -d --pull never --name "${prefix}-postgres" \
  --network "$network" --label "topsv3.operation.test=${operation_owner_id}" \
  --volume "${pg_volume}:/var/lib/postgresql/data" --env POSTGRES_HOST_AUTH_METHOD=trust \
  --env POSTGRES_USER=postgres --env POSTGRES_DB=postgres \
  --health-cmd='pg_isready -U postgres' --health-interval=1s --health-timeout=1s --health-retries=30 \
  postgres:17.10-alpine)"
deadline=$((SECONDS + 40))
until [[ "$("$real_docker" inspect --format '{{.State.Health.Status}}' "$postgres_id")" == healthy ]]; do
  (( SECONDS < deadline )) || fail 'PostgreSQL sentinela nao iniciou'
  sleep 0.2
done

server_program="$(cat <<'NODE_SERVER'
const http = require('node:http');
const role = process.env.SERVICE;
const prefix = process.env.TEST_PREFIX;
// Preserve the installed Next/Flight string escaping; only synthetic values.
const flightScript = record => '<script>self.__next_f.push([1,' + JSON.stringify(record + '\n') + '])</script>';
const benignDigest = process.env.CONTENT_PROFILE === 'benign' ? flightScript('1:{"digest":"$undefined"}') : '';
const errorDigest = flightScript('2:E{"digest":"12345"}');
let readinessFailures = 1, mode = '', recoveryStarted = 0;
const callbackRequired = process.env.STARTUP_CALLBACK === 'true';
const callbackNonce = require('node:crypto').randomUUID();
const startupTime = Date.now();
let callbackComplete = !callbackRequired;
const server = http.createServer(async (request, response) => {
  const correlation = request.headers['x-request-id'] || 'none';
  const send = (status, body, contentType = 'text/html; charset=utf-8') => {
    response.writeHead(status, {'content-type': contentType}); response.end(body);
  };
  try {
    if (request.url.startsWith('/__test/control/')) {
      mode = request.url.split('/').at(-1);
      if (mode.startsWith('recovery_')) recoveryStarted = Date.now();
      if (mode === 'final_failure') console.log('fixture enabled=true');
      return send(200, 'CONTROLLED');
    }
    if (role === 'backend') {
      if (request.url === '/__test/startup-callback/' + callbackNonce) {
        if (request.headers['x-synthetic-gateway'] !== prefix) return send(403, 'CALLBACK_GATEWAY_REQUIRED');
        console.log('STARTUP_CALLBACK_RECEIVED release=' + process.env.RELEASE_ID);
        return send(200, 'SYNTHETIC_CALLBACK_ACCEPTED');
      }
      if (request.url === '/api/health/liveness') {
        const live = callbackComplete && mode !== 'startup_callback_health_failure';
        return send(live ? 200 : 503, JSON.stringify({status: live ? 'UP' : 'DOWN', app: 'topsdojob-v3-backend'}), 'application/json');
      }
      if (request.url === '/api/health/readiness') {
        const ready = callbackComplete && mode !== 'startup_callback_health_failure' && (mode.startsWith('recovery_')
          ? Date.now() - recoveryStarted >= 32000 : readinessFailures-- <= 0);
        return send(ready ? 200 : 503, JSON.stringify({status: ready ? 'UP' : 'DOWN', app: 'topsdojob-v3-backend'}), 'application/json');
      }
      if (request.url === '/api/public/localidades') {
        console.log(`LOCALIDADES_REQUEST correlation=${correlation}`);
        return send(200, JSON.stringify({estados: [], cidades: [], totalAnuncios: 0}), 'application/json');
      }
      return send(200, JSON.stringify({status: 'UP', app: 'topsdojob-v3-backend'}), 'application/json');
    }
    if (role === 'gateway') {
      if (request.url.startsWith('/__test/startup-callback/')) {
        const result = await fetch(`http://${prefix}-backend:8080${request.url}`, {
          headers: {'X-Synthetic-Gateway': prefix}, signal: AbortSignal.timeout(2000)
        });
        console.log('STARTUP_CALLBACK_FORWARDED release=' + process.env.RELEASE_ID + ' status=' + result.status);
        response.setHeader('x-synthetic-gateway-start', String(startupTime));
        return send(result.status, await result.text());
      }
      if (mode === 'gateway_down' && request.url.startsWith('/health/')) return send(503, JSON.stringify({status: 'DOWN'}));
      const result = await fetch(`http://${prefix}-frontend:8080${request.url}`, {headers: {'X-Request-ID': correlation}, signal: AbortSignal.timeout(2000)});
      return send(result.status, await result.text());
    }
    if (request.url.startsWith('/health/')) {
      if (process.env.RELEASE_ID === 'a60b1e74978017a5bba1577f58804933b347c790') return send(404, 'LEGACY_ENDPOINT_ABSENT');
      const down = mode === 'frontend_down' || mode === 'recovery_new_frontend_down';
      return send(down ? 503 : 200, JSON.stringify({status: down ? 'DOWN' : 'UP', app: 'topsdojob-v3-frontend'}), 'application/json');
    }
    const locality = await fetch(`http://${prefix}-backend:8080/api/public/localidades`, {headers: {'X-Request-ID': correlation}, signal: AbortSignal.timeout(2000)});
    const data = await locality.json();
    if (!locality.ok || !Array.isArray(data.cidades)) return send(503, 'LOCALITY_UNAVAILABLE');
    if (mode === 'recovery_absent' || (mode === 'recovery_late' && Date.now() - recoveryStarted < 183000)) {
      return send(503, 'CONTROLLED_OLD_RELEASE_WARMUP');
    }
    if (mode === 'http_failure') return send(200, '<h1>Application error</h1>');
    const functional = request.url === '/anuncios'
      ? '<h1>Anúncios de acompanhantes</h1><p>Explore perfis publicados</p><p>Nenhum anúncio publicado.</p>'
      : '<h1>Encontre acompanhantes perto de você</h1>';
    const concreteError = mode === 'benign_mixed_error' || mode === 'recovery_benign_mixed_error';
    return send(200, functional + benignDigest + (concreteError ? errorDigest : ''));
  } catch { send(503, 'CONTROLLED_DEPENDENCY_FAILURE'); }
});
if (callbackRequired) {
  // Node is PID 1 in these synthetic containers. Handle Compose's normal stop
  // signal instead of consuming its fallback SIGKILL wait on each replacement.
  process.once('SIGTERM', () => server.close(() => process.exit(0)));
}
server.listen(8080, '0.0.0.0', async () => {
  if (role !== 'backend' || !callbackRequired) return;
  console.log('STARTUP_WAITING_FOR_CALLBACK release=' + process.env.RELEASE_ID);
  // Synthetic startup dependency only: no Efí request, credential or application
  // retry is involved. Readiness remains DOWN until a real HTTP round trip via
  // the fixture gateway succeeds, including its callback response.
  for (let attempt = 0; attempt < 100 && !callbackComplete; attempt++) {
    try {
      const result = await fetch(`http://${prefix}-gateway:8080/__test/startup-callback/${callbackNonce}`, {
        signal: AbortSignal.timeout(2000)
      });
      const body = await result.text();
      // Do not credit a previous gateway generation still running during replacement.
      callbackComplete = result.ok && Number(result.headers.get('x-synthetic-gateway-start')) >= startupTime
        && body === 'SYNTHETIC_CALLBACK_ACCEPTED';
    } catch {}
    if (!callbackComplete) await new Promise(resolve => setTimeout(resolve, 200));
  }
  console.log((callbackComplete ? 'STARTUP_CALLBACK_READY' : 'STARTUP_CALLBACK_EXHAUSTED')
    + ' release=' + process.env.RELEASE_ID + ' elapsed_ms=' + (Date.now() - startupTime));
});
NODE_SERVER
)"
for sha in "$previous_sha" "$candidate_sha" "$new_base_sha"; do
  builder="${prefix}-image-${sha:0:1}"
  record container "$builder"
  builder_id="$("$real_docker" create --name "$builder" --label "topsv3.operation.test=${operation_owner_id}" --entrypoint node node:22.13.1-alpine -e "$server_program")"
  image_id="$("$real_docker" commit --change "ENV IMAGE_RELEASE=${sha}" "$builder_id" "${prefix}-backend:${sha}")"
  record image "$image_id"
  "$real_docker" tag "$image_id" "${prefix}-frontend:${sha}"
  "$real_docker" rm "$builder_id" >/dev/null
done
"$real_docker" tag "${prefix}-backend:${previous_sha}" "${prefix}-gateway:stable"
previous_image="$("$real_docker" image inspect --format '{{.Id}}' "${prefix}-backend:${previous_sha}")"
candidate_image="$("$real_docker" image inspect --format '{{.Id}}' "${prefix}-backend:${candidate_sha}")"
[[ "$previous_image" != "$candidate_image" ]] || fail 'fixtures nao possuem imagens distintas'

mkdir "${work_dir}/bin"
cat > "${work_dir}/bin/docker" <<'DOCKER_BOUNDARY'
#!/usr/bin/env bash
set -euo pipefail
args=()
for argument in "$@"; do args+=("${argument//topsv3-production/$TEST_PREFIX}"); done
joined=" ${args[*]} "
if [[ "${args[0]:-}" == inspect && "$joined" == *'"com.docker.compose.project"'* ]]; then
  actual_project="$("$TEST_REAL_DOCKER" "${args[@]}")"
  [[ "$actual_project" == "$TEST_PREFIX" ]] || { echo 'COMPOSE_PROJECT_GUARD_FAILED' >&2; exit 74; }
  # Preserve the real guard across the controlled namespace translation.
  printf 'topsv3-production\n'
  exit 0
fi
if [[ "${args[0]:-}" == exec && "$joined" == *' psql '* ]]; then
  if [[ "$joined" == *' --set=public_media_prefix=synthetic-public/ '* ]]; then
    sql_input="$(cat)"
    [[ "$sql_input" == *'BEGIN READ ONLY;'* && "$sql_input" == *'PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE'* \
       && "$sql_input" == *'ROLLBACK;' ]] || exit 91
    # Synthetic empty public universe; the real aggregate classifier still runs.
    for scope in PUBLICO_GALERIA_DTO_SELECIONADO PUBLICO_CARD_DTO_SELECIONADO; do
      printf '2026-09-11T00:00:00Z\t%s\tTOTAL\tUNIVERSO\t0\t0\t0\t0\n' "$scope"
      for category in ACEITA_COMPLETO NAO_ACEITA_OU_NAO_COMPROVADO; do
        printf '2026-09-11T00:00:00Z\t%s\tCONTRATO\t%s\t0\t0\t0\t0\n' "$scope" "$category"
      done
    done
    printf 'PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE\n'
    printf 'CONTROLLED_BOUNDARY preview_public_sql_synthetic\n' >> "$TEST_EVENTS"
    exit 0
  fi
  if [[ "$joined" == *' --command '* ]]; then printf '%s|0|1\n' "$TEST_FLYWAY_VERSION"; else cat >/dev/null; cat "$TEST_SNAPSHOT"; fi
  printf 'CONTROLLED_BOUNDARY psql\n' >> "$TEST_EVENTS"
  exit 0
fi
if [[ "${args[0]:-}" == compose && "$joined" == *' flyway '* ]]; then
  printf 'CONTROLLED_BOUNDARY flyway\n' >> "$TEST_EVENTS"
  exit 0
fi
if [[ "${args[0]:-}" == compose && "$joined" == *' build '* ]]; then
  printf 'CONTROLLED_BOUNDARY app_build_prepared_images\n' >> "$TEST_EVENTS"
  exit 0
fi
if [[ "${args[0]:-}" == run && "$joined" == *' nginx:1.27-alpine '* ]]; then
  name="${TEST_PREFIX}-utility-${BASHPID}"
  printf 'container|%s|%s\n' "$name" "$TEST_OWNER_ID" >> "$TEST_RESOURCES"
  args=(run --name "$name" --label "topsv3.operation.test=${TEST_OWNER_ID}" "${args[@]:1}")
fi
if [[ "${args[0]:-}" == compose && "$joined" == *' up '* ]]; then
  for service in backend frontend gateway; do printf 'container|%s-%s|%s\n' "$TEST_PREFIX" "$service" "$TEST_OWNER_ID" >> "$TEST_RESOURCES"; done
  "$TEST_REAL_DOCKER" "${args[@]}"
  # The coordinator starts each service independently. Observe/inject only once
  # the complete trio exists, not after the first single-service invocation.
  [[ "${args[-1]}" == gateway ]] || exit 0
  for service in backend frontend gateway; do
    "$TEST_REAL_DOCKER" inspect "${TEST_PREFIX}-${service}" --format '{{.Name}} {{.Id}} {{.Image}}' >> "$TEST_EVENTS"
  done
  if [[ "$TEST_SCENARIO" == startup_callback* ]]; then
    deadline=$((SECONDS + 30))
    until "$TEST_REAL_DOCKER" logs "${TEST_PREFIX}-backend" 2>&1 | grep -q '^STARTUP_CALLBACK_READY '; do
      if (( SECONDS >= deadline )); then
        for service in backend gateway; do
          printf 'STARTUP_DIAGNOSTIC service=%s\n' "$service" >&2
          "$TEST_REAL_DOCKER" logs "${TEST_PREFIX}-${service}" 2>&1 | grep '^STARTUP_' >&2 || true
        done
        echo 'SYNTHETIC_CALLBACK_STARTUP_FAILED' >&2
        exit 75
      fi
      sleep 0.2
    done
    "$TEST_REAL_DOCKER" logs "${TEST_PREFIX}-gateway" 2>&1 | grep -q '^STARTUP_CALLBACK_FORWARDED .*status=200$' || exit 76
    stage=baseline
    [[ "${TOPSV3_RELEASE_SHA:-}" != bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ]] || stage=activation
    [[ "$stage" != baseline || ! -s "$TEST_ROOT/candidate-runtime" ]] || stage=recovery
    printf 'CALLBACK_TRACE stage=%s real_http=true backend_ready=true gateway_forwarded=true\n' "$stage" >> "$TEST_EVENTS"
  fi
  if [[ "${TOPSV3_RELEASE_SHA:-}" == bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ]]; then
    for service in backend frontend gateway; do
      "$TEST_REAL_DOCKER" inspect "${TEST_PREFIX}-${service}" --format '{{.Id}} {{.Image}} {{json .Config.Env}}' >> "$TEST_ROOT/candidate-runtime"
    done
    if [[ "$TEST_SCENARIO" != success && "$TEST_SCENARIO" != benign_success && "$TEST_SCENARIO" != startup_callback ]]; then
      target=frontend
      [[ "$TEST_SCENARIO" != final_failure ]] || target=backend
      [[ "$TEST_SCENARIO" != startup_callback_health_failure ]] || target=backend
      [[ "$TEST_SCENARIO" != gateway_down ]] || target=gateway
      failure_mode="$TEST_SCENARIO"
      [[ "$TEST_SCENARIO" != recovery_* ]] || failure_mode=http_failure
      [[ "$TEST_SCENARIO" != recovery_benign_mixed_error ]] || failure_mode=benign_mixed_error
      "$TEST_REAL_DOCKER" exec "${TEST_PREFIX}-${target}" node -e '
        (async () => { for (let i=0;i<30;i++) { try {
          const r=await fetch("http://127.0.0.1:8080/__test/control/"+process.argv[1]); if(r.ok)return;
        } catch {} await new Promise(r=>setTimeout(r,100)); } process.exit(70); })();' "$failure_mode"
    fi
  elif [[ -s "$TEST_ROOT/candidate-runtime" ]]; then
    printf 'RESTORE_UP time_ms=%s\n' "$(date +%s%3N)" >> "$TEST_EVENTS"
    touch "$TEST_ROOT/recovery-started"
    if [[ "$TEST_SCENARIO" == recovery_* ]]; then
      for target in backend frontend; do
        "$TEST_REAL_DOCKER" exec "${TEST_PREFIX}-${target}" node -e '
          (async () => { for (let i=0;i<30;i++) { try {
            const r=await fetch("http://127.0.0.1:8080/__test/control/"+process.argv[1]); if(r.ok)return;
          } catch {} await new Promise(r=>setTimeout(r,100)); } process.exit(70); })();' "$TEST_SCENARIO"
      done
    fi
  fi
  exit 0
fi
exec "$TEST_REAL_DOCKER" "${args[@]}"
DOCKER_BOUNDARY
cat > "${work_dir}/bin/curl" <<'CURL_BOUNDARY'
#!/usr/bin/env bash
set -euo pipefail
args=()
kind=unknown
for argument in "$@"; do
  case "$argument" in
    http://127.0.0.1:28080/*) target=backend; suffix="${argument#http://127.0.0.1:28080}" ;;
    http://127.0.0.1:23010/*) target=frontend; suffix="${argument#http://127.0.0.1:23010}" ;;
    http://127.0.0.1:23000/*) target=gateway; suffix="${argument#http://127.0.0.1:23000}" ;;
    http:*|https:*) echo 'URL fora do ensaio' >&2; exit 71 ;;
    *) args+=("$argument"); continue ;;
  esac
  case "$suffix" in /api/health/readiness) kind=readiness ;; /) kind=home ;; /anuncios) kind=catalog ;; esac
  ip="$("$TEST_REAL_DOCKER" inspect "${TEST_PREFIX}-${target}" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')"
  [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 72
  args+=("http://${ip}:8080${suffix}")
done
# Real wall-clock traces and an exclusive marker prove requests do not overlap.
# Only synthetic URLs are accepted above; no response body or credential is logged.
stage=baseline
[[ ! -s "$TEST_ROOT/candidate-runtime" ]] || stage=activation
[[ ! -f "$TEST_ROOT/recovery-started" ]] || stage=recovery
mkdir "$TEST_ROOT/probe-active" || { echo 'PROBE_OVERLAP' >> "$TEST_EVENTS"; exit 73; }
trap 'rmdir "$TEST_ROOT/probe-active"' EXIT
printf 'PROBE_TRACE event=start stage=%s kind=%s time_ms=%s\n' "$stage" "$kind" "$(date +%s%3N)" >> "$TEST_EVENTS"
rc=0
"$TEST_REAL_CURL" "${args[@]}" || rc=$?
# Observe exact fixture tokens in the actual HTTP body, without classifying it
# or copying the helper predicate. The helper still decides acceptance later.
body_file=
for ((i=0; i<${#args[@]}; i++)); do
  [[ "${args[i]}" != --output ]] || body_file="${args[i+1]:-}"
done
if [[ ( "$kind" == home || "$kind" == catalog ) && -r "$body_file" ]]; then
  benign=0 concrete_error=0
  if grep -Fq '\"digest\":\"$undefined\"' "$body_file"; then benign=1; fi
  if grep -Fq '2:E{\"digest\":\"12345\"}' "$body_file"; then concrete_error=1; fi
  printf 'CONTENT_TRACE stage=%s kind=%s benign=%s concrete_error=%s\n' \
    "$stage" "$kind" "$benign" "$concrete_error" >> "$TEST_EVENTS"
fi
printf 'PROBE_TRACE event=end stage=%s kind=%s time_ms=%s rc=%s\n' "$stage" "$kind" "$(date +%s%3N)" "$rc" >> "$TEST_EVENTS"
exit "$rc"
CURL_BOUNDARY
chmod +x "${work_dir}/bin/docker" "${work_dir}/bin/curl"
export TEST_REAL_DOCKER="$real_docker" TEST_REAL_CURL="$real_curl" TEST_PREFIX="$prefix" TEST_OWNER_ID="$operation_owner_id" TEST_RESOURCES="$resources"
export PATH="${work_dir}/bin:${PATH}"

application_identity() {
  {
    for service in backend frontend gateway; do
      printf 'service=%s\n' "$service"
      "$real_docker" inspect "${prefix}-${service}" --format '{{.Image}}'
      "$real_docker" inspect "${prefix}-${service}" --format '{{range .Config.Env}}{{println .}}{{end}}' | LC_ALL=C sort
    done
    sha256sum "${secrets}/production.env"
    readlink -e "${test_root}/current"
  } | sha256sum | cut -d ' ' -f 1
}

read_timeout_recovery_window() {
  local log="$1" duration_ms deadline_ms
  [[ "$(grep -c '^RECOVERY_WINDOW event=start ' "$log")" -eq 1 ]] || fail 'helper nao registrou exatamente um inicio de janela'
  [[ "$(grep -c '^RECOVERY_WINDOW event=end ' "$log")" -eq 1 ]] || fail 'helper nao registrou exatamente um fim de janela'
  recovery_started_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=start time_ms=([0-9]+) .*$/\1/p' "$log")"
  recovery_started_monotonic_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=start .* monotonic_ms=([0-9]+) .*$/\1/p' "$log")"
  deadline_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=start .* deadline_monotonic_ms=([0-9]+) .*$/\1/p' "$log")"
  duration_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=start .* duration_ms=([0-9]+)$/\1/p' "$log")"
  recovery_finished_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=end time_ms=([0-9]+) .*$/\1/p' "$log")"
  recovery_finished_monotonic_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=end .* monotonic_ms=([0-9]+) .*$/\1/p' "$log")"
  elapsed_recovery_ms="$(sed -n -E 's/^RECOVERY_WINDOW event=end .* elapsed_ms=([0-9]+) .*$/\1/p' "$log")"
  recovery_result="$(sed -n -E 's/^RECOVERY_WINDOW event=end .* result=([^ ]+) .*$/\1/p' "$log")"
  [[ "$recovery_started_ms" =~ ^[0-9]+$ && "$recovery_started_monotonic_ms" =~ ^[0-9]+$ \
    && "$deadline_ms" =~ ^[0-9]+$ && "$duration_ms" =~ ^[0-9]+$ \
    && "$recovery_finished_ms" =~ ^[0-9]+$ && "$recovery_finished_monotonic_ms" =~ ^[0-9]+$ \
    && "$elapsed_recovery_ms" =~ ^[0-9]+$ ]] || fail 'marcadores da janela real invalidos'
  [[ "$duration_ms" -eq 300000 && "$deadline_ms" -eq $((recovery_started_monotonic_ms + duration_ms)) ]] || fail 'deadline monotonico divergente'
  [[ "$elapsed_recovery_ms" -eq $((recovery_finished_monotonic_ms - recovery_started_monotonic_ms)) ]] || fail 'elapsed monotonico divergente'
  [[ "$recovery_result" == timeout ]] || fail "janela negativa terminou como ${recovery_result}"
  printf 'RECOVERY_ASSERTION source=helper_monotonic elapsed_ms=%s min_ms=300000 max_ms=325000 result=%s\n' \
    "$elapsed_recovery_ms" "$recovery_result"
  grep '^RECOVERY_WINDOW ' "$log"
  (( elapsed_recovery_ms >= 300000 && elapsed_recovery_ms <= 325000 )) || fail 'janela negativa nao respeitou prazo real de 300s'
}

# Reuse the existing database gate's synthetic fixture builder. The actual
# database/Flyway validators still execute; no application schema is recreated.
source <(sed -n '/^write_snapshot() {$/,/^}$/p' "${script_dir}/testar-gate-banco-production.sh")

for scenario in "${scenarios[@]}"; do
  previous_sha="$legacy_sha"
  [[ "$scenario" != recovery_new_frontend_down ]] || previous_sha="$new_base_sha"
  [[ "$scenario" != startup_callback* ]] || previous_sha="$new_base_sha"
  previous_image="$("$real_docker" image inspect --format '{{.Id}}' "${prefix}-backend:${previous_sha}")"
  "$real_docker" tag "$previous_image" "${prefix}-gateway:stable"
  case_dir="${work_dir}/${scenario}"
  test_root="${case_dir}/production"
  secrets="${case_dir}/secrets"
  content_profile=plain
  [[ "$scenario" != *benign* ]] || content_profile=benign
  startup_callback=false
  [[ "$scenario" != startup_callback* ]] || startup_callback=true
  mkdir -p "$secrets" "${test_root}/releases"
  # Public synthetic IndexNow value only. The directory is writable by the
  # restricted root utility on any runner GID; the private snapshot stays 0600.
  chmod 0777 "$secrets"
  printf 'INDEXNOW_KEY=%s\n' "$previous_key" > "${secrets}/production.env"
  chmod 0644 "${secrets}/production.env"
  for file in application-production.yml nginx-production-local.conf efi-webhook-allowlist.conf; do printf 'SYNTHETIC_CONFIGURATION\n' > "${secrets}/${file}"; done
  for sha in "$previous_sha" "$candidate_sha"; do
    release="${test_root}/releases/${sha}"
    mkdir -p "${release}/deploy/production" "${release}/scripts/deploy" "${release}/backend/src/main/resources/db/migration"
    printf '%s\n' "$sha" > "${release}/.release-sha"
    cp "$helper" "${script_dir}/validar-gate-banco-production.sh" "${script_dir}/validar-gate-flyway-production.sh" \
      "${script_dir}/capturar-snapshot-gate-banco-production.sql" \
      "${script_dir}/coordenar-transicao-previews-production.sh" \
      "${script_dir}/validar-transicao-previews-runtime.py" \
      "${script_dir}/validar-previews-publicos-production.sql" "${release}/scripts/deploy/"
    # Named boundary: the Node image cannot run the Java regularizer. Produce
    # synthetic VALIDATE journal data, but use a real terminal owned container
    # and the unchanged runtime terminal/public/cleanup predicates.
    cat > "${release}/scripts/deploy/executar-backfill-previews-production.sh" <<'SYNTHETIC_VALIDATE_BOUNDARY'
preview_backfill_main() {
  [[ "$#" -eq 9 && "$1" == VALIDATE && "$2" == final && "$3" == "$OP_CANDIDATE" \
     && "$4" == "$OP_ROOT/releases/$OP_CANDIDATE" && "$5" == "$OP_SECRETS/production.env" \
     && "$6" == topsv3-production && "$7" == topsv3-production && "$8" == topsv3-production-net \
     && "$9" == "$OP_DIR/previews/validate" ]] || return 91
  local report="$9" stage job_user
  job_user="$(id -u):$(id -g)"
  JOB_CONTAINER="$TEST_PREFIX-preview-validate-$OP_ID"
  printf 'CONTROLLED_BOUNDARY preview_validate_synthetic\n' >> "$TEST_EVENTS"
  printf 'container|%s|%s\n' "$JOB_CONTAINER" "$TEST_OWNER_ID" >> "$TEST_RESOURCES"
  printf '{"image":"%s","user":"%s"}\n' "$TEST_CANDIDATE_IMAGE" "$job_user" > "$report/image-pin.json"
  for stage in STARTED PLAN_READY VALIDATION_PASSED SUCCEEDED; do
    printf '{"stage":"%s","mode":"VALIDATE","commit":"NOT_STARTED"}\n' "$stage"
  done > "$report/final-validate.tsv.state.jsonl"
  op_run mutating docker create --pull never --network none --restart no \
    --user "$job_user" \
    --name "$JOB_CONTAINER" --label "topsv3.operation.test=$TEST_OWNER_ID" \
    --label "topsv3.preview.operation=$OP_ID" \
    --env R2_PUBLIC_MEDIA_PREFIX=synthetic-public/ --env R2_PUBLIC_BASE_URL=https://synthetic.invalid \
    --entrypoint node "$TEST_CANDIDATE_IMAGE" -e 'process.exit(0)' -- \
    --app.restricted-media-preview-reconciliation.mode=VALIDATE \
    --app.restricted-media-preview-reconciliation.apply-confirmed=false >/dev/null || return $?
  op_run mutating docker start --attach "$JOB_CONTAINER" || return $?
  op_run readonly python3 "$4/scripts/deploy/validar-transicao-previews-runtime.py" \
    outcome "$report" "$JOB_CONTAINER" "$OP_ID" VALIDATE final-validate.tsv.state.jsonl || return $?
  op_run readonly python3 "$4/scripts/deploy/validar-transicao-previews-runtime.py" \
    terminal "$report" "$JOB_CONTAINER" "$OP_ID" VALIDATE final-validate.tsv.state.jsonl
}
SYNTHETIC_VALIDATE_BOUNDARY
    if [[ "$sha" != "$legacy_sha" ]]; then
      mkdir -p "${release}/frontend/src/app/health/liveness" "${release}/frontend/src/app/health/readiness"
      cp "${repo_root}/frontend/src/app/health/liveness/route.ts" "${release}/frontend/src/app/health/liveness/route.ts"
      cp "${repo_root}/frontend/src/app/health/readiness/route.ts" "${release}/frontend/src/app/health/readiness/route.ts"
    fi
    cp "${repo_root}/backend/src/main/resources/db/migration/"*.sql "${release}/backend/src/main/resources/db/migration/"
    {
      printf 'name: %s\nservices:\n' "$prefix"
      for service in backend frontend gateway; do
        reference="${prefix}-${service}:${sha}"
        [[ "$service" != gateway ]] || reference="${prefix}-gateway:stable"
        cat <<COMPOSE_SERVICE
  ${service}:
    image: ${reference}
    container_name: ${prefix}-${service}
    labels:
      topsv3.operation.test: ${operation_owner_id}
    environment:
      SERVICE: ${service}
      TEST_PREFIX: ${prefix}
      CONTENT_PROFILE: ${content_profile}
      STARTUP_CALLBACK: "${startup_callback}"
      RELEASE_ID: ${sha}
      CONFIG_MARKER: configuration-${sha}
      INDEXNOW_KEY: \${INDEXNOW_KEY:?synthetic key required}
      NEXT_PUBLIC_SITE_URL: https://topsdojob.com
      NEXT_PUBLIC_ANALYTICS_ENABLED: "true"
      SEARCH_INDEXING_MODE: public
      SPRING_PROFILES_ACTIVE: production
      APP_ENV: producao
    networks: [operation_test]
COMPOSE_SERVICE
        if [[ "$startup_callback" == true ]]; then
          # Copy the actual application dependency edges, including those in the
          # restored release; the fixture does not silently omit Compose waits.
          if [[ "$service" != backend ]]; then
            dependency_block="$(awk -v service="$service" '
              { sub(/\r$/, "") }
              $0 == "  " service ":" { inside=1; next }
              inside && /^  [^ ]/ { exit }
              inside && /^    depends_on:/ { copying=1 }
              copying && /^    [^ ]/ && !/^    depends_on:/ { exit }
              copying { print }
            ' "${repo_root}/deploy/production/docker-compose.yml")"
            [[ "$dependency_block" == *'condition: service_healthy'* ]] || fail "depends_on real ausente: ${service}"
            printf '%s\n' "$dependency_block"
          fi
          health_path=/health/liveness
          [[ "$service" != backend ]] || health_path=/api/health/liveness
          cat <<COMPOSE_HEALTH
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:8080${health_path}').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]
      interval: 1s
      timeout: 1s
      retries: 5
COMPOSE_HEALTH
        fi
      done
      printf 'networks:\n  operation_test:\n    external: true\n    name: %s\n' "$network"
    } > "${release}/deploy/production/docker-compose.yml"
  done
  ln -s "releases/${previous_sha}" "${test_root}/current"
  write_snapshot "${case_dir}/database.snapshot" 100 100 70 30 35000 "$fixture_flyway_version" UP 1000 500
  sed -i '/^META|health|/d' "${case_dir}/database.snapshot"
  export TEST_ROOT="$test_root" TEST_SCENARIO="$scenario" TEST_SNAPSHOT="${case_dir}/database.snapshot" TEST_EVENTS="${case_dir}/events"
  export TEST_CANDIDATE_IMAGE="$candidate_image"
  touch "$TEST_EVENTS"
  if [[ "$scenario" == startup_callback ]]; then
    for service in backend frontend gateway; do
      if "$real_docker" inspect "${prefix}-${service}" >/dev/null 2>&1; then
        [[ "$("$real_docker" inspect "${prefix}-${service}" --format '{{index .Config.Labels "topsv3.operation.test"}}')" == "$operation_owner_id" ]] || fail 'ownership divergente antes da regressao'
        "$real_docker" rm -f "${prefix}-${service}" >/dev/null
      fi
      record container "${prefix}-${service}"
    done
    # Execute the former multi-service command unchanged, against fresh
    # containers. --no-deps does not remove healthy edges among selected services.
    if timeout --kill-after=5s 30s "$real_docker" compose --env-file "${secrets}/production.env" \
      -f "${test_root}/releases/${previous_sha}/deploy/production/docker-compose.yml" -p "$prefix" \
      up -d --no-deps --force-recreate --no-build --pull never backend frontend gateway > "${case_dir}/old-startup.log" 2>&1; then
      fail 'sequencia antiga nao reproduziu o bloqueio'
    else
      old_rc=$?
    fi
    [[ "$old_rc" -eq 1 ]] || fail "regressao terminou sem falha Compose de health: rc=${old_rc}"
    grep -Eq 'container .* is unhealthy' "${case_dir}/old-startup.log" || fail 'erro antigo nao comprovou dependencia unhealthy'
    [[ "$("$real_docker" inspect "${prefix}-backend" --format '{{.State.Health.Status}}')" == unhealthy ]] || fail 'backend antigo nao ficou unhealthy'
    "$real_docker" logs "${prefix}-backend" 2>&1 | grep -q '^STARTUP_WAITING_FOR_CALLBACK ' || fail 'backend nao aguardou callback'
    if "$real_docker" logs "${prefix}-backend" 2>&1 | grep -q '^STARTUP_CALLBACK_READY '; then fail 'callback antigo foi disponibilizado'; fi
    for service in frontend gateway; do
      [[ "$("$real_docker" inspect "${prefix}-${service}" --format '{{.State.Running}}')" == false ]] || fail "${service} iniciou apesar do bloqueio antigo"
    done
    printf 'STARTUP_OLD_SEQUENCE_LOG_BEGIN\n'
    cat "${case_dir}/old-startup.log"
    printf 'STARTUP_OLD_SEQUENCE_LOG_END\n'
    printf 'STARTUP_OLD_SEQUENCE result=BLOCKED real_compose=true backend=unhealthy frontend_running=false gateway_running=false\n'
  fi
  for service in backend frontend gateway; do
    TOPSV3_RELEASE_SHA="$previous_sha" docker compose --env-file "${secrets}/production.env" \
      -f "${test_root}/releases/${previous_sha}/deploy/production/docker-compose.yml" -p topsv3-production \
      up -d --no-deps --force-recreate --no-build --pull never "$service" >/dev/null
  done
  before_ids="$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')"
  # Extract the complete activation body, changing only the private fixture root.
  section=0 block=none
  while IFS= read -r line; do
    line="${line%$'\r'}"
    [[ "$line" != *'- name: Build and activate production release'* ]] || section=1
    [[ "$section" -eq 1 ]] || continue
    if [[ "$line" == *"cat <<'REMOTE_HEAD'"* ]]; then block=head; continue; fi
    if [[ "$block" == head && "${line// /}" == REMOTE_HEAD ]]; then printf '%s\n' "$candidate_key"; block=none; continue; fi
    if [[ "$line" == *"cat <<'REMOTE'"* ]]; then block=body; continue; fi
    if [[ "$block" == body && "${line// /}" == REMOTE ]]; then break; fi
    [[ "$block" != none ]] || continue
    line="${line#          }"
    line="${line//\/opt\/topsv3\/production/$test_root}"
    line="${line//\/opt\/topsv3\/secrets/$secrets}"
    printf '%s\n' "$line"
  done < "$workflow" > "${case_dir}/activation.sh"
  bash -n "${case_dir}/activation.sh"
  # Execute the complete workflow. Only the named backfill and SQL boundaries
  # below are synthetic; no workflow line or public gate is projected away.
  if [[ "$source_observer_checked" -eq 0 ]]; then
    # Retain the real Node/source observer regression independently of deployment.
    # Normalize only the explicitly verified fixture Name, never its runtime.
    "$real_docker" inspect "${prefix}-backend" > "${case_dir}/node-runtime.json"
    python3 - "$script_dir" "${case_dir}/node-runtime.json" "$prefix" <<'NODE_SOURCE_REJECTION'
import importlib.util
import json
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location("runtime", Path(sys.argv[1]) / "validar-transicao-previews-runtime.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)
source, = json.loads(Path(sys.argv[2]).read_text())
assert source["Name"] == "/" + sys.argv[3] + "-backend"
source["Name"] = "/topsv3-production-backend"
try:
    runtime.validate_source(source)
except RuntimeError as error:
    assert str(error) == "source_entrypoint_unproven", str(error)
else:
    raise AssertionError("real Node fixture accepted as a Spring source")
print("PREVIEW_NODE_SOURCE_REJECTION=PASS real_inspect=true name_only_normalized=true")
NODE_SOURCE_REJECTION
    source_observer_checked=1
  fi
  started=$SECONDS
  case_timeout=220
  [[ "$scenario" != recovery_* ]] || case_timeout=390
  if timeout --kill-after=5s "${case_timeout}s" bash "${case_dir}/activation.sh" "$candidate_sha" > "${work_dir}/last-operation.log" 2>&1; then rc=0; else rc=$?; fi
  operation_finished_ms="$(date +%s%3N)"
  printf 'TEST_PHASE event=operation_end scenario=%s time_ms=%s rc=%s\n' "$scenario" "$operation_finished_ms" "$rc"
  expected=1
  [[ "$scenario" != success && "$scenario" != benign_success && "$scenario" != startup_callback ]] || expected=0
  [[ "$rc" -eq "$expected" ]] || fail "${scenario}: exit=${rc}, esperado=${expected}"
  [[ "$(grep -c '^CONTROLLED_BOUNDARY preview_validate_synthetic$' "$TEST_EVENTS")" -eq 1 ]] || fail 'VALIDATE sintetico nao foi executado exatamente uma vez'
  [[ "$(grep -c '^CONTROLLED_BOUNDARY preview_public_sql_synthetic$' "$TEST_EVENTS")" -eq 1 ]] || fail 'gate publico real nao consultou a fronteira SQL exatamente uma vez'
  grep -Fxq 'PREVIEW_TRANSITION_READY=PASS readonly=true' "${work_dir}/last-operation.log" || fail 'gate readonly nao concluiu antes da ativacao'
  [[ -s "${test_root}/candidate-runtime" ]] || fail 'candidata nunca alterou os tres servicos'
  [[ "$(wc -l < "${test_root}/candidate-runtime")" -eq 3 ]] || fail 'nao observou troca de tres servicos'
  grep -Fq "$candidate_image" "${test_root}/candidate-runtime" || fail 'runtime nunca usou imagem candidata'
  grep -q "configuration-${candidate_sha}" "${test_root}/candidate-runtime" || fail 'runtime nunca usou configuracao candidata'
  grep -q 'status=503' "${work_dir}/last-operation.log" || fail 'readiness negativa nao foi exercitada'
  grep -q 'status=200' "${work_dir}/last-operation.log" || fail 'readiness/HTTP nao recuperaram'
  backend_logs="$("$real_docker" logs "${prefix}-backend" 2>&1)" || fail 'nao foi possivel coletar logs do backend sintetico'
  if [[ "$scenario" == recovery_new_frontend_down ]]; then
    # The restored main profile fails before heavy home/catalog probes.
    # Its new backend must not receive localidades through the unavailable UI.
    if grep -q 'LOCALIDADES_REQUEST' <<< "$backend_logs"; then
      fail 'frontend health DOWN permitiu consulta pesada de localidades na recuperacao'
    fi
  else
    grep -q 'LOCALIDADES_REQUEST' <<< "$backend_logs" || fail 'frontend nao consultou backend/localidades'
  fi
  operation_id="$(sed -n 's/^id=//p' "${test_root}/operations/active.state")"
  preview_remaining="$("$real_docker" container ls --all --quiet --filter "label=topsv3.preview.operation=${operation_id}")" || fail 'ausencia do job VALIDATE nao comprovada'
  [[ -z "$preview_remaining" ]] || fail 'job VALIDATE proprio permaneceu depois do gate'
  snapshot="${test_root}/operations/${operation_id}"
  [[ "$(stat -c '%u %a' "${snapshot}/env.copy")" == "${EUID} 600" ]] || fail 'snapshot nao pertence ao UID nao-zero com modo0600'
  if [[ "$scenario" == *benign* ]]; then
    for kind in home catalog; do
      grep -Fxq "CONTENT_TRACE stage=baseline kind=${kind} benign=1 concrete_error=0" "$TEST_EVENTS" || fail "${scenario}: base sem fixture benigna real em ${kind}"
    done
    if [[ "$scenario" == benign_success ]]; then
      for kind in home catalog; do
        grep -Fxq "CONTENT_TRACE stage=activation kind=${kind} benign=1 concrete_error=0" "$TEST_EVENTS" || fail "candidata saudavel sem fixture benigna real em ${kind}"
      done
    else
      grep -Fxq 'CONTENT_TRACE stage=activation kind=home benign=1 concrete_error=1' "$TEST_EVENTS" || fail 'candidata nao serviu benigno e erro concreto no mesmo corpo real'
    fi
  fi
  if [[ "$scenario" == success || "$scenario" == benign_success || "$scenario" == startup_callback ]]; then
    grep -qx result=COMPLETED "${test_root}/operations/active.state" || fail 'sucesso sem conclusao'
    [[ "$(readlink -e "${test_root}/current")" == "${test_root}/releases/${candidate_sha}" ]] || fail 'candidata nao promovida'
    printf 'ACTIVATION_VERIFIED scenario=%s result=COMPLETED candidate=%s\n' "$scenario" "$candidate_sha"
    docker_cmd_before="$(grep -c '^CONTROLLED_BOUNDARY app_build_prepared_images$' "$TEST_EVENTS")"
    if [[ "$scenario" == success ]]; then
      "$real_docker" exec "${prefix}-frontend" node -e 'fetch("http://127.0.0.1:8080/__test/control/http_failure").then(r=>{if(!r.ok)process.exit(1)})'
      if bash "$helper" reconcile "$test_root" "$operation_id" "$candidate_sha" --confirm-daemon-quiescent >> "${work_dir}/last-operation.log" 2>&1; then fail 'reconcile aprovou falha posterior'; fi
      grep -qx result=INCOMPLETE "${test_root}/operations/active.state" || fail 'falha posterior nao permaneceu INCOMPLETE'
      grep -qx original_rc=0 "${test_root}/operations/active.state" || fail 'falha posterior sobrescreveu sucesso original'
      grep -qx failure_rc=1 "${snapshot}/post-workflow.failure" || fail 'falha posterior nao registrada separadamente'
      "$real_docker" exec "${prefix}-frontend" node -e 'fetch("http://127.0.0.1:8080/__test/control/recovered").then(r=>{if(!r.ok)process.exit(1)})'
      echo 'PASS: reconcile_falha_posterior_preserva_workflow_original_zero'
    fi
    bash "$helper" rollback "$test_root" "$operation_id" --confirm-daemon-quiescent >> "${work_dir}/last-operation.log" 2>&1
    [[ "$(grep -c '^CONTROLLED_BOUNDARY app_build_prepared_images$' "$TEST_EVENTS")" == "$docker_cmd_before" ]] || fail 'rollback reconstruiu imagens'
  fi
  expected_result=ROLLED_BACK
  [[ "$scenario" != recovery_absent && "$scenario" != recovery_benign_mixed_error && "$scenario" != recovery_new_frontend_down ]] || expected_result=INCOMPLETE
  grep -qx "result=${expected_result}" "${test_root}/operations/active.state" || fail "${scenario}: resultado de recuperacao divergente"
  [[ "$(readlink -e "${test_root}/current")" == "${test_root}/releases/${previous_sha}" ]] || fail 'release anterior nao restaurada'
  [[ "$(cat "${secrets}/production.env")" == "INDEXNOW_KEY=${previous_key}" ]] || fail 'env anterior nao restaurado'
  [[ "$(stat -c '%u %a' "${secrets}/production.env")" == "${EUID} 644" ]] || fail 'owner/mode anterior nao restaurado'
  for service in backend frontend gateway; do
    [[ "$("$real_docker" inspect "${prefix}-${service}" --format '{{.Image}}')" == "$previous_image" ]] || fail "imagem anterior divergente: $service"
    "$real_docker" inspect "${prefix}-${service}" --format '{{json .Config.Env}}' | grep -q "configuration-${previous_sha}" || fail "configuracao anterior divergente: $service"
  done
  if [[ "$scenario" == startup_callback* ]]; then
    grep -qx "${previous_sha}|main-v1" "${snapshot}/previous.health-profile" || fail 'callback recuperado sem perfil main-v1'
    for stage in baseline activation recovery; do
      [[ "$(grep -c "^CALLBACK_TRACE stage=${stage} real_http=true backend_ready=true gateway_forwarded=true$" "$TEST_EVENTS")" -eq 1 ]] || fail "callback nao comprovado uma vez em ${stage}"
    done
    [[ "$(grep -c '^RESTORE_UP ' "$TEST_EVENTS")" -eq 1 ]] || fail 'callback repetiu restauracao fisica'
    if [[ "$scenario" == startup_callback_health_failure ]]; then
      grep -qx original_rc=1 "${test_root}/operations/active.state" || fail 'falha real de saude foi perdida'
      ! grep -qx result=COMPLETED "${test_root}/operations/active.state" || fail 'falha real de saude aprovada'
      printf 'STARTUP_HEALTH_FAILURE candidate_rejected=true callback_succeeded=true recovery=ROLLED_BACK\n'
    fi
    printf 'STARTUP_CALLBACK_RESULT scenario=%s activation_rc=%s recovery=%s previous_profile=main-v1\n' "$scenario" "$rc" "$expected_result"
  fi
  after_ids="$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')"
  [[ "$before_ids" != "$after_ids" ]] || fail 'nao houve recriacao real de containers'
  [[ "$("$real_docker" inspect "${prefix}-postgres" --format '{{.Id}}')" == "$postgres_id" ]] || fail 'PostgreSQL foi trocado'
  [[ "$("$real_docker" inspect "$sentinel" --format '{{.Id}}')" == "$sentinel_id" ]] || fail 'sentinela alheia alterada'
  if [[ "$scenario" == *benign* ]]; then
    [[ "$(grep -c '^RESTORE_UP ' "$TEST_EVENTS")" -eq 1 ]] || fail 'cenario benigno recriou a base mais de uma vez'
    if [[ "$scenario" == recovery_benign_mixed_error ]]; then
      grep -Fxq 'CONTENT_TRACE stage=recovery kind=home benign=1 concrete_error=1' "$TEST_EVENTS" || fail 'base restaurada nao serviu a mistura real de erro'
      ! grep -Eq '^result=(ROLLED_BACK|RECONCILED)$' "${test_root}/operations/active.state" || fail 'erro real na base foi aprovado'
    else
      for kind in home catalog; do
        grep -Fxq "CONTENT_TRACE stage=recovery kind=${kind} benign=1 concrete_error=0" "$TEST_EVENTS" || fail "base restaurada sem fixture benigna real em ${kind}"
      done
    fi
    [[ "$scenario" == benign_success ]] || grep -qx original_rc=1 "${test_root}/operations/active.state" || fail 'erro original da candidata perdido'
    printf 'CONTENT_OPERATION_RESULT scenario=%s activation_rc=%s result=%s original_rc=%s restore_count=1\n' \
      "$scenario" "$rc" "$expected_result" "$(sed -n 's/^original_rc=//p' "${test_root}/operations/active.state")"
  fi
  if [[ "$scenario" == recovery_* && "$scenario" != recovery_new_frontend_down ]]; then
    [[ "$(grep -c '^RESTORE_UP ' "$TEST_EVENTS")" -eq 1 ]] || fail 'recuperacao recriou servicos mais de uma vez'
    grep -qx original_rc=1 "${test_root}/operations/active.state" || fail 'recuperacao perdeu erro original'
    ! grep -q '^PROBE_OVERLAP$' "$TEST_EVENTS" || fail 'sondas sobrepostas'
    # The real helper waits 15s AFTER a failed batch. Readiness is the first
    # request of each batch; every next start must follow the prior batch end.
    awk '
      /^RESTORE_UP / {split($2,a,"="); restore=a[2]}
      /^PROBE_TRACE / && /stage=recovery/ {
        delete f; for(i=2;i<=NF;i++){split($i,a,"=");f[a[1]]=a[2]}
        if(f["event"]=="start") {
          if(active) exit 1;
          if(f["kind"]=="readiness") {
            if(batches && f["time_ms"]-last_end<14500) exit 2;
            if(!batches) first=f["time_ms"];
            batches++;
          }
          if(f["kind"]=="home") {if(!homes) first_home=f["time_ms"]; homes++}
          if(f["kind"]=="catalog") catalogs++;
          active=1;
        } else {if(!active)exit 3;active=0;last_end=f["time_ms"]}
      }
      END {
        if(active || batches<10 || batches>21 || homes<5 || first_home-restore<32000) exit 4;
        if(scenario=="recovery_late" && (catalogs!=1 || last_end-restore<183000 || last_end-first>305000))exit 5;
        if((scenario=="recovery_absent" || scenario=="recovery_benign_mixed_error") && (catalogs || last_end-first>305000))exit 6;
        printf "RECOVERY_TIMING scenario=%s clock=real batches=%s homes=%s catalogs=%s first_home_after_restore_ms=%s last_probe_after_restore_ms=%s\n",scenario,batches,homes,catalogs,first_home-restore,last_end-restore;
      }' scenario="$scenario" "$TEST_EVENTS" || fail 'prazo/espacamento/serialidade de recuperacao divergente'
    if [[ "$scenario" == recovery_absent || "$scenario" == recovery_benign_mixed_error ]]; then
      restore_started_ms="$(sed -n 's/^RESTORE_UP time_ms=//p' "$TEST_EVENTS")"
      [[ "$restore_started_ms" =~ ^[0-9]+$ ]] || fail 'marcador RESTORE_UP invalido'
      read_timeout_recovery_window "${work_dir}/last-operation.log"
      printf 'RECOVERY_BOUNDARIES restore_up_ms=%s window_start_ms=%s window_end_ms=%s operation_end_ms=%s\n' \
        "$restore_started_ms" "$recovery_started_ms" "$recovery_finished_ms" "$operation_finished_ms"
      printf 'RECOVERY_DEADLINE clock=real elapsed_ms=%s result=INCOMPLETE original_rc=1 recreate_count=1\n' "$elapsed_recovery_ms"
    fi
  fi
  if [[ "$scenario" == frontend_down || "$scenario" == gateway_down || "$scenario" == recovery_new_frontend_down ]]; then
    grep -q 'url=http://127.0.0.1:28080/api/health/readiness status=200' "${work_dir}/last-operation.log" || fail 'backend UP nao observado'
    port=23010
    [[ "$scenario" != gateway_down ]] || port=23000
    grep -q "url=http://127.0.0.1:${port}/health/.*status=503" "${work_dir}/last-operation.log" || fail 'frontend/gateway DOWN nao observado'
    grep -qx 'original_rc=1' "${test_root}/operations/active.state" || fail 'erro da candidata perdido'
    if [[ "$scenario" == recovery_new_frontend_down ]]; then
      grep -qx "${previous_sha}|main-v1" "${snapshot}/previous.health-profile" || fail 'base nova foi rebaixada a perfil legado'
      [[ "$(grep -c '^RESTORE_UP ' "$TEST_EVENTS")" -eq 1 ]] || fail 'base nova nao registrou exatamente uma restauracao fisica'
      restore_started_ms="$(sed -n 's/^RESTORE_UP time_ms=//p' "$TEST_EVENTS")"
      [[ "$restore_started_ms" =~ ^[0-9]+$ ]] || fail 'marcador RESTORE_UP invalido'
      read_timeout_recovery_window "${work_dir}/last-operation.log"
      printf 'RECOVERY_BOUNDARIES restore_up_ms=%s window_start_ms=%s window_end_ms=%s operation_end_ms=%s\n' \
        "$restore_started_ms" "$recovery_started_ms" "$recovery_finished_ms" "$operation_finished_ms"
      printf 'NEW_BASE_RECOVERY elapsed_ms=%s result=INCOMPLETE profile=main-v1\n' "$elapsed_recovery_ms"
    else
      grep -qx "${previous_sha}|legacy-a60" "${snapshot}/previous.health-profile" || fail 'rollback legado sem perfil fixo'
    fi
  fi
  if [[ "$scenario" == success ]]; then
    restored_identity="$(application_identity)"
    bash "$helper" rollback "$test_root" "$operation_id" --confirm-daemon-quiescent >> "${work_dir}/last-operation.log" 2>&1
    grep -qx result=ROLLED_BACK "${test_root}/operations/active.state" || fail 'rollback repetido nao concluiu'
    [[ "$(application_identity)" == "$restored_identity" ]] || fail 'rollback repetido nao foi idempotente para imagem/config/current'
    ids_before_reconcile="$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')"
    bash "$helper" reconcile "$test_root" "$operation_id" "$previous_sha" --confirm-daemon-quiescent >> "${work_dir}/last-operation.log" 2>&1
    grep -qx result=RECONCILED "${test_root}/operations/active.state" || fail 'reconciliacao real nao concluiu'
    [[ "$(application_identity)" == "$restored_identity" ]] || fail 'reconciliacao alterou imagem/config/current'
    [[ "$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')" == "$ids_before_reconcile" ]] || fail 'reconciliacao recriou runtime'
    [[ "$(grep -c '^CONTROLLED_BOUNDARY app_build_prepared_images$' "$TEST_EVENTS")" == "$docker_cmd_before" ]] || fail 'recuperacao manual repetida reconstruiu imagens'
    echo 'PASS: rollback_manual_idempotente_e_reconciliacao_sem_mutacao_do_runtime'
  fi
  if [[ "$scenario" == recovery_absent ]]; then
    cp "${test_root}/operations/active.state" "${case_dir}/incomplete-original.state"
    ids_before_retry="$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')"
    "$real_docker" exec "${prefix}-frontend" node -e 'fetch("http://127.0.0.1:8080/__test/control/recovered").then(r=>{if(!r.ok)process.exit(1)})'
    bash "$helper" rollback "$test_root" "$operation_id" --confirm-daemon-quiescent >> "${work_dir}/last-operation.log" 2>&1
    grep -qx result=INCOMPLETE "${case_dir}/incomplete-original.state" || fail 'reprovacao inicial perdida'
    grep -qx result=ROLLED_BACK "${test_root}/operations/active.state" || fail 'saude recuperada nao reconhecida'
    grep -qx original_rc=1 "${test_root}/operations/active.state" || fail 'primeiro erro perdido no retry'
    [[ "$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')" == "$ids_before_retry" ]] || fail 'retry de INCOMPLETE refez restauro concluido'
    [[ "$(grep -c '^RESTORE_UP ' "$TEST_EVENTS")" == 1 ]] || fail 'restauro fisico foi repetido'
    echo 'PASS: incomplete_funcional_retry_somente_observacao_sem_segundo_restauro'
  fi
  # Retain sanitized evidence in the caller/CI log before deleting private temp
  # snapshots. The emitted helper lines contain only fixed URLs and technical IDs.
  printf 'PROBE_EVIDENCE_BEGIN scenario=%s\n' "$scenario"
  grep '^PROBE ' "${work_dir}/last-operation.log"
  if [[ "$scenario" == startup_callback* ]]; then grep '^CALLBACK_TRACE ' "$TEST_EVENTS"; fi
  if [[ "$scenario" == *benign* ]]; then grep '^CONTENT_TRACE ' "$TEST_EVENTS"; fi
  if [[ "$scenario" == recovery_* ]]; then grep -E '^(RESTORE_UP|PROBE_TRACE).*' "$TEST_EVENTS"; fi
  printf 'PROBE_EVIDENCE_END scenario=%s\n' "$scenario"
  printf 'PASS: containers_%s elapsed_s=%s uid=%s snapshot_mode=600 previous=%s candidate=%s restored=%s\n' \
    "$scenario" "$((SECONDS - started))" "$EUID" "$previous_image" "$candidate_image" "$previous_image"
done
sha256sum "$helper" "$workflow"
echo 'OPERATION_TEST_COVERAGE core=real_containers workflow=integral backfill=synthetic_validate sql=synthetic_aggregate public_predicate=real no_real_spring_or_storage_claim=true'
echo 'PRODUCTION_OPERATION_CONTAINER_TESTS=PASS'
