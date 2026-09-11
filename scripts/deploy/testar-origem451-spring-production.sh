#!/usr/bin/env bash
set -euo pipefail

# Negative proof only: exact archived Spring451 with the observed absence of
# graceful settings. No coordinator/operation success is simulated or claimed.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd -- "${script_dir}/../.." && pwd)"
origin=451a6cb90dd1e67c5c774a0618c984d6b73735f8
archive_hash=57597678e86b1fe5d579a7bf82d971e81efc5e618222b7323434b5047003b3dc
observer="${script_dir}/validar-transicao-previews-runtime.py"
fail() { printf 'ORIGIN451_SPRING_TEST_FAIL: %s\n' "$*" >&2; exit 1; }
[[ $# -eq 0 && "$(uname -s)" == Linux ]] || fail 'requires Linux; no external target arguments accepted'
for tool in docker python3 mvn tar sha256sum timeout keytool; do command -v "$tool" >/dev/null || fail "missing tool: $tool"; done
work_base="$(readlink -e "${ORIGIN451_WORK_ROOT:-${TMPDIR:-/tmp}}")"
evidence="$(mktemp -d "${work_base}/tops-origin451-spring.XXXXXXXX")"
owner="$(cat /proc/sys/kernel/random/uuid)"
label=topsv3.origin451.test
backend=topsv3-production-backend
pg=topsv3-production-postgres
network=topsv3-production-net
storage="tops-origin451-${owner}-storage"
flyway="tops-origin451-${owner}-flyway"
image="tops-origin451-proof:${owner}"
container_ids=() network_id= image_id=
docker_read() { timeout --signal=TERM --kill-after=2s 15s docker "$@"; }
docker_mutate() {
  local seconds="$1" rc
  shift
  if timeout --signal=TERM --kill-after=2s "${seconds}s" docker "$@"; then return 0; else rc=$?; fi
  # A command error/timeout does not prove the daemon-side mutator terminated.
  # Persist even inside command substitutions; empty inventories cannot clear it.
  printf 'operation=%s exit=%s termination=UNPROVEN\n' "$1" "$rc" >>"${evidence}/mutator-termination-unproven"
  return "$rc"
}
docker_remove() { docker_mutate 30 "$@"; }
owned_volumes() {
  python3 - "$1" "$2" "$label" "$owner" <<'OWNED_CONTAINER'
import json, sys
from pathlib import Path
value=json.loads(Path(sys.argv[1]).read_text())[0]
assert value['Id'] == sys.argv[2]
assert value['Config']['Labels'].get(sys.argv[3]) == sys.argv[4]
for mount in value['Mounts']:
    if mount['Type'] == 'volume': print(mount['Name'])
OWNED_CONTAINER
}
cleanup() {
  local original=$? extra=0 identifier actual volume inventory_ok=false
  trap - EXIT
  # Inspect failure is never absence. Owner discovery covers a lost create reply.
  set +e
  printf '%s\n' "${container_ids[@]}" >"${evidence}/cleanup-container-ids"
  if docker_read ps -aq --no-trunc --filter "label=${label}=${owner}" >"${evidence}/cleanup-discovered-containers"; then
    cat "${evidence}/cleanup-discovered-containers" >>"${evidence}/cleanup-container-ids"
  else extra=1; fi
  if docker_read ps -aq --no-trunc >"${evidence}/cleanup-all-containers-before"; then inventory_ok=true; else extra=1; fi
  sort -u "${evidence}/cleanup-container-ids" -o "${evidence}/cleanup-container-ids"
  : >"${evidence}/cleanup-volume-names"
  while IFS= read -r identifier; do
    [[ -n "$identifier" ]] || continue
    # Preserve the initial anonymous-volume ledger even if an independent
    # remover already deleted the container without -v. Do not delete volumes
    # directly: a surviving known volume is a cleanup failure requiring review.
    if [[ -f "${evidence}/created-${identifier}.json" ]]; then
      owned_volumes "${evidence}/created-${identifier}.json" "$identifier" >>"${evidence}/cleanup-volume-names" || extra=1
    fi
    if [[ "$inventory_ok" != true ]]; then extra=1; continue; fi
    if ! grep -Fxq "$identifier" "${evidence}/cleanup-all-containers-before"; then continue; fi
    if ! docker_read inspect "$identifier" >"${evidence}/cleanup-before-${identifier}.json" 2>>"${evidence}/cleanup.log"; then extra=1; continue; fi
    if ! owned_volumes "${evidence}/cleanup-before-${identifier}.json" "$identifier" >"${evidence}/volumes-${identifier}"; then extra=1; continue; fi
    cat "${evidence}/volumes-${identifier}" >>"${evidence}/cleanup-volume-names"
    docker_remove rm -fv "$identifier" >>"${evidence}/cleanup.log" 2>&1 || extra=1
  done <"${evidence}/cleanup-container-ids"
  if docker_read ps -aq --no-trunc >"${evidence}/cleanup-all-containers-after"; then
    while IFS= read -r identifier; do
      [[ -n "$identifier" ]] || continue
      if grep -Fxq "$identifier" "${evidence}/cleanup-all-containers-after"; then extra=1; fi
    done <"${evidence}/cleanup-container-ids"
  else extra=1; fi
  if docker_read volume ls --format '{{.Name}}' >"${evidence}/cleanup-all-volumes-after"; then
    while IFS= read -r volume; do
      [[ -n "$volume" ]] || continue
      if grep -Fxq "$volume" "${evidence}/cleanup-all-volumes-after"; then extra=1; fi
    done <"${evidence}/cleanup-volume-names"
  else extra=1; fi
  printf '%s\n' "$network_id" >"${evidence}/cleanup-network-ids"
  if docker_read network ls -q --no-trunc --filter "label=${label}=${owner}" >"${evidence}/cleanup-discovered-networks"; then
    cat "${evidence}/cleanup-discovered-networks" >>"${evidence}/cleanup-network-ids"
  else extra=1; fi
  sort -u "${evidence}/cleanup-network-ids" -o "${evidence}/cleanup-network-ids"
  inventory_ok=false
  if docker_read network ls -q --no-trunc >"${evidence}/cleanup-all-networks-before"; then inventory_ok=true; else extra=1; fi
  while IFS= read -r identifier; do
    [[ -n "$identifier" ]] || continue
    if [[ "$inventory_ok" != true ]]; then extra=1; continue; fi
    if ! grep -Fxq "$identifier" "${evidence}/cleanup-all-networks-before"; then continue; fi
    actual="$(docker_read network inspect "$identifier" --format "{{index .Labels \"$label\"}}")" || actual=
    if [[ "$actual" != "$owner" ]]; then extra=1; continue; fi
    docker_remove network rm "$identifier" >>"${evidence}/cleanup.log" 2>&1 || extra=1
  done <"${evidence}/cleanup-network-ids"
  if docker_read network ls -q --no-trunc >"${evidence}/cleanup-all-networks-after"; then
    while IFS= read -r identifier; do
      [[ -n "$identifier" ]] || continue
      if grep -Fxq "$identifier" "${evidence}/cleanup-all-networks-after"; then extra=1; fi
    done <"${evidence}/cleanup-network-ids"
  else extra=1; fi
  printf '%s\n' "$image_id" >"${evidence}/cleanup-image-ids"
  if docker_read image ls -q --no-trunc --filter "label=${label}=${owner}" >"${evidence}/cleanup-discovered-images"; then
    cat "${evidence}/cleanup-discovered-images" >>"${evidence}/cleanup-image-ids"
  else extra=1; fi
  sort -u "${evidence}/cleanup-image-ids" -o "${evidence}/cleanup-image-ids"
  while IFS= read -r identifier; do
    [[ -n "$identifier" ]] || continue
    actual="$(docker_read image inspect "$identifier" --format "{{index .Config.Labels \"$label\"}}")" || actual=
    if [[ "$actual" != "$owner" ]]; then extra=1; continue; fi
    docker_remove image rm "$identifier" >>"${evidence}/cleanup.log" 2>&1 || extra=1
  done <"${evidence}/cleanup-image-ids"
  if docker_read image ls -q --no-trunc >"${evidence}/cleanup-all-images-after"; then
    while IFS= read -r identifier; do
      [[ -n "$identifier" ]] || continue
      if grep -Fxq "$identifier" "${evidence}/cleanup-all-images-after"; then extra=1; fi
    done <"${evidence}/cleanup-image-ids"
  else extra=1; fi
  # Recheck unique-owner inventories: late creation is not a clean result.
  for actual in container network image; do
    case "$actual" in
      container) docker_read ps -aq --no-trunc --filter "label=${label}=${owner}" ;;
      network) docker_read network ls -q --no-trunc --filter "label=${label}=${owner}" ;;
      image) docker_read image ls -q --no-trunc --filter "label=${label}=${owner}" ;;
    esac >"${evidence}/cleanup-owner-${actual}-after"
    if (( $? != 0 )) || [[ -s "${evidence}/cleanup-owner-${actual}-after" ]]; then extra=1; fi
  done
  if [[ -s "${evidence}/mutator-termination-unproven" ]]; then extra=1; fi
  printf 'ORIGIN451_SPRING_EVIDENCE=%s cleanup_exit=%s\n' "$evidence" "$extra"
  printf 'original_exit=%s\ncleanup_exit=%s\n' "$original" "$extra" >"${evidence}/cleanup.result"
  if (( original != 0 )); then exit "$original"; fi
  exit "$extra"
}
trap cleanup EXIT

# Successful daemon inventory is required before interpreting any absence.
docker ps -a --format '{{.Names}}' >"${evidence}/existing-containers"
docker network ls --format '{{.Name}}' >"${evidence}/existing-networks"
for name in "$backend" "$pg" "$storage" "$flyway"; do
  if grep -Fxq "$name" "${evidence}/existing-containers"; then fail "reserved fixture name occupied: $name"; fi
done
if grep -Fxq "$network" "${evidence}/existing-networks"; then fail 'canonical fixture network already exists'; fi
for base in postgres:17.10-alpine flyway/flyway:12.10.0 eclipse-temurin:17.0.13_11-jre node:22.13.1-alpine; do
  docker image inspect "$base" >"${evidence}/base-${base//[\/:]/_}.json" || fail "required local image absent: $base"
done
if [[ -n "${ORIGIN451_ARCHIVE:-}" ]]; then
  [[ -f "$ORIGIN451_ARCHIVE" && ! -L "$ORIGIN451_ARCHIVE" ]] || fail 'invalid provided archive'
  cp -- "$ORIGIN451_ARCHIVE" "${evidence}/origin451.tar"
else
  git -C "$repo" archive --format=tar "$origin" backend >"${evidence}/origin451.tar"
fi
[[ "$(sha256sum "${evidence}/origin451.tar" | cut -d' ' -f1)" == "$archive_hash" ]] || fail 'archive bytes differ from exact451 export'
mkdir "${evidence}/source" "${evidence}/build-context" "${evidence}/reports"
tar -xf "${evidence}/origin451.tar" -C "${evidence}/source"
source_root="${evidence}/source/backend"
[[ "$(find "${source_root}/src/main/resources/db/migration" -name 'V*.sql' | wc -l)" -eq 53 ]] || fail 'expected53 source migrations'
python3 - "$source_root" >"${evidence}/source-before.sha256" <<'SOURCE_HASH'
import hashlib
from pathlib import Path
import sys
root = Path(sys.argv[1])
for file in sorted(path for path in root.rglob('*') if path.is_file() and 'target' not in path.parts):
    print(hashlib.sha256(file.read_bytes()).hexdigest(), file.relative_to(root).as_posix())
SOURCE_HASH
maven_args=(-B -ntp -Dstyle.color=never -Dmaven.test.skip=true)
[[ "${ORIGIN451_MAVEN_OFFLINE:-false}" != true ]] || maven_args+=(-o)
[[ -z "${ORIGIN451_MAVEN_REPOSITORY:-}" ]] || maven_args+=("-Dmaven.repo.local=${ORIGIN451_MAVEN_REPOSITORY}")
(cd "$source_root" && timeout --signal=TERM --kill-after=15s 600s mvn "${maven_args[@]}" package) \
  >"${evidence}/maven.log" 2>&1 || fail 'exact451 build failed; see private maven.log'
jar="${source_root}/target/topsdojob-v3-backend-0.0.1-SNAPSHOT.jar"
[[ -s "$jar" ]] || fail 'exact451 executable jar missing'
cp -- "$jar" "${evidence}/build-context/app.jar"
cat >"${evidence}/build-context/Dockerfile" <<'DOCKERFILE'
FROM eclipse-temurin:17.0.13_11-jre
WORKDIR /app
COPY app.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
DOCKERFILE
docker_mutate 180 build --pull=false --network none \
  --label "${label}=${owner}" --label "topsv3.origin-sha=${origin}" -t "$image" "${evidence}/build-context" \
  >"${evidence}/image-build.log" 2>&1 || fail 'local image build failed'
image_id="$(docker image inspect "$image" --format '{{.Id}}')"
sha256sum "${evidence}/origin451.tar" "$jar" "$observer" >"${evidence}/input-identities.sha256"
network_id="$(docker_remove network create --internal --label "${label}=${owner}" "$network")"
new_container() {
  local id
  id="$(docker_remove create --pull never --label "${label}=${owner}" "$@")" || return $?
  [[ "$id" =~ ^[a-f0-9]{64}$ ]] || return 1
  container_ids+=("$id")
  docker inspect "$id" >"${evidence}/created-${id}.json"
  docker_remove start "$id" >/dev/null
  LAST_CONTAINER_ID="$id"
}
POSTGRES_PASSWORD="CHANGE_ME" PGPASSWORD="CHANGE_ME" new_container --name "$pg" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=origin451 -e POSTGRES_USER=origin451 -e POSTGRES_PASSWORD -e PGPASSWORD postgres:17.10-alpine
pg_id="$LAST_CONTAINER_ID"
bash "${script_dir}/aguardar-postgres-efemero.sh" "$pg" origin451 origin451 >"${evidence}/postgres-readiness.log" 2>&1
FLYWAY_PASSWORD="CHANGE_ME" new_container --name "$flyway" --network "$network" -e FLYWAY_PASSWORD \
  --mount "type=bind,source=${source_root}/src/main/resources/db/migration,target=/flyway/sql,readonly" \
  flyway/flyway:12.10.0 -url=jdbc:postgresql://postgres:5432/origin451 -user=origin451 -locations=filesystem:/flyway/sql migrate
[[ "$(timeout --signal=TERM 180s docker wait "$LAST_CONTAINER_ID")" == 0 ]] || fail '53migrations failed'
docker logs "$LAST_CONTAINER_ID" >"${evidence}/flyway.log" 2>&1

# TLS storage is wholly synthetic. This negative expects ZERO object requests;
# it does not claim S3 or pixel processing has been exercised.
keytool -genkeypair -noprompt -alias fixture -keyalg RSA -keysize 2048 -validity 1 \
  -dname CN=storage-fixture -storetype PKCS12 -keystore "${evidence}/fixture.p12" \
  -storepass CHANGE_ME -keypass CHANGE_ME >"${evidence}/tls-fixture.log" 2>&1
chmod 0600 "${evidence}/fixture.p12"
new_container --name "$storage" --network "$network" --network-alias storage-fixture \
  --mount "type=bind,source=${evidence}/fixture.p12,target=/fixture.p12,readonly" \
  node:22.13.1-alpine node -e '
const fs=require("fs"), https=require("https");
https.createServer({pfx:fs.readFileSync("/fixture.p12"),passphrase:"CHANGE_ME"},(req,res)=>{
  console.log("OBJECT_REQUEST");res.writeHead(503);res.end("SYNTHETIC_UNAVAILABLE");
}).listen(8443,"0.0.0.0");'
storage_id="$LAST_CONTAINER_ID"
SPRING_DATASOURCE_PASSWORD="CHANGE_ME" APP_EVENT_HASH_SALT="CHANGE_ME" \
APP_AGE_GATE_SIGNING_VALUE="CHANGE_ME" R2_ACCESS_KEY="CHANGE_ME" R2_SIGNING_VALUE="CHANGE_ME" \
new_container --name "$backend" --network "$network" \
  -e SPRING_PROFILES_ACTIVE=production -e APP_ENV=producao \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/origin451 -e SPRING_DATASOURCE_USERNAME=origin451 \
  -e SPRING_DATASOURCE_PASSWORD -e SPRING_JPA_HIBERNATE_DDL_AUTO=validate -e SPRING_JPA_OPEN_IN_VIEW=false \
  -e APP_EVENT_HASH_SALT -e APP_AGE_GATE_SIGNING_VALUE \
  -e APP_CANONICAL_DOMAIN=https://topsdojob.com -e OUTBOX_EMAIL_ENABLED=false \
  -e EFI_ENABLED=false -e EFI_RECONCILIATION_ENABLED=false -e EFI_WEBHOOK_REGISTRATION_ENABLED=false \
  -e R2_ENABLED=true -e R2_ENDPOINT=https://storage-fixture:8443 -e R2_ACCESS_KEY -e R2_SIGNING_VALUE \
  -e R2_PUBLIC_MEDIA_BUCKET=fixture-public -e R2_PRIVATE_MEDIA_BUCKET=fixture-private -e R2_DOCUMENT_BUCKET=fixture-documents \
  -e R2_PUBLIC_MEDIA_PREFIX=hml/fixture/public/ -e R2_PRIVATE_MEDIA_PREFIX=hml/fixture/private/ \
  -e R2_DOCUMENT_PREFIX=hml/fixture/documents/ -e R2_PUBLIC_BASE_URL=https://storage-fixture:8443 \
  "$image" --app.fixture.admin-provision.enabled=false --app.fixture.stories.enabled=false \
  --app.fixture.owner-credential.enabled=false --app.fixture.auth-smoke.enabled=false
backend_id="$LAST_CONTAINER_ID"
deadline=$((SECONDS + 120))
until docker exec "$storage_id" node -e '
fetch("http://topsv3-production-backend:8080/api/health/readiness",{signal:AbortSignal.timeout(2000)})
.then(async r=>{const j=await r.json(); if(r.status!==200||j.status!=="UP")process.exit(1); console.log(JSON.stringify(j));})
.catch(()=>process.exit(1));' >"${evidence}/spring-health.json" 2>/dev/null; do
  if (( SECONDS >= deadline )) || [[ "$(docker inspect "$backend_id" --format '{{.State.Running}}')" != true ]]; then
    docker logs "$backend_id" >"${evidence}/spring-boot.log" 2>&1
    fail 'real Spring451 did not become ready'
  fi
  sleep 1
done
docker logs "$backend_id" >"${evidence}/spring-boot.log" 2>&1
grep -Fq 'Started TopsDoJobBackendApplication' "${evidence}/spring-boot.log" || fail 'Spring startup marker missing'
db_fingerprint() {
  docker exec -i "$pg_id" psql -X -qAt --no-password -U origin451 -d origin451 --set=ON_ERROR_STOP=1 <<'DB_FINGERPRINT'
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s'; SET LOCAL lock_timeout='1s';
SELECT format('SELECT %L, count(*), md5(coalesce(string_agg(to_jsonb(t)::text, chr(10) ORDER BY to_jsonb(t)::text), '''')) FROM %I.%I t;', schemaname||'.'||tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename
\gexec
ROLLBACK;
DB_FINGERPRINT
}
db_fingerprint >"${evidence}/database.before"
docker inspect "$backend_id" >"${evidence}/runtime.before.json"
set +e
python3 "$observer" preflight "${evidence}/reports" "$origin" >"${evidence}/observer-negative.log" 2>&1
rc=$?
set -e
[[ "$rc" -eq 1 ]] || fail "observer exit $rc expected1"
grep -Fxq 'PREVIEW_TRANSITION=FAIL reason=source_graceful_configuration_unproven' "${evidence}/observer-negative.log" \
  || fail 'real observer did not reject the expected missing graceful configuration'
[[ ! -e "${evidence}/reports/preview-source.json" && ! -e "${evidence}/reports/preview-drained.json" ]] || fail 'negative created approval receipt'
db_fingerprint >"${evidence}/database.after"
cmp "${evidence}/database.before" "${evidence}/database.after" || fail 'database changed across preflight'
docker inspect "$backend_id" >"${evidence}/runtime.after.json"
python3 - "${evidence}/runtime.before.json" "${evidence}/runtime.after.json" <<'RUNTIME_UNCHANGED'
import json
from pathlib import Path
import sys
before, after = (json.loads(Path(p).read_text())[0] for p in sys.argv[1:])
for key in ('Id','Image','RestartCount','Config','Mounts'):
    assert before[key] == after[key], key
for key in ('Running','StartedAt','FinishedAt','ExitCode','OOMKilled','Paused','Restarting'):
    assert before['State'][key] == after['State'][key], key
assert after['State']['Running']
for entry in after['Config']['Env']:
    assert not entry.startswith(('SERVER_SHUTDOWN=', 'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=',
      'SPRING_TASK_EXECUTION_SHUTDOWN_', 'SPRING_TASK_SCHEDULING_SHUTDOWN_', 'JAVA_TOOL_OPTIONS=',
      'JDK_JAVA_OPTIONS=', 'SPRING_APPLICATION_JSON=')), entry.split('=',1)[0]
print('REAL_SPRING451_IDENTITY=PASS running=true missing_graceful=true runtime_unchanged=true')
RUNTIME_UNCHANGED
[[ -z "$(docker logs "$storage_id" 2>&1)" ]] || fail 'unexpected synthetic storage request/startup error'
sha256sum -c "${evidence}/input-identities.sha256" >"${evidence}/input-identities-after.log"
python3 - "$source_root" "${evidence}/source-before.sha256" <<'SOURCE_UNCHANGED'
import hashlib
from pathlib import Path
import sys
root=Path(sys.argv[1])
current=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+' '+p.relative_to(root).as_posix()+'\n'
  for p in sorted(root.rglob('*')) if p.is_file() and 'target' not in p.parts)
assert current == Path(sys.argv[2]).read_text(), 'archived source changed during build'
SOURCE_UNCHANGED
printf 'ORIGIN451_SPRING_NEGATIVE=PASS origin=%s jar_sha256=%s observer_exit=1 receipts=0 database_unchanged=true storage_requests=0 scope=real_spring_real_observer_no_coordinator_success_claim\n' \
  "$origin" "$(sha256sum "$jar" | cut -d' ' -f1)"
