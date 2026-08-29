#!/usr/bin/env bash

set -Eeuo pipefail

V2_LAB_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "${V2_LAB_DIR}/lib.sh"

v2_lab_container() {
  v2_compose ps -q "$1"
}

v2_probe_once() {
  local url="$1" expected_status="$2" required_text="${3:-}"
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    "${V2_NODE_IMAGE}" node -e '
      const [url, expected, text] = process.argv.slice(1);
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 10000);
      fetch(url, { redirect: "manual", signal: controller.signal })
        .then(async response => {
          const body = await response.text();
          const ok = response.status === Number(expected) && (!text || body.includes(text));
          console.log(JSON.stringify({ status: response.status, bytes: Buffer.byteLength(body) }));
          process.exit(ok ? 0 : 1);
        })
        .catch(error => { console.error(error.name); process.exit(1); })
        .finally(() => clearTimeout(timer));
    ' "${url}" "${expected_status}" "${required_text}" </dev/null
}

v2_wait_probe() {
  local url="$1" expected_status="$2" required_text="${3:-}" attempt
  for attempt in $(seq 1 90); do
    if v2_probe_once "${url}" "${expected_status}" "${required_text}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  v2_probe_once "${url}" "${expected_status}" "${required_text}"
}

v2_generate_tls() {
  local cert_dir="${V2_RUNTIME_DIR}/certs/minio"
  mkdir -m 0700 -p -- "${cert_dir}" "${V2_RUNTIME_DIR}/certs" "${V2_RUNTIME_DIR}/reports"
  openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 1 \
    -subj '/CN=minio' -addext 'subjectAltName=DNS:minio' \
    -keyout "${cert_dir}/private.key" -out "${cert_dir}/public.crt" >/dev/null 2>&1
  chmod 0600 "${cert_dir}/private.key" "${cert_dir}/public.crt"
  docker run --rm \
    --volume "${V2_RUNTIME_DIR}/certs:/certs" \
    "${V2_JRE_IMAGE}" keytool -importcert -noprompt \
    -alias pipeline-v2-minio -file /certs/minio/public.crt \
    -keystore /certs/truststore.p12 -storetype PKCS12 \
    -storepass pipeline-v2-trust >/dev/null
  chmod 0600 "${V2_RUNTIME_DIR}/certs/truststore.p12"
}

v2_wait_postgres() {
  local container attempt
  container="$(v2_lab_container postgres)"
  for attempt in $(seq 1 60); do
    if docker exec "${container}" pg_isready -U topsdojob_v2 -d topsdojob_v2 \
      </dev/null >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

v2_mc() {
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    --volume "${V2_RUNTIME_DIR}/mc:/root/.mc" \
    --volume "${V2_RUNTIME_DIR}/fixture/objects:/fixtures:ro" \
    "${V2_MINIO_CLIENT_IMAGE}" "$@" </dev/null
}

v2_wait_minio() {
  local attempt
  mkdir -m 0700 -p -- "${V2_RUNTIME_DIR}/mc"
  for attempt in $(seq 1 60); do
    if v2_mc --insecure alias set local https://minio:9000 \
      "${V2_MINIO_ACCESS_KEY}" "${V2_MINIO_SECRET_KEY}" \
      --api S3v4 --path on >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

v2_smtp_probe() {
  docker run --rm --network "${V2_PROJECT_NAME}_lab" \
    "${V2_NODE_IMAGE}" node -e '
      const net = require("node:net");
      const timer = setTimeout(() => process.exit(1), 3000);
      const socket = net.createConnection({ host: "mailpit", port: 1025 });
      socket.once("data", data => {
        clearTimeout(timer);
        process.exit(data.toString("utf8").startsWith("220") ? 0 : 1);
      });
      socket.once("error", () => process.exit(1));
    ' </dev/null
}

v2_wait_local_dependencies() {
  local attempt
  v2_wait_probe http://efi-stub:8090 200 '"provider":"local-stub"' ||
    v2_die "stub HTTP local da Efi nao ficou pronto"
  for attempt in $(seq 1 60); do
    if v2_smtp_probe >/dev/null 2>&1; then
      v2_log "LOCAL_EFI_STUB=OK LOCAL_SMTP=OK"
      return 0
    fi
    sleep 1
  done
  v2_die "SMTP local nao ficou pronto"
}

v2_psql() {
  local container
  container="$(v2_lab_container postgres)"
  docker exec "${container}" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username topsdojob_v2 --dbname topsdojob_v2 "$@" </dev/null
}

v2_prepare_database_and_storage() {
  local container object_count
  v2_compose up -d postgres minio mailpit efi-stub
  v2_wait_postgres || v2_die "PostgreSQL 17 nao ficou pronto"
  v2_wait_minio || v2_die "MinIO TLS local nao ficou pronto"
  v2_wait_local_dependencies
  for bucket in topsdojob-v2-public topsdojob-v2-private topsdojob-v2-documents; do
    v2_mc --insecure mb --ignore-existing "local/${bucket}" >/dev/null
  done

  v2_compose run --rm --no-deps -T flyway migrate </dev/null
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';" | tr -d '[:space:]')" == "53" ]]
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';" | tr -d '[:space:]')" == "53" ]]
  [[ "$(v2_psql --tuples-only --no-align --command 'SELECT count(*) FROM flyway_schema_history WHERE NOT success;' | tr -d '[:space:]')" == "0" ]]

  v2_node "scripts/deploy/v2/contract.mjs" fixture "${V2_RUNTIME_DIR}/fixture" 1344
  container="$(v2_lab_container postgres)"
  docker cp "${V2_RUNTIME_DIR}/fixture/fixture.sql" "${container}:/tmp/pipeline-v2-fixture.sql"
  docker exec "${container}" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
    --username topsdojob_v2 --dbname topsdojob_v2 \
    --file /tmp/pipeline-v2-fixture.sql </dev/null >/dev/null
  docker exec "${container}" rm -- /tmp/pipeline-v2-fixture.sql </dev/null
  v2_mc --insecure mirror /fixtures local/topsdojob-v2-public --overwrite >/dev/null
  object_count="$(v2_mc --insecure find local/topsdojob-v2-public/hml/midias-aprovadas/restritas-borradas/v1 \
    --name '*.jpg' | wc -l | tr -d '[:space:]')"
  [[ "${object_count}" == "1344" ]] || v2_die "inventario MinIO divergente: ${object_count}"
  export V2_OBJECT_COUNT_BEFORE="${object_count}"
}

v2_run_backfill_mode() {
  local mode="$1" output="$2" confirm="false"
  [[ "${mode}" != "APPLY" ]] || confirm="true"
  docker image inspect "${V2_BACKEND_IMAGE}" >/dev/null
  v2_compose run --rm --no-deps -T backend \
    --app.bootstrap=restricted-media-preview-backfill \
    --spring.main.web-application-type=none \
    --spring.main.banner-mode=off \
    --logging.level.root=WARN \
    --app.restricted-media-preview-reconciliation.enabled=true \
    --app.restricted-media-preview-reconciliation.mode="${mode}" \
    --app.restricted-media-preview-reconciliation.apply-confirmed="${confirm}" \
    --app.restricted-media-preview-reconciliation.batch-size=200 \
    --app.restricted-media-preview-reconciliation.report-path="/reports/${mode,,}-$(basename "${output}").tsv" \
    </dev/null | tee "${output}"
}

v2_backfill_contract() {
  local before after object_count_after
  before="$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status='DESCONHECIDO';" | tr -d '[:space:]')"
  [[ "${before}" == "1344" ]]
  v2_run_backfill_mode PLAN "${V2_RUNTIME_DIR}/plan.log"
  grep -Eq 'mode=PLAN .*eligible=1344 .*r2_available=1344 .*missing=0 .*inconsistent=0 .*unproven=0 .*db_updated=0' "${V2_RUNTIME_DIR}/plan.log"
  after="$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status='DESCONHECIDO';" | tr -d '[:space:]')"
  [[ "${after}" == "${before}" ]] || v2_die "PLAN alterou o banco"

  v2_run_backfill_mode APPLY "${V2_RUNTIME_DIR}/apply.log"
  grep -Eq 'mode=APPLY .*eligible=1344 .*missing=0 .*inconsistent=0 .*unproven=0 .*db_updated=1344 .*db_available=1344 .*db_unknown=0 .*db_pending=0 .*db_inconsistent=0' "${V2_RUNTIME_DIR}/apply.log"
  v2_run_backfill_mode APPLY "${V2_RUNTIME_DIR}/apply-idempotent.log"
  grep -Eq 'mode=APPLY .*eligible=1344 .*db_updated=0 .*db_unchanged=1344 .*db_available=1344 .*db_unknown=0 .*db_pending=0 .*db_inconsistent=0' "${V2_RUNTIME_DIR}/apply-idempotent.log"

  [[ "$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status='DISPONIVEL';" | tr -d '[:space:]')" == "1344" ]]
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT count(*) FROM arquivo_midia WHERE preview_restrito_status IN ('DESCONHECIDO','PENDENTE');" | tr -d '[:space:]')" == "0" ]]
  object_count_after="$(v2_mc --insecure find local/topsdojob-v2-public/hml/midias-aprovadas/restritas-borradas/v1 \
    --name '*.jpg' | wc -l | tr -d '[:space:]')"
  [[ "${object_count_after}" == "${V2_OBJECT_COUNT_BEFORE}" ]] || v2_die "backfill alterou MinIO"
  v2_log "BACKFILL PLAN=OK APPLY=1344 IDEMPOTENT=0 OBJECT_MUTATIONS=0"
}

v2_candidate_gate() {
  local name="$1" start end rc
  shift
  start="$(date +%s%3N)"
  v2_log "CANDIDATE_GATE_START=${name}"
  set +e
  "$@"
  rc=$?
  set -e
  end="$(date +%s%3N)"
  if [[ "${rc}" -ne 0 ]]; then
    v2_log "CANDIDATE_GATE_RESULT=${name} result=FAIL exit=${rc} duration_ms=$((end-start))"
    return "${rc}"
  fi
  v2_log "CANDIDATE_GATE_RESULT=${name} result=OK exit=0 duration_ms=$((end-start))"
  V2_GATE_COUNT=$((V2_GATE_COUNT + 1))
}

v2_database_gate() {
  [[ "$(v2_psql --tuples-only --no-align --command 'SELECT 1;' | tr -d '[:space:]')" == "1" ]]
  [[ "$(v2_psql --tuples-only --no-align --command "SELECT max(version::int)||'|'||count(*) FILTER (WHERE NOT success) FROM flyway_schema_history WHERE version ~ '^[0-9]+$';" | tr -d '[:space:]')" == "53|0" ]]
}

v2_internal_api_gate() {
  local frontend backend_ip resolved configured
  frontend="$(v2_lab_container frontend)"
  backend_ip="$(docker inspect "$(v2_lab_container backend)" --format "{{(index .NetworkSettings.Networks \"${V2_PROJECT_NAME}_lab\").IPAddress}}")"
  resolved="$(docker exec "${frontend}" node -e "require('node:dns').lookup('backend',(e,a)=>{if(e)process.exit(1);process.stdout.write(a)})" </dev/null)"
  configured="$(docker inspect "${frontend}" --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^INTERNAL_API_URL=//p')"
  [[ "${configured}" == "http://backend:8080/api/public" && "${resolved}" == "${backend_ip}" ]]
}

v2_logs_gate() {
  local service container
  for service in backend frontend gateway; do
    container="$(v2_lab_container "${service}")"
    [[ "$(docker inspect "${container}" --format '{{.State.Running}}')" == "true" ]]
    if docker logs "${container}" 2>&1 | grep -Eqi 'APPLICATION FAILED TO START|OutOfMemoryError|FATAL:|panic:|emerg'; then
      return 1
    fi
  done
}

v2_start_candidate_and_gates() {
  local postgres
  docker image inspect "${V2_BACKEND_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build backend
  v2_wait_probe http://backend:8080/api/health/liveness 200 '"status":"UP"'
  v2_wait_probe http://backend:8080/api/health/readiness 200 '"status":"UP"'

  postgres="$(v2_lab_container postgres)"
  docker pause "${postgres}" >/dev/null
  v2_wait_probe http://backend:8080/api/health/readiness 503 '"status":"DOWN"'
  v2_probe_once http://backend:8080/api/health/liveness 200 '"status":"UP"' >/dev/null
  docker unpause "${postgres}" >/dev/null
  v2_wait_probe http://backend:8080/api/health/readiness 200 '"status":"UP"'
  v2_log "READINESS_503=OK LIVENESS_DEPENDENCY_FAILURE=200 RECOVERY=OK"

  docker image inspect "${V2_FRONTEND_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build frontend
  v2_wait_probe http://frontend:3000/health/liveness 200 '"status":"UP"'
  v2_wait_probe http://frontend:3000/health/readiness 200 '"status":"UP"'
  docker image inspect "${V2_GATEWAY_IMAGE}" >/dev/null
  v2_compose up -d --no-deps --no-build gateway
  v2_wait_probe http://gateway:8080/health/readiness 200 '"status":"UP"'

  V2_GATE_COUNT=0
  v2_candidate_gate backend_liveness v2_probe_once http://backend:8080/api/health/liveness 200 '"status":"UP"'
  v2_candidate_gate backend_readiness v2_probe_once http://backend:8080/api/health/readiness 200 '"status":"UP"'
  v2_candidate_gate frontend_liveness v2_probe_once http://frontend:3000/health/liveness 200 '"status":"UP"'
  v2_candidate_gate frontend_readiness v2_probe_once http://frontend:3000/health/readiness 200 '"status":"UP"'
  v2_candidate_gate gateway_backend_liveness v2_probe_once http://gateway:8080/api/health/liveness 200 '"status":"UP"'
  v2_candidate_gate gateway_backend_readiness v2_probe_once http://gateway:8080/api/health/readiness 200 '"status":"UP"'
  v2_candidate_gate gateway_frontend_liveness v2_probe_once http://gateway:8080/health/liveness 200 '"status":"UP"'
  v2_candidate_gate gateway_frontend_readiness v2_probe_once http://gateway:8080/health/readiness 200 '"status":"UP"'
  v2_candidate_gate gateway_home v2_probe_once http://gateway:8080/ 200
  v2_candidate_gate gateway_listagem v2_probe_once http://gateway:8080/anuncios 200
  v2_candidate_gate gateway_localidades v2_probe_once http://gateway:8080/api/public/localidades 200
  v2_candidate_gate database v2_database_gate
  v2_candidate_gate internal_api_dns v2_internal_api_gate
  v2_candidate_gate candidate_logs v2_logs_gate
  [[ "${V2_GATE_COUNT}" -eq 14 ]] || v2_die "candidate gates incompletos: ${V2_GATE_COUNT}/14"
  v2_log "CANDIDATE_GATES=14/14"
}

v2_port_occupied_contract() {
  local holder challenger port
  holder="${V2_PROJECT_NAME}-port-holder"
  challenger="${V2_PROJECT_NAME}-port-challenger"
  docker run -d --name "${holder}" -p 127.0.0.1::8099 "${V2_NODE_IMAGE}" \
    node -e "require('node:http').createServer((q,s)=>s.end('ok')).listen(8099,'0.0.0.0')" >/dev/null
  port="$(docker port "${holder}" 8099/tcp | awk -F: 'END {print $NF}')"
  if docker run --name "${challenger}" -p "127.0.0.1:${port}:8099" "${V2_NODE_IMAGE}" \
    node -e "require('node:http').createServer(()=>{}).listen(8099,'0.0.0.0')" >/dev/null 2>&1; then
    return 1
  fi
  docker rm "${challenger}" >/dev/null 2>&1 || true
  docker stop --time 10 "${holder}" >/dev/null
  docker rm "${holder}" >/dev/null
  v2_log "PORT_OCCUPIED=BLOCKED"
}

v2_invalid_nginx_contract() {
  local invalid="${V2_RUNTIME_DIR}/invalid-nginx.conf"
  printf '%s\n' 'this is not valid nginx configuration;' > "${invalid}"
  if docker run --rm --volume "${invalid}:/etc/nginx/conf.d/default.conf:ro" \
    "${V2_NGINX_IMAGE}" nginx -t >/dev/null 2>&1; then
    return 1
  fi
  v2_log "INVALID_NGINX=BLOCKED"
}

v2_write_drain_config() {
  local upstream="$1" output="$2"
  printf '%s\n' \
    'events {}' \
    'http {' \
    '  server {' \
    '    listen 8099;' \
    "    location / { proxy_pass http://${upstream}:8081; proxy_read_timeout 60s; }" \
    '  }' \
    '}' > "${output}"
}

v2_long_request_drain_contract() {
  local old new proxy client config response
  old="${V2_PROJECT_NAME}-old"
  new="${V2_PROJECT_NAME}-new"
  proxy="${V2_PROJECT_NAME}-drain-proxy"
  client="${V2_PROJECT_NAME}-long-client"
  config="${V2_RUNTIME_DIR}/drain-nginx.conf"
  local server='const http=require("node:http");const name=process.argv[1];http.createServer((req,res)=>{res.writeHead(200,{"content-type":"application/octet-stream"});let n=0;const timer=setInterval(()=>{res.write(name+":"+n+"\n");if(++n===17){clearInterval(timer);res.end(name)}},1000)}).listen(8081,"0.0.0.0")'
  docker run -d --name "${old}" --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "${server}" old >/dev/null
  docker run -d --name "${new}" --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "${server}" new >/dev/null
  v2_write_drain_config "${old}" "${config}"
  docker run -d --name "${proxy}" --network "${V2_PROJECT_NAME}_lab" \
    --volume "${config}:/etc/nginx/nginx.conf:ro" "${V2_NGINX_IMAGE}" >/dev/null
  sleep 1
  docker run -d --name "${client}" --network "${V2_PROJECT_NAME}_lab" \
    "${V2_NODE_IMAGE}" node -e "fetch('http://${proxy}:8099/').then(async r=>{const b=await r.text();console.log(r.status+'|'+b.slice(-3));process.exit(r.status===200&&b.endsWith('old')?0:1)}).catch(()=>process.exit(1))" >/dev/null
  sleep 2
  v2_write_drain_config "${new}" "${config}"
  docker exec "${proxy}" nginx -t </dev/null >/dev/null
  docker exec "${proxy}" nginx -s reload </dev/null >/dev/null
  response="$(docker run --rm --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "fetch('http://${proxy}:8099/').then(async r=>{const b=await r.text();console.log(r.status+'|'+b.slice(-3))})" </dev/null)"
  [[ "${response}" == "200|new" ]]
  [[ "$(docker wait "${client}")" == "0" ]]
  [[ "$(docker logs "${client}" | tail -n 1)" == "200|old" ]]
  docker rm "${client}" >/dev/null

  v2_write_drain_config "${old}" "${config}"
  docker exec "${proxy}" nginx -t </dev/null >/dev/null
  docker exec "${proxy}" nginx -s reload </dev/null >/dev/null
  response="$(docker run --rm --network "${V2_PROJECT_NAME}_lab" "${V2_NODE_IMAGE}" node -e "fetch('http://${proxy}:8099/').then(async r=>{const b=await r.text();console.log(r.status+'|'+b.slice(-3))})" </dev/null)"
  [[ "${response}" == "200|old" ]]
  for container in "${proxy}" "${new}" "${old}"; do
    docker stop --time 30 "${container}" >/dev/null
    docker rm "${container}" >/dev/null
  done
  v2_log "LONG_REQUEST_GT_15S=OK STREAM=OK DRAIN=OK ROLLBACK=OK HTTP_500_502_504=0"
}

v2_run_lab() {
  local evidence_dir="$1"
  v2_require_command openssl
  mkdir -m 0700 -p -- "${evidence_dir}"
  v2_generate_tls
  v2_prepare_database_and_storage
  v2_backfill_contract
  v2_start_candidate_and_gates
  v2_port_occupied_contract
  v2_invalid_nginx_contract
  v2_long_request_drain_contract
  printf '%s\n' \
    'RUN=PASS' \
    'CANDIDATE_GATES=14/14' \
    'READINESS_503=PASS' \
    'BACKFILL_PLAN=PASS' \
    'BACKFILL_APPLY=1344' \
    'BACKFILL_SECOND_APPLY=0' \
    'R2_REAL_MUTATIONS=0' \
    'HTTP_500_502_504=0' > "${evidence_dir}/lab-summary.txt"
}
