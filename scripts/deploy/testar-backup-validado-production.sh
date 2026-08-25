#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
backup_producer="${script_dir}/criar-backup-validado-production.sh"
suffix="$(printf '%s-%s' "$$" "${RANDOM}" | tr -cd 'a-zA-Z0-9-')"
source_container="topsv3-backup-source-test-${suffix}"
source_volume="${source_container}-data"
postgres_image="postgres:17.10-alpine"
temp_dir="$(mktemp -d)"
backup_dir="${temp_dir}/backups"
receipt_file="${temp_dir}/backup.receipt"
fixture_sql="${temp_dir}/fixture.sql"
source_started=0
volume_created=0

if [[ "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
  mkdir -p "${temp_dir}/bin"
  cat > "${temp_dir}/bin/install" <<'INSTALL_SHIM'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == -d && "${2:-}" == -m ]]; then
  mode="$3"
  shift 3
  mkdir -p -- "$@"
  chmod "$mode" -- "$@"
  exit 0
fi
exec /usr/bin/install "$@"
INSTALL_SHIM
  chmod +x "${temp_dir}/bin/install"
  export PATH="${temp_dir}/bin:${PATH}"
fi

cleanup() {
  local rc=$?
  if [[ "$source_started" -eq 1 ]]; then
    docker rm -f "$source_container" >/dev/null 2>&1 || true
  fi
  if [[ "$volume_created" -eq 1 ]]; then
    docker volume rm "$source_volume" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$temp_dir"
  exit "$rc"
}
trap cleanup EXIT

docker volume create "$source_volume" >/dev/null
volume_created=1
docker run -d \
  --name "$source_container" \
  --network none \
  --volume "${source_volume}:/var/lib/postgresql/data" \
  --env POSTGRES_DB=topsv3_backup_fixture \
  --env POSTGRES_USER=postgres \
  --env POSTGRES_HOST_AUTH_METHOD=trust \
  "$postgres_image" >/dev/null
source_started=1

ready=0
for attempt in $(seq 1 90); do
  if docker exec "$source_container" pg_isready -U postgres -d topsv3_backup_fixture \
    >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
[[ "$ready" -eq 1 ]] || { echo "FALHA: PostgreSQL de origem nao ficou pronto" >&2; exit 1; }

cat > "$fixture_sql" <<'SQL'
CREATE TABLE usuario (id bigint PRIMARY KEY);
CREATE TABLE anuncio (id bigint PRIMARY KEY);
CREATE TABLE arquivo_midia (id bigint PRIMARY KEY);
CREATE TABLE documento_usuario (id bigint PRIMARY KEY);
CREATE TABLE ativacao_beneficio (id bigint PRIMARY KEY);
CREATE TABLE movimento_credito (id bigint PRIMARY KEY);
CREATE TABLE documento_busca_anuncio (id bigint PRIMARY KEY);
CREATE TABLE flyway_schema_history (
  installed_rank integer PRIMARY KEY,
  version varchar(50),
  success boolean NOT NULL
);
INSERT INTO flyway_schema_history (installed_rank, version, success)
VALUES (1, '051', true);
SQL
docker exec -i "$source_container" psql \
  --no-psqlrc --set=ON_ERROR_STOP=1 \
  -U postgres -d topsv3_backup_fixture < "$fixture_sql"

bash "$backup_producer" \
  "$source_container" \
  "$backup_dir" \
  1111111111111111111111111111111111111111 \
  "$receipt_file"

grep -qx 'BACKUP_STATUS=VALIDATED' "$receipt_file"
flyway_version="$(sed -n 's/^BACKUP_FLYWAY_VERSION=//p' "$receipt_file")"
[[ "$flyway_version" =~ ^[0-9]+$ ]]
printf -v flyway_normalized '%03d' "$((10#${flyway_version}))"
[[ "$flyway_normalized" == 051 ]]
backup_path="$(sed -n 's/^BACKUP_PATH=//p' "$receipt_file")"
[[ -n "$backup_path" && -s "$backup_path" ]]
checksum_path="${backup_path}.sha256"
[[ -s "$checksum_path" ]]
(
  cd -- "$(dirname -- "$backup_path")"
  sha256sum --check "$(basename -- "$checksum_path")" >/dev/null
)
docker exec -i "$source_container" pg_restore --list < "$backup_path" >/dev/null
echo "PASS: backup_restaurado_flyway_051"

if docker ps -a --format '{{.Names}}' | grep -q '^topsv3-backup-restore-'; then
  echo "FALHA: container efemero de restauracao permaneceu" >&2
  exit 1
fi
if docker volume ls --format '{{.Name}}' | grep -q '^topsv3-backup-restore-'; then
  echo "FALHA: volume efemero de restauracao permaneceu" >&2
  exit 1
fi

echo "BACKUP_RESTORE_INTEGRATION_TEST=PASS"
