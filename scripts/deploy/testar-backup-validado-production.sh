#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
backup_producer="${script_dir}/criar-backup-validado-production.sh"
wait_for_ephemeral_postgres="${script_dir}/aguardar-postgres-efemero.sh"
suffix="$(printf '%s-%s' "$$" "${RANDOM}" | tr -cd 'a-zA-Z0-9-')"
source_container="topsv3-backup-source-test-${suffix}"
source_volume="${source_container}-data"
postgres_image="postgres:17.10-alpine"
temp_dir="$(mktemp -d)"
backup_dir="${temp_dir}/backups"
receipt_file="${temp_dir}/backup.receipt"
fixture_sql="${temp_dir}/fixture.sql"
real_docker="$(command -v docker)"
test_owner_id="backup-test-${suffix}"
resources="${temp_dir}/resources"
sentinel_container="topsv3-backup-restore-sentinel-${suffix}"
sentinel_volume="${sentinel_container}-data"
touch "$resources"

fail() { echo "FALHA: $*" >&2; exit 1; }
die() { echo "ERRO_BACKUP_PRODUCTION: $*" >&2; exit 1; }

# Exercise the same shared waiter invoked by the producer, with its 90s budget.
grep -Fq 'POSTGRES_EFEMERO_TIMEOUT_SECONDS=90 bash "$wait_for_ephemeral_postgres"' "$backup_producer" || fail 'caller nao fixa prazo real'

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
  local kind identifier owner actual
  # Reverse creation order; never enumerate/delete resources by a global prefix.
  while IFS='|' read -r kind identifier owner; do
    if [[ "$kind" == container ]]; then
      if actual="$("$real_docker" inspect --format '{{index .Config.Labels "topsv3.backup.owner"}}' "$identifier" 2>/dev/null)"; then
        if [[ "$actual" == "$owner" ]]; then
          if ! "$real_docker" rm -f "$identifier" >/dev/null; then
            echo "FALHA_CLEANUP_TESTE: container proprio ${identifier}" >&2
            [[ "$rc" -ne 0 ]] || rc=1
          fi
        else
          echo "FALHA_CLEANUP_TESTE: ownership divergente; container preservado" >&2
          [[ "$rc" -ne 0 ]] || rc=1
        fi
      fi
    elif [[ "$kind" == volume ]]; then
      if actual="$("$real_docker" volume inspect --format '{{index .Labels "topsv3.backup.owner"}}' "$identifier" 2>/dev/null)"; then
        if [[ "$actual" == "$owner" ]]; then
          if ! "$real_docker" volume rm "$identifier" >/dev/null; then
            echo "FALHA_CLEANUP_TESTE: volume proprio ${identifier}" >&2
            [[ "$rc" -ne 0 ]] || rc=1
          fi
        else
          echo "FALHA_CLEANUP_TESTE: ownership divergente; volume preservado" >&2
          [[ "$rc" -ne 0 ]] || rc=1
        fi
      fi
    fi
  done < <(tac "$resources")
  if ! rm -rf -- "$temp_dir"; then
    echo "FALHA_CLEANUP_TESTE: diretorio temporario proprio ${temp_dir}" >&2
    [[ "$rc" -ne 0 ]] || rc=1
  fi
  exit "$rc"
}
trap cleanup EXIT

printf 'volume|%s|%s\n' "$source_volume" "$test_owner_id" >> "$resources"
docker volume create --label "topsv3.backup.owner=${test_owner_id}" "$source_volume" >/dev/null
printf 'container|%s|%s\n' "$source_container" "$test_owner_id" >> "$resources"
source_id="$(docker run -d \
  --name "$source_container" \
  --label "topsv3.backup.owner=${test_owner_id}" \
  --network none \
  --volume "${source_volume}:/var/lib/postgresql/data" \
  --env POSTGRES_DB=topsv3_backup_fixture \
  --env POSTGRES_USER=postgres \
  --env POSTGRES_HOST_AUTH_METHOD=trust \
  "$postgres_image")"

POSTGRES_EFEMERO_TIMEOUT_SECONDS=90 bash "$wait_for_ephemeral_postgres" "$source_id" postgres topsv3_backup_fixture

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
INSERT INTO usuario (id) VALUES (17);
INSERT INTO anuncio (id) VALUES (23);
SQL
docker exec -i "$source_container" psql \
  --no-psqlrc --set=ON_ERROR_STOP=1 --no-password --host=127.0.0.1 --port=5432 \
  -U postgres -d topsv3_backup_fixture < "$fixture_sql"

# These sentinels belong to this outer test, not to any backup-producer run.
printf 'volume|%s|%s\n' "$sentinel_volume" "$test_owner_id" >> "$resources"
docker volume create --label "topsv3.backup.owner=${test_owner_id}" "$sentinel_volume" >/dev/null
printf 'container|%s|%s\n' "$sentinel_container" "$test_owner_id" >> "$resources"
sentinel_id="$(docker create --name "$sentinel_container" --network none \
  --label "topsv3.backup.owner=${test_owner_id}" --volume "${sentinel_volume}:/var/lib/postgresql/data" \
  --entrypoint /bin/true "$postgres_image")"

# Controlled fault injection only. All other commands, including dump-list,
# authenticated SQL and the successful restore, use the real Docker/PostgreSQL.
mkdir -p "${temp_dir}/fault-bin"
cat > "${temp_dir}/fault-bin/docker" <<'DOCKER_SHIM'
#!/usr/bin/env bash
set -euo pipefail
args=("$@")
tool=''
owner=''
for arg in "$@"; do
  case "$arg" in psql|pg_isready|pg_dump|pg_restore|createdb) tool="$arg" ;; esac
  [[ "$arg" != topsv3.backup.owner=* ]] || owner="${arg#topsv3.backup.owner=}"
done
event() { printf '%s\n' "$1" >> "${BACKUP_TEST_STATE}/calls"; }
tcp_contract() {
  [[ " $* " == *' --host=127.0.0.1 '* && " $* " == *' --port=5432 '* \
    && " $* " == *' --username=postgres '* ]] || { echo 'TCP_CONTRACT_MISSING' >&2; exit 66; }
}
if [[ "${1:-}" == logs && "$BACKUP_TEST_MODE" == marker_delay ]]; then
  event logs_attempt
  if (( $(grep -c '^logs_attempt$' "${BACKUP_TEST_STATE}/calls") <= 2 )); then
    printf 'database system is ready to accept connections\n'
    exit 0
  fi
  "$BACKUP_TEST_DOCKER" "$@"
  event final_marker_observed
  exit 0
fi
if [[ "${1:-}" == exec ]]; then
  if [[ "$tool" == psql && " $* " == *' SELECT 1 '* ]]; then
    tcp_contract "$@"
    [[ " $* " == *' PGCONNECT_TIMEOUT=2 '* && " $* " == *' statement_timeout=2000 '* \
      && " $* " == *' --no-password '* && " $* " != *' -i '* ]] \
      || { echo 'SQL_TIMEOUT_OR_STDIN_CONTRACT_MISSING' >&2; exit 66; }
    event sql_attempt
    if [[ "$BACKUP_TEST_MODE" == marker_delay ]]; then
      grep -qx final_marker_observed "${BACKUP_TEST_STATE}/calls" || { echo 'SQL_BEFORE_FINAL_MARKER' >&2; exit 70; }
    fi
    if [[ "$BACKUP_TEST_MODE" == final_probe_deadline ]] && (( $(grep -c '^sql_attempt$' "${BACKUP_TEST_STATE}/calls") >= 3 )); then
      sleep 90
      event forbidden_late_probe_completed
    fi
    if [[ "$BACKUP_TEST_MODE" == transient ]] && (( $(grep -c '^sql_attempt$' "${BACKUP_TEST_STATE}/calls") <= 3 )); then
      echo 'SQL_TCP_NOT_READY_SYNTHETIC: socket-only initialization phase' >&2
      exit 2
    fi
    for index in "${!args[@]}"; do
      if [[ "$BACKUP_TEST_MODE" == auth && "${args[$index]}" == --username=postgres ]]; then
        args[$index]='--username=backup_test_missing_role'
      elif [[ "$BACKUP_TEST_MODE" == deadline && "${args[$index]}" == --port=5432 ]]; then
        args[$index]='--port=1'
      fi
    done
    if output="$("$BACKUP_TEST_DOCKER" "${args[@]}")"; then
      [[ "$output" != 1 ]] || event sql_ready
      printf '%s\n' "$output"
      exit 0
    else
      exit "$?"
    fi
  fi
  if [[ "$tool" == pg_isready && "$BACKUP_TEST_MODE" == transient ]]; then
    echo 'socket: accepting connections (controlled negative condition)'
    exit 0
  fi
  if [[ "$tool" == pg_dump && "$BACKUP_TEST_MODE" == invalid_dump && " $* " != *' --version '* ]]; then
    printf 'INVALID_SYNTHETIC_DUMP\n'
    exit 0
  fi
  if [[ "$tool" == createdb ]]; then echo 'CREATEDB_FORBIDDEN' >&2; exit 69; fi
  if [[ "$tool" == pg_restore && " $* " != *' --list '* ]]; then
    tcp_contract "$@"
    grep -qx sql_ready "${BACKUP_TEST_STATE}/calls" || { echo 'MUTATION_BEFORE_SQL_READY' >&2; exit 67; }
    event "$tool"
    if [[ "$BACKUP_TEST_MODE" == restore_failure && "$tool" == pg_restore ]]; then
      echo 'ORIGINAL_RESTORE_FAILURE_43' >&2
      exit 43
    fi
    "$BACKUP_TEST_DOCKER" "$@"
    if [[ "$tool" == pg_restore ]]; then
      restored_id="$(cat "${BACKUP_TEST_STATE}/restore-id")"
      restored="$("$BACKUP_TEST_DOCKER" exec "$restored_id" psql --no-psqlrc --set=ON_ERROR_STOP=1 \
        --no-password --host=127.0.0.1 --port=5432 --username=postgres --dbname=restore_validation \
        -At --command "SELECT (SELECT id FROM usuario)::text || '|' || (SELECT id FROM anuncio)::text" </dev/null)"
      [[ "$restored" == '17|23' ]] || { echo 'RESTORED_CONTENT_MISMATCH' >&2; exit 68; }
      event restored_rows_17_23
    fi
    exit 0
  fi
fi
if [[ -n "$owner" && ( "${1:-}" == run || ( "${1:-}" == volume && "${2:-}" == create ) ) ]]; then
  output="$("$BACKUP_TEST_DOCKER" "$@")"
  if [[ "${1:-}" == run ]]; then
    kind=container
    printf '%s\n' "$output" > "${BACKUP_TEST_STATE}/restore-id"
  else
    kind=volume
  fi
  printf '%s|%s|%s\n' "$kind" "$output" "$owner" >> "$BACKUP_TEST_RESOURCES"
  printf '%s|%s|%s\n' "$kind" "$output" "$owner" >> "${BACKUP_TEST_STATE}/resources"
  printf '%s\n' "$output"
  exit 0
fi
exec "$BACKUP_TEST_DOCKER" "$@"
DOCKER_SHIM
chmod +x "${temp_dir}/fault-bin/docker"

assert_cleanup() {
  local kind identifier owner
  while IFS='|' read -r kind identifier owner; do
    if [[ "$kind" == container ]]; then
      if "$real_docker" inspect "$identifier" >/dev/null 2>&1; then fail "container proprio permaneceu: ${identifier}"; fi
    else
      if "$real_docker" volume inspect "$identifier" >/dev/null 2>&1; then fail "volume proprio permaneceu: ${identifier}"; fi
    fi
  done < "${case_dir}/resources"
  [[ "$("$real_docker" inspect --format '{{.Id}}' "$sentinel_container")" == "$sentinel_id" ]] || fail 'sentinela alheia removida'
  [[ "$("$real_docker" volume inspect --format '{{index .Labels "topsv3.backup.owner"}}' "$sentinel_volume")" == "$test_owner_id" ]] || fail 'volume sentinela alterado'
  [[ -z "$(find "$case_dir" -name '*.partial' -print -quit)" ]] || fail 'arquivo parcial permaneceu'
}

run_case() {
  local name="$1" mode="$2" expected_rc="$3" expected_message="$4" runner="${5:-producer}"
  local rc started elapsed
  local -a case_command
  case_dir="${temp_dir}/${name}"
  mkdir -p "$case_dir"
  touch "${case_dir}/calls" "${case_dir}/resources"
  backup_dir="${case_dir}/backups"
  receipt_file="${case_dir}/backup.receipt"
  if [[ "$runner" == readiness ]]; then
    case_command=(env POSTGRES_EFEMERO_TIMEOUT_SECONDS=90 bash "$wait_for_ephemeral_postgres" "$source_id" postgres topsv3_backup_fixture)
  else
    case_command=(bash "$backup_producer" "$source_container" "$backup_dir" 1111111111111111111111111111111111111111 "$receipt_file")
  fi
  if [[ "$mode" == clock_shift ]]; then
    # Only the child fixture changes Bash's wall-clock-derived variable.
    # No host clock/configuration is modified.
    printf '%s\n' 'SECONDS=0' "trap 'SECONDS=\$((SECONDS + 1000000))' DEBUG" 'set -T' \
      > "${case_dir}/seconds-jump.bash"
    case_command=(env "BASH_ENV=${case_dir}/seconds-jump.bash" "${case_command[@]}")
  fi
  started=$SECONDS
  if PATH="${temp_dir}/fault-bin:${PATH}" BACKUP_TEST_MODE="$mode" BACKUP_TEST_STATE="$case_dir" \
    BACKUP_TEST_DOCKER="$real_docker" BACKUP_TEST_SOURCE="$source_id" BACKUP_TEST_RESOURCES="$resources" \
    timeout --kill-after=2s 240s "${case_command[@]}" > "${case_dir}/result.log" 2>&1; then rc=0; else rc=$?; fi
  elapsed=$((SECONDS - started))
  if [[ "$rc" -ne "$expected_rc" ]] || ! grep -Fq -- "$expected_message" "${case_dir}/result.log"; then
    cat "${case_dir}/result.log" >&2
    fail "${name}: exit=${rc}, esperado=${expected_rc}"
  fi
  if [[ "$mode" == auth || "$mode" == deadline ]]; then
    (( elapsed >= 88 && elapsed <= 95 )) || fail "${name}: deadline real divergente (${elapsed}s)"
    (( $(grep -c '^sql_attempt$' "${case_dir}/calls") <= 90 )) || fail 'tentativas ilimitadas'
    if grep -Eq '^(sql_ready|createdb|pg_restore)$' "${case_dir}/calls"; then fail "${name}: prontidao falsa ou mutacao"; fi
    if grep -q READINESS_SQL_PASS "${case_dir}/result.log"; then fail "${name}: falso sucesso SQL"; fi
  elif [[ "$mode" == transient ]]; then
    [[ "$(grep -c '^sql_attempt$' "${case_dir}/calls")" -eq 6 ]] || fail 'fase transitoria e tres provas nao foram aguardadas'
    grep -qx sql_ready "${case_dir}/calls" || fail 'SQL autenticado ausente'
  elif [[ "$mode" == marker_delay ]]; then
    [[ "$(grep -c '^logs_attempt$' "${case_dir}/calls")" == 3 && "$(grep -c '^sql_ready$' "${case_dir}/calls")" == 3 ]] || fail 'marcador final ou tres provas nao preservados'
  elif [[ "$mode" == final_probe_deadline ]]; then
    (( elapsed >= 88 && elapsed <= 95 )) || fail "ultima prova ultrapassou deadline (${elapsed}s)"
    [[ "$(grep -c '^sql_ready$' "${case_dir}/calls")" == 2 ]] || fail 'ultima prova incompleta foi aceita'
    if grep -qx forbidden_late_probe_completed "${case_dir}/calls"; then fail 'subcomando nao foi limitado ao saldo'; fi
  fi
  if [[ "$expected_rc" -ne 0 ]]; then [[ ! -e "$receipt_file" ]] || fail 'falha publicou receipt'; fi
  assert_cleanup
  printf 'PASS: %s exit=%s elapsed=%ss\n' "$name" "$rc" "$elapsed"
  if [[ "$expected_rc" -ne 0 ]]; then tail -n 3 "${case_dir}/result.log"; fi
}

run_case backup_restaurado_flyway_051 normal 0 BACKUP_VALIDATION=PASS
grep -qx restored_rows_17_23 "${case_dir}/calls" || fail 'conteudo sintetico nao verificado no destino'

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

run_case inicializacao_transitoria transient 0 'POSTGRES_EFEMERO_READY=PASS probes=3' readiness
run_case marcador_final_antes_sql marker_delay 0 'POSTGRES_EFEMERO_READY=PASS probes=3' readiness
run_case relogio_bash_nao_altera_deadline clock_shift 0 'POSTGRES_EFEMERO_READY=PASS probes=3' readiness
# pg_isready accepts a connection even when that role cannot authenticate SQL.
docker exec "$source_id" pg_isready --host=127.0.0.1 --port=5432 --username=backup_test_missing_role --dbname=postgres >/dev/null
run_case autenticacao_real_recusada auth 1 'role "backup_test_missing_role" does not exist' readiness
run_case deadline_real deadline 1 'PostgreSQL efemero nao ficou pronto via TCP e SQL em 90s' readiness
run_case ultima_prova_dentro_deadline final_probe_deadline 1 'PostgreSQL efemero nao ficou pronto via TCP e SQL em 90s' readiness
run_case dump_invalido invalid_dump 1 'pg_restore: error:'
[[ ! -s "${case_dir}/resources" ]] || fail 'dump invalido criou destino antes de validar arquivo'
run_case restore_sem_retry restore_failure 43 ORIGINAL_RESTORE_FAILURE_43
[[ "$(grep -c '^pg_restore$' "${case_dir}/calls")" -eq 1 ]] || fail 'mutacao parcialmente executada foi repetida'
if grep -qx createdb "${case_dir}/calls"; then fail 'createdb nao pertence ao contrato entrypoint'; fi
[[ "$(docker exec "$source_id" psql --no-psqlrc --set=ON_ERROR_STOP=1 --no-password \
  --host=127.0.0.1 --port=5432 --username=postgres --dbname=topsv3_backup_fixture \
  -At --command "SELECT (SELECT id FROM usuario)::text || '|' || (SELECT id FROM anuncio)::text" </dev/null)" == '17|23' ]] || fail 'origem sintetica alterada'

# The entrypoint owns database creation; an existing receipt cannot be replaced.
receipt_before="$(sha256sum "${temp_dir}/backup_restaurado_flyway_051/backup.receipt")"
if bash "$backup_producer" "$source_container" "${temp_dir}/backup_restaurado_flyway_051/backups" \
  1111111111111111111111111111111111111111 "${temp_dir}/backup_restaurado_flyway_051/backup.receipt" \
  > "${temp_dir}/collision.log" 2>&1; then fail 'receipt existente foi sobrescrito'; fi
[[ "$(sha256sum "${temp_dir}/backup_restaurado_flyway_051/backup.receipt")" == "$receipt_before" ]] || fail 'receipt anterior alterado'
echo 'PASS: receipt_existente_preservado'
docker stop "$source_container" >/dev/null
if POSTGRES_EFEMERO_MAX_ATTEMPTS=3 bash "$wait_for_ephemeral_postgres" "$source_container" postgres topsv3_backup_fixture \
  > "${temp_dir}/stopped.log" 2>&1; then fail 'PostgreSQL parado aceito'; fi
echo 'PASS: falha_real_postgres_bloqueia'

echo "BACKUP_RESTORE_INTEGRATION_TEST=PASS"
