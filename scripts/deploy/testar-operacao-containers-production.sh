#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
helper="${script_dir}/proteger-operacao-production.sh"
workflow="${repo_root}/.github/workflows/deploy-production.yml"
fail() { printf 'FALHA_CONTAINER_OPERACAO: %s\n' "$*" >&2; exit 1; }
[[ "$(uname -s)" == Linux ]] || fail 'execute em Linux real'
[[ "$EUID" -ne 0 ]] || fail 'execute como usuario nao-root para provar snapshot 0600 de UID nao-zero'
for executable in docker curl bash flock setsid timeout sha256sum; do command -v "$executable" >/dev/null || fail "ferramenta ausente: $executable"; done
real_docker="$(command -v docker)"
real_curl="$(command -v curl)"
"$real_docker" compose version >/dev/null
[[ "$("$real_docker" version --format '{{.Server.Os}}')" == linux ]] || fail 'Docker Linux necessario'
for base in node:22.13.1-alpine postgres:17.10-alpine nginx:1.27-alpine; do
  if ! "$real_docker" image inspect "$base" >/dev/null 2>&1; then "$real_docker" pull "$base"; fi
done
token="$(cat /proc/sys/kernel/random/uuid)"
prefix="topsv3-operation-${token:0:12}"
work_base="$(readlink -e "${OPERATION_TEST_WORK_ROOT:-${TMPDIR:-/tmp}}")"
work_dir="$(mktemp -d "${work_base}/tops-operation-containers.XXXXXX")"
resources="${work_dir}/resources"
network="${prefix}-net"
previous_sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
candidate_sha=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
previous_key=INDEXNOW-SYNTHETIC-PREVIOUS
candidate_key=INDEXNOW-SYNTHETIC-CANDIDATE
attached=0
touch "$resources"
record() { printf '%s|%s|%s\n' "$1" "$2" "${3:-$token}" >> "$resources"; }
cleanup() {
  local rc=$? kind identifier owner actual id
  trap - EXIT
  if [[ "$attached" -eq 1 ]]; then
    "$real_docker" network disconnect "$network" "$OPERATION_TEST_CONTROLLER" >/dev/null 2>&1 || { [[ "$rc" -ne 0 ]] || rc=1; }
  fi
  while IFS='|' read -r kind identifier owner; do
    case "$kind" in
      container|image) actual="$("$real_docker" "$kind" inspect --format '{{index .Config.Labels "topsv3.operation.test"}}' "$identifier" 2>/dev/null)" || continue ;;
      volume|network) actual="$("$real_docker" "$kind" inspect --format '{{index .Labels "topsv3.operation.test"}}' "$identifier" 2>/dev/null)" || continue ;;
      *) [[ "$rc" -ne 0 ]] || rc=1; continue ;;
    esac
    if [[ "$actual" != "$owner" ]]; then
      echo 'FALHA_CLEANUP: ownership divergente; recurso preservado' >&2
      [[ "$rc" -ne 0 ]] || rc=1
      continue
    fi
    id="$("$real_docker" "$kind" inspect --format '{{.Id}}' "$identifier" 2>/dev/null)" || id="$identifier"
    case "$kind" in
      container) "$real_docker" rm -f "$id" >/dev/null ;;
      image) "$real_docker" image rm -f "$id" >/dev/null ;;
      volume) "$real_docker" volume rm "$identifier" >/dev/null ;;
      network) "$real_docker" network rm "$id" >/dev/null ;;
    esac || { [[ "$rc" -ne 0 ]] || rc=1; }
  done < <(tac "$resources")
  if [[ "$rc" -ne 0 && -f "${work_dir}/last-operation.log" ]]; then tail -n 100 "${work_dir}/last-operation.log" >&2; fi
  [[ "$work_dir" == "${work_base}/tops-operation-containers."* ]] || exit 1
  rm -rf -- "$work_dir" || { [[ "$rc" -ne 0 ]] || rc=1; }
  printf 'CONTAINER_TEST_CLEANUP exit=%s owned_resources_only=true\n' "$rc"
  exit "$rc"
}
trap cleanup EXIT

record network "$network"
"$real_docker" network create --internal --label "topsv3.operation.test=${token}" "$network" >/dev/null
if [[ -n "${OPERATION_TEST_CONTROLLER:-}" ]]; then
  # Optional only for a local Docker-in-Docker-client controller. Ubuntu CI runs
  # this script directly and reaches its private bridge IPs without this branch.
  [[ -n "$("$real_docker" inspect --format '{{index .Config.Labels "topsv3.operation.test"}}' "$OPERATION_TEST_CONTROLLER")" ]] || fail 'controller nao rotulado'
  "$real_docker" network disconnect none "$OPERATION_TEST_CONTROLLER" >/dev/null 2>&1 || true
  "$real_docker" network connect "$network" "$OPERATION_TEST_CONTROLLER"
  attached=1
fi
sentinel="${prefix}-sentinel"
record container "$sentinel" "sentinel-${token}"
sentinel_id="$("$real_docker" create --name "$sentinel" --network none --label "topsv3.operation.test=sentinel-${token}" node:22.13.1-alpine node -e 'process.exit(0)')"
pg_volume="${prefix}-postgres-data"
record volume "$pg_volume"
"$real_docker" volume create --label "topsv3.operation.test=${token}" "$pg_volume" >/dev/null
record container "${prefix}-postgres"
postgres_id="$("$real_docker" run -d --pull never --name "${prefix}-postgres" \
  --network "$network" --label "topsv3.operation.test=${token}" \
  --volume "${pg_volume}:/var/lib/postgresql/data" --env POSTGRES_HOST_AUTH_METHOD=trust \
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
let readinessFailures = 1, mode = '';
http.createServer(async (request, response) => {
  const correlation = request.headers['x-request-id'] || 'none';
  const send = (status, body, contentType = 'text/html; charset=utf-8') => {
    response.writeHead(status, {'content-type': contentType}); response.end(body);
  };
  try {
    if (request.url.startsWith('/__test/control/')) {
      mode = request.url.split('/').at(-1);
      if (mode === 'final_failure') console.log('fixture enabled=true');
      return send(200, 'CONTROLLED');
    }
    if (role === 'backend') {
      if (request.url === '/api/health/readiness') {
        const ready = readinessFailures-- <= 0;
        return send(ready ? 200 : 503, JSON.stringify({status: ready ? 'UP' : 'DOWN', app: 'topsdojob-v3-backend'}), 'application/json');
      }
      if (request.url === '/api/public/localidades') {
        console.log(`LOCALIDADES_REQUEST correlation=${correlation}`);
        return send(200, JSON.stringify({estados: [], cidades: [], totalAnuncios: 0}), 'application/json');
      }
      return send(200, JSON.stringify({status: 'UP', app: 'topsdojob-v3-backend'}), 'application/json');
    }
    if (role === 'gateway') {
      const result = await fetch(`http://${prefix}-frontend:8080${request.url}`, {headers: {'X-Request-ID': correlation}, signal: AbortSignal.timeout(2000)});
      return send(result.status, await result.text());
    }
    const locality = await fetch(`http://${prefix}-backend:8080/api/public/localidades`, {headers: {'X-Request-ID': correlation}, signal: AbortSignal.timeout(2000)});
    const data = await locality.json();
    if (!locality.ok || !Array.isArray(data.cidades)) return send(503, 'LOCALITY_UNAVAILABLE');
    if (mode === 'http_failure') return send(200, '<h1>Application error</h1>');
    return send(200, request.url === '/anuncios'
      ? '<h1>Anúncios de acompanhantes</h1><p>Explore perfis publicados</p><p>Nenhum anúncio publicado.</p>'
      : '<h1>Encontre acompanhantes perto de você</h1>');
  } catch { send(503, 'CONTROLLED_DEPENDENCY_FAILURE'); }
}).listen(8080, '0.0.0.0');
NODE_SERVER
)"
for sha in "$previous_sha" "$candidate_sha"; do
  builder="${prefix}-image-${sha:0:1}"
  record container "$builder"
  builder_id="$("$real_docker" create --name "$builder" --label "topsv3.operation.test=${token}" --entrypoint node node:22.13.1-alpine -e "$server_program")"
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
if [[ "${args[0]:-}" == exec && "$joined" == *' psql '* ]]; then
  if [[ "$joined" == *' --command '* ]]; then printf '053|0|1\n'; else cat >/dev/null; cat "$TEST_SNAPSHOT"; fi
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
  printf 'container|%s|%s\n' "$name" "$TEST_TOKEN" >> "$TEST_RESOURCES"
  args=(run --name "$name" --label "topsv3.operation.test=${TEST_TOKEN}" "${args[@]:1}")
fi
if [[ "${args[0]:-}" == compose && "$joined" == *' up '* ]]; then
  for service in backend frontend gateway; do printf 'container|%s-%s|%s\n' "$TEST_PREFIX" "$service" "$TEST_TOKEN" >> "$TEST_RESOURCES"; done
  "$TEST_REAL_DOCKER" "${args[@]}"
  for service in backend frontend gateway; do
    "$TEST_REAL_DOCKER" inspect "${TEST_PREFIX}-${service}" --format '{{.Name}} {{.Id}} {{.Image}}' >> "$TEST_EVENTS"
  done
  if [[ "${TOPSV3_RELEASE_SHA:-}" == bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ]]; then
    for service in backend frontend gateway; do
      "$TEST_REAL_DOCKER" inspect "${TEST_PREFIX}-${service}" --format '{{.Id}} {{.Image}} {{json .Config.Env}}' >> "$TEST_ROOT/candidate-runtime"
    done
    if [[ "$TEST_SCENARIO" != success ]]; then
      target=frontend
      [[ "$TEST_SCENARIO" != final_failure ]] || target=backend
      "$TEST_REAL_DOCKER" exec "${TEST_PREFIX}-${target}" node -e '
        (async () => { for (let i=0;i<30;i++) { try {
          const r=await fetch("http://127.0.0.1:8080/__test/control/"+process.argv[1]); if(r.ok)return;
        } catch {} await new Promise(r=>setTimeout(r,100)); } process.exit(70); })();' "$TEST_SCENARIO"
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
for argument in "$@"; do
  case "$argument" in
    http://127.0.0.1:28080/*) target=backend; suffix="${argument#http://127.0.0.1:28080}" ;;
    http://127.0.0.1:23000/*) target=gateway; suffix="${argument#http://127.0.0.1:23000}" ;;
    http:*|https:*) echo 'URL fora do ensaio' >&2; exit 71 ;;
    *) args+=("$argument"); continue ;;
  esac
  ip="$("$TEST_REAL_DOCKER" inspect "${TEST_PREFIX}-${target}" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')"
  [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 72
  args+=("http://${ip}:8080${suffix}")
done
exec "$TEST_REAL_CURL" "${args[@]}"
CURL_BOUNDARY
chmod +x "${work_dir}/bin/docker" "${work_dir}/bin/curl"
export TEST_REAL_DOCKER="$real_docker" TEST_REAL_CURL="$real_curl" TEST_PREFIX="$prefix" TEST_TOKEN="$token" TEST_RESOURCES="$resources"
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

# Reuse the existing database gate's synthetic fixture builder. The actual
# database/Flyway validators still execute; no application schema is recreated.
source <(sed -n '/^write_snapshot() {$/,/^}$/p' "${script_dir}/testar-gate-banco-production.sh")

for scenario in http_failure final_failure success; do
  case_dir="${work_dir}/${scenario}"
  test_root="${case_dir}/production"
  secrets="${case_dir}/secrets"
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
      "${script_dir}/capturar-snapshot-gate-banco-production.sql" "${release}/scripts/deploy/"
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
      topsv3.operation.test: ${token}
    environment:
      SERVICE: ${service}
      TEST_PREFIX: ${prefix}
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
      done
      printf 'networks:\n  operation_test:\n    external: true\n    name: %s\n' "$network"
    } > "${release}/deploy/production/docker-compose.yml"
  done
  ln -s "releases/${previous_sha}" "${test_root}/current"
  write_snapshot "${case_dir}/database.snapshot" 100 100 70 30 35000 053 UP 1000 500
  sed -i '/^META|health|/d' "${case_dir}/database.snapshot"
  export TEST_ROOT="$test_root" TEST_SCENARIO="$scenario" TEST_SNAPSHOT="${case_dir}/database.snapshot" TEST_EVENTS="${case_dir}/events"
  touch "$TEST_EVENTS"
  TOPSV3_RELEASE_SHA="$previous_sha" docker compose --env-file "${secrets}/production.env" \
    -f "${test_root}/releases/${previous_sha}/deploy/production/docker-compose.yml" -p topsv3-production \
    up -d --no-deps --force-recreate --no-build --pull never backend frontend gateway >/dev/null
  before_ids="$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')"
  # Extract the real streamed activation body, changing only its filesystem root.
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
  started=$SECONDS
  if timeout --kill-after=5s 120s bash "${case_dir}/activation.sh" "$candidate_sha" > "${work_dir}/last-operation.log" 2>&1; then rc=0; else rc=$?; fi
  expected=1
  [[ "$scenario" != success ]] || expected=0
  [[ "$rc" -eq "$expected" ]] || fail "${scenario}: exit=${rc}, esperado=${expected}"
  [[ -s "${test_root}/candidate-runtime" ]] || fail 'candidata nunca alterou os tres servicos'
  [[ "$(wc -l < "${test_root}/candidate-runtime")" -eq 3 ]] || fail 'nao observou troca de tres servicos'
  grep -Fq "$candidate_image" "${test_root}/candidate-runtime" || fail 'runtime nunca usou imagem candidata'
  grep -q "configuration-${candidate_sha}" "${test_root}/candidate-runtime" || fail 'runtime nunca usou configuracao candidata'
  grep -q 'status=503' "${work_dir}/last-operation.log" || fail 'readiness negativa nao foi exercitada'
  grep -q 'status=200' "${work_dir}/last-operation.log" || fail 'readiness/HTTP nao recuperaram'
  grep -q 'LOCALIDADES_REQUEST' <("$real_docker" logs "${prefix}-backend" 2>&1) || fail 'frontend nao consultou backend/localidades'
  operation_id="$(sed -n 's/^id=//p' "${test_root}/operations/active.state")"
  snapshot="${test_root}/operations/${operation_id}"
  [[ "$(stat -c '%u %a' "${snapshot}/env.copy")" == "${EUID} 600" ]] || fail 'snapshot nao pertence ao UID nao-zero com modo0600'
  if [[ "$scenario" == success ]]; then
    grep -qx result=COMPLETED "${test_root}/operations/active.state" || fail 'sucesso sem conclusao'
    [[ "$(readlink -e "${test_root}/current")" == "${test_root}/releases/${candidate_sha}" ]] || fail 'candidata nao promovida'
    docker_cmd_before="$(grep -c '^CONTROLLED_BOUNDARY app_build_prepared_images$' "$TEST_EVENTS")"
    bash "$helper" rollback "$test_root" "$operation_id" --confirm-daemon-quiescent >> "${work_dir}/last-operation.log" 2>&1
    [[ "$(grep -c '^CONTROLLED_BOUNDARY app_build_prepared_images$' "$TEST_EVENTS")" == "$docker_cmd_before" ]] || fail 'rollback reconstruiu imagens'
  fi
  grep -qx result=ROLLED_BACK "${test_root}/operations/active.state" || fail "${scenario}: recuperacao nao concluida"
  [[ "$(readlink -e "${test_root}/current")" == "${test_root}/releases/${previous_sha}" ]] || fail 'release anterior nao restaurada'
  [[ "$(cat "${secrets}/production.env")" == "INDEXNOW_KEY=${previous_key}" ]] || fail 'env anterior nao restaurado'
  [[ "$(stat -c '%u %a' "${secrets}/production.env")" == "${EUID} 644" ]] || fail 'owner/mode anterior nao restaurado'
  for service in backend frontend gateway; do
    [[ "$("$real_docker" inspect "${prefix}-${service}" --format '{{.Image}}')" == "$previous_image" ]] || fail "imagem anterior divergente: $service"
    "$real_docker" inspect "${prefix}-${service}" --format '{{json .Config.Env}}' | grep -q "configuration-${previous_sha}" || fail "configuracao anterior divergente: $service"
  done
  after_ids="$("$real_docker" inspect "${prefix}-backend" "${prefix}-frontend" "${prefix}-gateway" --format '{{.Id}}')"
  [[ "$before_ids" != "$after_ids" ]] || fail 'nao houve recriacao real de containers'
  [[ "$("$real_docker" inspect "${prefix}-postgres" --format '{{.Id}}')" == "$postgres_id" ]] || fail 'PostgreSQL foi trocado'
  [[ "$("$real_docker" inspect "$sentinel" --format '{{.Id}}')" == "$sentinel_id" ]] || fail 'sentinela alheia alterada'
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
  printf 'PASS: containers_%s elapsed_s=%s uid=%s snapshot_mode=600 previous=%s candidate=%s restored=%s\n' \
    "$scenario" "$((SECONDS - started))" "$EUID" "$previous_image" "$candidate_image" "$previous_image"
done
sha256sum "$helper" "$workflow"
echo 'PRODUCTION_OPERATION_CONTAINER_TESTS=PASS'
