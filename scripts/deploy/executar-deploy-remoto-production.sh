#!/usr/bin/env bash
set -euo pipefail

DEPLOY_ROOT=/opt/topsv3/production
SECRETS_ROOT=/opt/topsv3/secrets

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  return 1
}

validate_release_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || fail "SHA de release invalido"
}

validate_sha256() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]] || fail "SHA-256 invalido"
}

assert_stdin_closed() {
  local unexpected
  if [ -t 0 ]; then
    fail "stdin interativo proibido no deploy remoto"
    return 1
  fi
  if IFS= read -r -t 0.05 unexpected; then
    fail "deploy remoto recebeu dados pelo stdin"
    return 1
  fi
}

validate_prerequisites() {
  [ "$#" -eq 0 ] || fail "prerequisites nao recebe argumentos"
  test "$(id -un)" = "topsdojob"
  test -r "${SECRETS_ROOT}/production.env"
  test -r "${SECRETS_ROOT}/application-production.yml"
  test -r "${SECRETS_ROOT}/nginx-production-local.conf"
  test -w /etc/nginx/sites-enabled/topsdojob-live
  test -r "${SECRETS_ROOT}/efi-webhook-allowlist.conf"
  test -d "${SECRETS_ROOT}/efi"
  test -L "${DEPLOY_ROOT}/current"
  test -w "${DEPLOY_ROOT}/incoming"
  test -w "${DEPLOY_ROOT}/releases"
  command -v docker >/dev/null
  docker compose version >/dev/null
  docker image inspect nginx:1.27-alpine >/dev/null
  docker inspect topsv3-production-postgres >/dev/null
  test "$(docker inspect topsv3-production-postgres --format '{{.State.Health.Status}}')" = "healthy"
  test "$(docker inspect topsv3-production-postgres --format '{{index .Config.Labels "com.docker.compose.project"}}')" = "topsv3-production"
  docker volume inspect topsv3-production-postgres-data >/dev/null
  sudo -n nginx -t >/dev/null
  sudo -n -l /usr/bin/systemctl reload nginx >/dev/null
}

synchronize_indexnow() (
  [ "$#" -eq 2 ] || fail "uso: indexnow MANIFESTO SHA256"
  local manifest="$1"
  local expected_sha256="$2"
  local actual_sha256

  validate_sha256 "${expected_sha256}"
  [[ "${manifest}" =~ ^/opt/topsv3/production/incoming/indexnow-[0-9]+-[0-9]+-[0-9a-f]{40}\.key$ ]] \
    || fail "manifesto IndexNow fora do caminho permitido"
  trap 'rm -f -- "${manifest}"' EXIT
  test -f "${manifest}"
  actual_sha256="$(sha256sum -- "${manifest}" | cut -d ' ' -f 1)"
  test "${actual_sha256}" = "${expected_sha256}"
  test "$(stat -c '%a' "${manifest}")" = "600"
  docker image inspect nginx:1.27-alpine >/dev/null
  docker run --rm --pull never \
    --network none \
    --read-only \
    --cap-drop ALL \
    --cap-add CHOWN \
    --security-opt no-new-privileges:true \
    --volume "${SECRETS_ROOT}:/secrets:rw" \
    --volume "${manifest}:/run/indexnow-key:ro" \
    nginx:1.27-alpine sh -c '
      set -eu
      env_file=/secrets/production.env
      key_file=/run/indexnow-key
      IFS= read -r key < "${key_file}"
      test -n "${key}"
      test "$(tr -d "\r\n" < "${key_file}")" = "${key}"
      case "${key}" in
        *[!A-Za-z0-9-]*) exit 2 ;;
      esac
      key_length="${#key}"
      test "${key_length}" -ge 8
      test "${key_length}" -le 128
      owner="$(stat -c "%u:%g" "${env_file}")"
      mode="$(stat -c "%a" "${env_file}")"
      temporary="$(mktemp "${env_file}.indexnow.XXXXXX")"
      cleanup() { rm -f -- "${temporary}"; }
      trap cleanup EXIT
      awk "!/^INDEXNOW_KEY=/" "${env_file}" > "${temporary}"
      printf "INDEXNOW_KEY=%s\n" "${key}" >> "${temporary}"
      chown "${owner}" "${temporary}"
      chmod "${mode}" "${temporary}"
      mv -f -- "${temporary}" "${env_file}"
      trap - EXIT
    ' </dev/null
)

install_release() {
  [ "$#" -eq 1 ] || fail "uso: install RELEASE_SHA"
  local release_sha="$1"
  local release_root="${DEPLOY_ROOT}/releases"
  local release_dir
  local incoming
  local temporary

  validate_release_sha "${release_sha}"
  release_dir="${release_root}/${release_sha}"
  incoming="${DEPLOY_ROOT}/incoming/${release_sha}.tar.gz"
  temporary="${release_root}/.${release_sha}.incoming"
  test -f "${incoming}"
  if [ -e "${release_dir}" ]; then
    test -f "${release_dir}/.release-sha"
    test "$(cat "${release_dir}/.release-sha")" = "${release_sha}"
    rm -f -- "${incoming}"
    return 0
  fi
  rm -rf -- "${temporary}"
  install -d -m 0750 "${temporary}"
  tar -xzf "${incoming}" -C "${temporary}"
  printf '%s\n' "${release_sha}" > "${temporary}/.release-sha"
  chmod -R a-w "${temporary}"
  mv -- "${temporary}" "${release_dir}"
  rm -f -- "${incoming}"
}

deploy_release() {
  [ "$#" -eq 1 ] || fail "uso: deploy RELEASE_SHA"
  local release_sha="$1"
  local release_dir compose_file env_file previous_release active_state_file
  local active_backend_port active_gateway_port postgres_id_before postgres_volume_before
  local db user snapshot_sql database_gate flyway_gate backup_producer
  local preview_backfill atomic_activator migration_dir snapshot_dir
  local snapshot_before snapshot_migrated snapshot_after backup_receipt
  local preview_backfill_report_dir baseline_health expected_flyway
  local applied_before failed_before expected_count_before inspection migrations_pending
  local preview_backfill_required backup_required backup_status current_production_sha
  local applied_after_migration failed_after_migration expected_count_after_migration

  validate_release_sha "${release_sha}"
  release_dir="${DEPLOY_ROOT}/releases/${release_sha}"
  compose_file="${release_dir}/deploy/production/docker-compose.yml"
  env_file="${SECRETS_ROOT}/production.env"
  previous_release="$(readlink -f "${DEPLOY_ROOT}/current")"
  active_state_file="${DEPLOY_ROOT}/runtime/active-release.env"
  active_backend_port=28080
  active_gateway_port=23000
  if [ -r "${active_state_file}" ]; then
    active_backend_port="$(sed -n 's/^ACTIVE_BACKEND_PORT=//p' "${active_state_file}" | tail -1)"
    active_gateway_port="$(sed -n 's/^ACTIVE_GATEWAY_PORT=//p' "${active_state_file}" | tail -1)"
  fi
  [[ "${active_backend_port}" =~ ^[0-9]+$ ]]
  [[ "${active_gateway_port}" =~ ^[0-9]+$ ]]
  postgres_id_before="$(docker inspect topsv3-production-postgres --format '{{.Id}}')"
  postgres_volume_before="$(docker inspect topsv3-production-postgres --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')"
  db="$(docker inspect topsv3-production-postgres --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^POSTGRES_DB=//p')"
  user="$(docker inspect topsv3-production-postgres --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^POSTGRES_USER=//p')"
  snapshot_sql="${release_dir}/scripts/deploy/capturar-snapshot-gate-banco-production.sql"
  database_gate="${release_dir}/scripts/deploy/validar-gate-banco-production.sh"
  flyway_gate="${release_dir}/scripts/deploy/validar-gate-flyway-production.sh"
  backup_producer="${release_dir}/scripts/deploy/criar-backup-validado-production.sh"
  preview_backfill="${release_dir}/scripts/deploy/executar-backfill-previews-production.sh"
  atomic_activator="${release_dir}/scripts/deploy/ativar-release-atomica-production.sh"
  migration_dir="${release_dir}/backend/src/main/resources/db/migration"
  snapshot_dir="$(mktemp -d)"
  snapshot_before="${snapshot_dir}/before.snapshot"
  snapshot_migrated="${snapshot_dir}/migrated.snapshot"
  snapshot_after="${snapshot_dir}/after.snapshot"
  backup_receipt="${snapshot_dir}/backup.receipt"
  preview_backfill_report_dir="${DEPLOY_ROOT}/runtime/preview-backfill/${release_sha}"
  export TOPSV3_RELEASE_SHA="${release_sha}"
  local compose=(docker compose --env-file "${env_file}" -f "${compose_file}" -p topsv3-production)

  cleanup_database_gate() {
    rm -rf -- "${snapshot_dir}"
  }

  capture_database_snapshot() {
    local destination="$1"
    local health_state="$2"
    docker exec -i topsv3-production-postgres psql \
      --no-psqlrc --set=ON_ERROR_STOP=1 \
      -U "${user}" -d "${db}" -At -F '|' \
      < "${snapshot_sql}" > "${destination}"
    printf 'META|health|%s\n' "${health_state}" >> "${destination}"
    LC_ALL=C sort -o "${destination}" "${destination}"
  }

  read_flyway_state() {
    local expected_version="$1"
    local expected_numeric="$((10#${expected_version}))"
    docker exec topsv3-production-postgres psql \
      --no-psqlrc --set=ON_ERROR_STOP=1 \
      -U "${user}" -d "${db}" -At -F '|' \
      </dev/null --command "
        SELECT
          COALESCE((
            SELECT version
            FROM flyway_schema_history
            WHERE success AND version ~ '^[0-9]+$'
            ORDER BY version::integer DESC, installed_rank DESC
            LIMIT 1
          ), 'AUSENTE'),
          count(*) FILTER (WHERE NOT success),
          count(*) FILTER (
            WHERE success
              AND CASE
                WHEN version ~ '^[0-9]+$' THEN version::integer
                ELSE NULL
              END = ${expected_numeric}
          )
        FROM flyway_schema_history;
      "
  }

  on_error() {
    local rc=$?
    cleanup_database_gate || true
    exit "${rc}"
  }
  trap on_error ERR

  "${compose[@]}" config --quiet </dev/null
  baseline_health=DOWN
  if curl -fsS "http://127.0.0.1:${active_backend_port}/api/health" | grep -q '"status":"UP"' \
    && curl -fsS "http://127.0.0.1:${active_gateway_port}/" >/dev/null \
    && curl -fsS "http://127.0.0.1:${active_gateway_port}/anuncios" >/dev/null; then
    baseline_health=UP
  fi
  capture_database_snapshot "${snapshot_before}" "${baseline_health}"
  expected_flyway="$(bash "${flyway_gate}" expected "${migration_dir}" </dev/null)"
  IFS='|' read -r applied_before failed_before expected_count_before \
    < <(read_flyway_state "${expected_flyway}")
  inspection="$(bash "${flyway_gate}" inspect "${expected_flyway}" \
    "${applied_before}" "${failed_before}" "${expected_count_before}" NOT_CHECKED </dev/null)"
  migrations_pending="$(awk -F= '$1 == "MIGRATIONS_PENDING" { print $2 }' <<< "${inspection}")"
  test "${migrations_pending}" = true || test "${migrations_pending}" = false
  preview_backfill_required=false
  if [ "$((10#${expected_flyway}))" -ge 53 ]; then
    test -r "${preview_backfill}"
    preview_backfill_required=true
  fi
  bash "${database_gate}" "${snapshot_before}" "${snapshot_before}" "${applied_before}" </dev/null

  backup_required=false
  if [ "${migrations_pending}" = true ]; then
    backup_required=true
  fi
  if [ "${preview_backfill_required}" = true ]; then
    backup_required=true
  fi

  backup_status=NOT_REQUIRED
  if [ "${backup_required}" = true ]; then
    test -f "${previous_release}/.release-sha"
    current_production_sha="$(cat "${previous_release}/.release-sha")"
    bash "${backup_producer}" \
      topsv3-production-postgres \
      "${DEPLOY_ROOT}/backups/postgresql" \
      "${current_production_sha}" \
      "${backup_receipt}" </dev/null
    backup_status="$(sed -n 's/^BACKUP_STATUS=//p' "${backup_receipt}")"
    test "${backup_status}" = VALIDATED
  fi
  bash "${flyway_gate}" before "${expected_flyway}" \
    "${applied_before}" "${failed_before}" "${expected_count_before}" "${backup_status}" </dev/null

  "${compose[@]}" run --rm -T --no-deps flyway migrate </dev/null
  "${compose[@]}" run --rm -T --no-deps flyway validate </dev/null
  IFS='|' read -r applied_after_migration failed_after_migration expected_count_after_migration \
    < <(read_flyway_state "${expected_flyway}")
  bash "${flyway_gate}" after "${expected_flyway}" \
    "${applied_after_migration}" "${failed_after_migration}" \
    "${expected_count_after_migration}" "${backup_status}" </dev/null
  capture_database_snapshot "${snapshot_migrated}" "${baseline_health}"
  bash "${database_gate}" "${snapshot_before}" "${snapshot_migrated}" \
    "${applied_before}" "${expected_flyway}" </dev/null

  printf 'REMOTE_DEPLOY_MARKER=before_initial_backfill\n'
  if [ "${preview_backfill_required}" = true ]; then
    "${compose[@]}" build backend </dev/null
    local preview_backfill_mode
    for preview_backfill_mode in PLAN APPLY VALIDATE; do
      bash "${preview_backfill}" \
        "${preview_backfill_mode}" initial \
        "${release_sha}" "${release_dir}" "${env_file}" \
        topsv3-production topsv3-production topsv3-production-net \
        "${preview_backfill_report_dir}" </dev/null
    done
  fi
  printf 'REMOTE_DEPLOY_MARKER=after_initial_backfill\n'

  capture_database_snapshot "${snapshot_after}" UP
  bash "${database_gate}" "${snapshot_migrated}" "${snapshot_after}" \
    "${expected_flyway}" </dev/null
  bash "${atomic_activator}" \
    "${release_sha}" "${release_dir}" "${env_file}" \
    "${expected_flyway}" "${user}" "${db}" </dev/null

  test "$(docker inspect topsv3-production-postgres --format '{{.Id}}')" = "${postgres_id_before}"
  test "$(docker inspect topsv3-production-postgres --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')" = "${postgres_volume_before}"
  test "$(readlink -f "${DEPLOY_ROOT}/current")" = "${release_dir}"
  cleanup_database_gate
  trap - ERR
}

smoke_release() {
  [ "$#" -eq 1 ] || fail "uso: smoke RELEASE_SHA"
  local release_sha="$1"
  local release_dir="${DEPLOY_ROOT}/releases/${release_sha}"
  local state_file="${DEPLOY_ROOT}/runtime/active-release.env"
  local active_sha active_project active_prefix
  local active_backend_port active_frontend_port active_gateway_port
  local port service container config_files frontend_env backend_env logs

  validate_release_sha "${release_sha}"
  state_value() {
    sed -n "s/^$1=//p" "${state_file}" | tail -1
  }
  test -r "${state_file}"
  active_sha="$(state_value ACTIVE_RELEASE_SHA)"
  active_project="$(state_value ACTIVE_PROJECT)"
  active_prefix="$(state_value ACTIVE_PREFIX)"
  active_backend_port="$(state_value ACTIVE_BACKEND_PORT)"
  active_frontend_port="$(state_value ACTIVE_FRONTEND_PORT)"
  active_gateway_port="$(state_value ACTIVE_GATEWAY_PORT)"
  for port in "${active_backend_port}" "${active_frontend_port}" "${active_gateway_port}"; do
    [[ "${port}" =~ ^[0-9]+$ ]]
    test "${port}" -ge 1024
    test "${port}" -le 65535
  done
  test "${active_sha}" = "${release_sha}"
  [[ "${active_project}" =~ ^[a-z0-9][a-z0-9_.-]+$ ]]
  [[ "${active_prefix}" =~ ^[a-z0-9][a-z0-9_.-]+$ ]]
  test "$(readlink -f "${DEPLOY_ROOT}/current")" = "${release_dir}"
  test "$(cat "${DEPLOY_ROOT}/current/.release-sha")" = "${release_sha}"
  for service in backend frontend gateway; do
    container="${active_prefix}-${service}"
    config_files="$(docker inspect "${container}" --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}')"
    case "${config_files}" in
      *"/releases/${release_sha}/deploy/production/docker-compose.yml"*) ;;
      *) echo "ERRO: ${service} nao usa o release ${release_sha}." >&2; return 1 ;;
    esac
    test "$(docker inspect "${container}" --format '{{index .Config.Labels "com.docker.compose.project"}}')" = "${active_project}"
    test "$(docker inspect "${container}" --format '{{.State.Running}}')" = "true"
  done
  test "$(docker inspect topsv3-production-postgres --format '{{.State.Health.Status}}')" = "healthy"
  curl -fsS "http://127.0.0.1:${active_backend_port}/api/health/liveness" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${active_backend_port}/api/health/readiness" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${active_frontend_port}/health/liveness" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${active_frontend_port}/health/readiness" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${active_gateway_port}/health/liveness" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${active_gateway_port}/health/readiness" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${active_gateway_port}/" >/dev/null
  curl -fsS "http://127.0.0.1:${active_gateway_port}/anuncios" >/dev/null
  frontend_env="$(docker inspect "${active_prefix}-frontend" --format '{{range .Config.Env}}{{println .}}{{end}}')"
  grep -qx 'NEXT_PUBLIC_SITE_URL=https://topsdojob.com' <<< "${frontend_env}"
  grep -qx 'NEXT_PUBLIC_ANALYTICS_ENABLED=true' <<< "${frontend_env}"
  grep -qx 'SEARCH_INDEXING_MODE=public' <<< "${frontend_env}"
  grep -q '^INDEXNOW_KEY=' <<< "${frontend_env}"
  backend_env="$(docker inspect "${active_prefix}-backend" --format '{{range .Config.Env}}{{println .}}{{end}}')"
  grep -qx 'SPRING_PROFILES_ACTIVE=production' <<< "${backend_env}"
  grep -qx 'APP_ENV=producao' <<< "${backend_env}"
  if printf '%s\n%s\n' "${backend_env}" "${frontend_env}" | grep -Eqi 'v3\.esle\.cloud|mailpit|homologacao|sandbox'; then
    echo 'ERRO: configuracao de homologacao encontrada no runtime de producao.' >&2
    return 1
  fi
  logs="$(docker logs "${active_prefix}-backend" 2>&1)"
  if printf '%s' "${logs}" | grep -Eqi 'IMPORTACAO_.*(INICIO|EXECUTADA)|fixture.*(INICIO|EXECUTADA|enabled=true)'; then
    echo 'ERRO: marcador de importador ou fixture encontrado na producao.' >&2
    return 1
  fi
}

main() {
  [ "$#" -ge 1 ] || fail "subcomando remoto ausente"
  local command="$1"
  shift
  assert_stdin_closed
  case "${command}" in
    prerequisites) validate_prerequisites "$@" ;;
    indexnow) synchronize_indexnow "$@" ;;
    install) install_release "$@" ;;
    deploy) deploy_release "$@" ;;
    smoke) smoke_release "$@" ;;
    *) fail "subcomando remoto invalido" ;;
  esac
}

main "$@"
