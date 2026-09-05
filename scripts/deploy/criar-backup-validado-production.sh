#!/usr/bin/env bash
set -euo pipefail

die() {
  echo "ERRO_BACKUP_PRODUCTION: $*" >&2
  exit 1
}

if [[ $# -ne 4 ]]; then
  die "uso: $0 POSTGRES_CONTAINER BACKUP_DIR PRODUCTION_SHA RECEIPT_FILE"
fi

postgres_container="$1"
backup_dir="$2"
production_sha="$3"
receipt_file="$4"

[[ "$postgres_container" =~ ^[A-Za-z0-9_.-]+$ ]] || die "container PostgreSQL invalido"
[[ "$production_sha" =~ ^[0-9a-f]{40}$ ]] || die "SHA de producao invalido"
[[ "$backup_dir" == /* ]] || die "diretorio de backup deve ser absoluto"
[[ "$receipt_file" == /* ]] || die "receipt deve ser absoluto"
case "$backup_dir" in
  */releases|*/releases/*|*/.git|*/.git/*) die "backup nao pode ficar em release ou Git" ;;
esac

docker inspect "$postgres_container" >/dev/null
[[ "$(docker inspect --format '{{.State.Running}}' "$postgres_container")" == true ]] \
  || die "PostgreSQL nao esta em execucao"

postgres_image="$(docker inspect --format '{{.Config.Image}}' "$postgres_container")"
docker image inspect "$postgres_image" >/dev/null
database="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$postgres_container" \
  | sed -n 's/^POSTGRES_DB=//p' | head -n 1)"
database_user="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$postgres_container" \
  | sed -n 's/^POSTGRES_USER=//p' | head -n 1)"
[[ -n "$database" && -n "$database_user" ]] || die "identidade local do PostgreSQL indisponivel"

server_major="$(docker exec "$postgres_container" postgres --version | sed -E 's/.* ([0-9]+)\..*/\1/')"
dump_major="$(docker exec "$postgres_container" pg_dump --version | sed -E 's/.* ([0-9]+)\..*/\1/')"
[[ "$server_major" =~ ^[0-9]+$ && "$dump_major" =~ ^[0-9]+$ ]] \
  || die "nao foi possivel identificar a major do PostgreSQL"
[[ "$server_major" == "$dump_major" ]] || die "pg_dump e servidor possuem majors diferentes"

install -d -m 700 "$backup_dir"
chmod 700 "$backup_dir"
receipt_dir="$(dirname "$receipt_file")"
[[ -d "$receipt_dir" && -w "$receipt_dir" ]] || die "diretorio do receipt indisponivel"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${backup_dir}/topsv3-production-${timestamp}-${production_sha}.dump"
backup_partial="${backup_file}.partial"
checksum_file="${backup_file}.sha256"
checksum_partial="${checksum_file}.partial"
receipt_partial="${receipt_file}.partial"
for output_file in "$backup_file" "$backup_partial" "$checksum_file" "$checksum_partial" "$receipt_file" "$receipt_partial"; do
  [[ ! -e "$output_file" && ! -L "$output_file" ]] || die "arquivo de backup ou receipt ja existe"
done
temporary_dir="$(mktemp -d)"
source_metrics="${temporary_dir}/source.metrics"
restored_metrics="${temporary_dir}/restored.metrics"
validation_sql="${temporary_dir}/validation.sql"
readiness_error="${temporary_dir}/readiness.error"
restore_token="${temporary_dir##*/}"
restore_container="topsv3-backup-restore-${restore_token,,}"
restore_volume="${restore_container}-data"
restore_cid_file="${temporary_dir}/restore.cid"
restore_cid_argument="$restore_cid_file"
if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
  restore_cid_argument="$(cygpath -m "$restore_cid_file")"
fi
restore_id=''
restore_started=0
volume_created=0

cleanup() {
  local rc=$?
  local cleanup_failed=0 owner='' resource_identity=''
  trap - EXIT
  if [[ "$restore_started" -eq 1 ]]; then
    if [[ -s "$restore_cid_file" ]]; then
      if ! restore_id="$(cat "$restore_cid_file")"; then
        echo 'ERRO_BACKUP_CLEANUP: nao foi possivel ler a identidade temporaria' >&2
        cleanup_failed=1
      fi
    fi
    if resource_identity="$(docker inspect --format '{{.Id}}|{{index .Config.Labels "topsv3.backup.owner"}}' "${restore_id:-$restore_container}")"; then
      IFS='|' read -r restore_id owner <<< "$resource_identity"
      if [[ "$restore_id" =~ ^[0-9a-f]{64}$ && "$owner" == "$restore_token" ]]; then
        if ! docker rm -f "$restore_id" >/dev/null; then
          echo 'ERRO_BACKUP_CLEANUP: nao foi possivel remover o container temporario proprio' >&2
          cleanup_failed=1
        fi
      else
        echo 'ERRO_BACKUP_CLEANUP: container nao pertence a esta execucao; preservado' >&2
        cleanup_failed=1
      fi
    else
      echo 'ERRO_BACKUP_CLEANUP: identidade do container temporario nao confirmada; remocao nao executada' >&2
      cleanup_failed=1
    fi
  fi
  if [[ "$volume_created" -eq 1 ]]; then
    if owner="$(docker volume inspect --format '{{index .Labels "topsv3.backup.owner"}}' "$restore_volume")" \
      && [[ "$owner" == "$restore_token" ]]; then
      if ! docker volume rm "$restore_volume" >/dev/null; then
        echo 'ERRO_BACKUP_CLEANUP: nao foi possivel remover o volume temporario proprio' >&2
        cleanup_failed=1
      fi
    else
      echo 'ERRO_BACKUP_CLEANUP: identidade do volume temporario nao confirmada; preservado' >&2
      cleanup_failed=1
    fi
  fi
  if ! rm -rf -- "$temporary_dir"; then
    echo 'ERRO_BACKUP_CLEANUP: nao foi possivel remover o diretorio temporario proprio' >&2
    cleanup_failed=1
  fi
  if ! rm -f -- "$backup_partial" "$checksum_partial" "$receipt_partial"; then
    echo 'ERRO_BACKUP_CLEANUP: nao foi possivel remover os arquivos parciais proprios' >&2
    cleanup_failed=1
  fi
  if [[ "$rc" -eq 0 && "$cleanup_failed" -ne 0 ]]; then
    rc=1
  fi
  exit "$rc"
}
trap cleanup EXIT

cat > "$validation_sql" <<'SQL'
SELECT 'PUBLIC_TABLES', count(*)::text
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
SELECT 'FLYWAY_SUCCESS', count(*)::text FROM flyway_schema_history WHERE success;
SELECT 'FLYWAY_FAILED', count(*)::text FROM flyway_schema_history WHERE NOT success;
SELECT 'FLYWAY_MAX', COALESCE(max(version::integer)::text, 'AUSENTE')
FROM flyway_schema_history
WHERE success AND version ~ '^[0-9]+$';
SELECT 'ESSENTIAL_TABLES', count(*)::text
FROM (VALUES
  ('usuario'), ('anuncio'), ('arquivo_midia'), ('documento_usuario'),
  ('ativacao_beneficio'), ('movimento_credito'), ('flyway_schema_history'),
  ('documento_busca_anuncio')
) AS expected(name)
WHERE to_regclass('public.' || expected.name) IS NOT NULL;
SQL

docker exec -i "$postgres_container" psql -X -v ON_ERROR_STOP=1 \
  -U "$database_user" -d "$database" -At -F '|' < "$validation_sql" \
  | LC_ALL=C sort > "$source_metrics"
grep -qx 'FLYWAY_FAILED|0' "$source_metrics" || die "historico Flyway da origem possui falha"
grep -qx 'ESSENTIAL_TABLES|8' "$source_metrics" || die "tabelas essenciais ausentes na origem"

umask 077
docker exec "$postgres_container" pg_dump \
  --format=custom \
  --compress=6 \
  --no-owner \
  --no-acl \
  --serializable-deferrable \
  --no-password \
  -U "$database_user" \
  -d "$database" > "$backup_partial"
[[ -s "$backup_partial" ]] || die "pg_dump gerou arquivo vazio"
chmod 600 "$backup_partial"
docker exec -i "$postgres_container" pg_restore --list < "$backup_partial" >/dev/null

docker volume create --label "topsv3.backup.owner=${restore_token}" "$restore_volume" >/dev/null
volume_created=1
[[ "$(docker volume inspect --format '{{index .Labels "topsv3.backup.owner"}}' "$restore_volume")" == "$restore_token" ]] \
  || die "volume temporario nao pertence a esta execucao"
restore_started=1
docker run --pull never -d \
  --name "$restore_container" \
  --cidfile "$restore_cid_argument" \
  --label "topsv3.backup.owner=${restore_token}" \
  --network none \
  --volume "${restore_volume}:/var/lib/postgresql/data" \
  --env POSTGRES_HOST_AUTH_METHOD=trust \
  "$postgres_image" >/dev/null
restore_id="$(cat "$restore_cid_file")"
[[ "$restore_id" =~ ^[0-9a-f]{64}$ ]] || die "identidade do container temporario invalida"

# The image's initialization server accepts Unix sockets before its normal
# shutdown. Only the final server accepts this TCP connection and SQL query.
# BEGIN RESTORE_POSTGRES_READINESS
ready=0
ready_deadline=$((SECONDS + 90))
for attempt in $(seq 1 90); do
  remaining=$((ready_deadline - SECONDS))
  (( remaining > 1 )) || break
  attempt_timeout=$((remaining - 1))
  if (( attempt_timeout > 5 )); then
    attempt_timeout=5
  fi
  if ready_output="$(timeout --kill-after=1s "${attempt_timeout}s" \
    docker exec --env PGCONNECT_TIMEOUT=2 --env 'PGOPTIONS=-c statement_timeout=2000' \
      "$restore_id" psql --no-psqlrc --set=ON_ERROR_STOP=1 --no-password \
      --host=127.0.0.1 --port=5432 --username=postgres --dbname=postgres \
      -At --command 'SELECT 1' </dev/null 2> "$readiness_error")" \
    && [[ "$ready_output" == 1 ]]; then
    ready=1
    break
  fi
  (( SECONDS < ready_deadline )) || break
  sleep 1
done
if [[ "$ready" -ne 1 ]]; then
  if [[ -s "$readiness_error" ]]; then
    cat "$readiness_error" >&2
  fi
  die "PostgreSQL efemero nao ficou pronto via TCP e SQL em 90s"
fi
# END RESTORE_POSTGRES_READINESS

docker exec "$restore_id" createdb \
  --host=127.0.0.1 --port=5432 --username=postgres --maintenance-db=postgres \
  --no-password restore_validation </dev/null
docker exec -i "$restore_id" pg_restore \
  --host=127.0.0.1 --port=5432 --username=postgres \
  --dbname=restore_validation --no-password \
  --no-owner \
  --no-acl \
  --exit-on-error < "$backup_partial"
docker exec -i "$restore_id" psql -X -v ON_ERROR_STOP=1 --no-password \
  --host=127.0.0.1 --port=5432 --username=postgres --dbname=restore_validation \
  -At -F '|' < "$validation_sql" \
  | LC_ALL=C sort > "$restored_metrics"
cmp -s "$source_metrics" "$restored_metrics" || die "schema restaurado diverge da origem"

docker rm -f "$restore_id" >/dev/null
restore_started=0
docker volume rm "$restore_volume" >/dev/null
volume_created=0

backup_sha256="$(sha256sum "$backup_partial" | cut -d' ' -f1)"
backup_bytes="$(stat -c %s "$backup_partial")"
flyway_version="$(awk -F'|' '$1 == "FLYWAY_MAX" { print $2 }' "$restored_metrics")"
[[ "$backup_sha256" =~ ^[0-9a-f]{64}$ ]] || die "SHA-256 do backup invalido"
[[ "$backup_bytes" -gt 0 ]] || die "backup sem bytes"

mv -T "$backup_partial" "$backup_file"
printf '%s  %s\n' "$backup_sha256" "$(basename "$backup_file")" > "$checksum_partial"
chmod 600 "$backup_file" "$checksum_partial"
mv -T "$checksum_partial" "$checksum_file"

cat > "$receipt_partial" <<EOF
BACKUP_STATUS=VALIDATED
BACKUP_PATH=${backup_file}
BACKUP_BYTES=${backup_bytes}
BACKUP_SHA256=${backup_sha256}
BACKUP_SERVER_MAJOR=${server_major}
BACKUP_FLYWAY_VERSION=${flyway_version}
EOF
chmod 600 "$receipt_partial"
mv -T "$receipt_partial" "$receipt_file"

trap - EXIT
rm -rf -- "$temporary_dir"
printf 'BACKUP_VALIDATION=PASS\n'
printf 'BACKUP_BYTES=%s\n' "$backup_bytes"
printf 'BACKUP_SHA256=%s\n' "$backup_sha256"
printf 'BACKUP_FLYWAY_VERSION=%s\n' "$flyway_version"
