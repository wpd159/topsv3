#!/usr/bin/env bash
# Shared protection for the existing inline activation and manual recovery.
# This file never activates a release by itself and never restores a database.
OP_HEALTH_CONTRACT=main-v1

_op_legacy_source_sha() {
  # Published descendants of a60 retain its readiness-only contract. This fixed
  # lineage is selected before probing; an unknown SHA or failed main probe
  # must never acquire the legacy profile.
  case "$1" in
    a60b1e74978017a5bba1577f58804933b347c790|324b9cd4d62990e5d7e553bdfdfc8c3fe6d6a700|b8a88cd7bb469717f09b5aa05a693e33a903cd72|7c2285d21a2d05c4ad9828734c0daebf9d40dd68|451a6cb90dd1e67c5c774a0618c984d6b73735f8) return 0 ;;
    *) return 1 ;;
  esac
}

_op_release_profile() {
  local sha="$1" release="${OP_ROOT}/releases/$1"
  _op_sha "${sha}" || return 2
  [ "$(cat "${release}/.release-sha")" = "${sha}" ] || return 1
  if _op_legacy_source_sha "${sha}"; then
    printf 'legacy-a60\n'
  else
    # Explicit release contract, never inferred from a failed/404 probe.
    grep -qx 'OP_HEALTH_CONTRACT=main-v1' "${release}/scripts/deploy/proteger-operacao-production.sh" || return 1
    [ -f "${release}/frontend/src/app/health/liveness/route.ts" ] || return 1
    [ -f "${release}/frontend/src/app/health/readiness/route.ts" ] || return 1
    printf 'main-v1\n'
  fi
}
_op_snapshot_profile() {
  local sha="$1" destination="$2" profile
  profile="$(_op_release_profile "${sha}")" || return 1
  [ ! -e "${destination}" ] || return 2
  printf '%s|%s\n' "${sha}" "${profile}" > "${destination}"
}
_op_smoke_health() {
  local profile="$1" correlation="$2" deadline="${3:-}" remaining url
  local -a urls=(http://127.0.0.1:28080/api/health/readiness)
  case "${profile}" in
    main-v1) urls+=(http://127.0.0.1:28080/api/health/liveness http://127.0.0.1:23010/health/liveness http://127.0.0.1:23010/health/readiness http://127.0.0.1:23000/health/liveness http://127.0.0.1:23000/health/readiness) ;;
    legacy-a60) ;;
    *) return 2 ;;
  esac
  for url in "${urls[@]}"; do
    remaining=
    if [ -n "${deadline}" ]; then remaining=$((deadline - SECONDS)); fi
    if [[ "${url}" = */api/health/readiness ]]; then
      _op_http_probe "${url}" readiness "${correlation}" "${remaining}" || return 1
    elif [[ "${url}" = *:28080/* ]]; then
      _op_http_probe "${url}" backend-health "${correlation}" "${remaining}" || return 1
    else
      _op_http_probe "${url}" frontend-health "${correlation}" "${remaining}" || return 1
    fi
  done
}

_op_error() { printf 'ERRO operacao-production: %s\n' "$*" >&2; }
_op_sha() { [[ "$1" =~ ^[a-f0-9]{40}$ ]]; }
_op_id() { [[ "$1" =~ ^[a-f0-9-]{36}$ ]]; }
_op_start() {
  local value
  [ -r "/proc/$1/stat" ] || return 1
  value="$(cat "/proc/$1/stat")" || return 1
  value="${value##*) }"
  set -- ${value}
  printf '%s\n' "${20}"
}
_op_alive() {
  [ -n "${1:-}" ] && [ -n "${2:-}" ] &&
    [ "$(cat /proc/sys/kernel/random/boot_id)" = "${OP_BOOT}" ] &&
    [ "$(_op_start "$1" 2>/dev/null)" = "$2" ]
}
_op_running() {
  local value
  _op_alive "$1" "$2" || return 1
  value="$(cat "/proc/$1/stat")" || return 1
  value="${value##*) }"
  [[ "${value}" != Z\ * ]]
}
_op_state() {
  local temporary="${OP_ROOT}/operations/.state-${OP_ID}-${BASHPID}"
  (
    umask 077
    printf '%s\n' "version=1" "id=${OP_ID}" "candidate=${OP_CANDIDATE}" \
      "previous=${OP_PREVIOUS_SHA:-}" "phase=${OP_PHASE}" "pid=${OP_PID}" \
      "start=${OP_START}" "boot=${OP_BOOT}" "child_pid=${OP_CHILD_PID:-}" \
      "child_start=${OP_CHILD_START:-}" "mutated=${OP_MUTATED:-0}" \
      "snapshot=${OP_SNAPSHOT_READY:-0}" "result=${OP_RESULT:-}" \
      "original_rc=${OP_ORIGINAL_RC:-0}" "ambiguous=${OP_AMBIGUOUS:-0}" \
      "migration_started=${OP_MIGRATION_STARTED:-0}" > "${temporary}" || exit 1
    mv -f -- "${temporary}" "${OP_ROOT}/operations/active.state"
  ) || return 1
}
_op_read() {
  local name="$1" file="$2"
  [ "$(grep -c "^${name}=" "${file}")" -eq 1 ] || return 1
  sed -n "s/^${name}=//p" "${file}"
}
_op_validate_state() {
  local file="$1" value name
  [ -f "${file}" ] && [ ! -L "${file}" ] && [ "$(wc -l < "${file}")" -eq 16 ] || return 1
  [ "$(_op_read version "${file}")" = 1 ] || return 1
  _op_id "$(_op_read id "${file}")" || return 1
  _op_sha "$(_op_read candidate "${file}")" || return 1
  value="$(_op_read previous "${file}")" || return 1
  [ -z "${value}" ] || _op_sha "${value}" || return 1
  value="$(_op_read phase "${file}")" || return 1
  [[ "${value}" =~ ^[A-Z_]{2,40}$ ]] || return 1
  _op_id "$(_op_read boot "${file}")" || return 1
  for name in pid start original_rc; do
    value="$(_op_read "${name}" "${file}")" || return 1
    [[ "${value}" =~ ^[0-9]+$ ]] || return 1
  done
  for name in child_pid child_start; do
    value="$(_op_read "${name}" "${file}")" || return 1
    [[ "${value}" =~ ^[0-9]*$ ]] || return 1
  done
  for name in mutated snapshot ambiguous migration_started; do
    value="$(_op_read "${name}" "${file}")" || return 1
    [[ "${value}" = 0 || "${value}" = 1 ]] || return 1
  done
  value="$(_op_read result "${file}")" || return 1
  case "${value}" in ''|COMPLETED|ROLLED_BACK|ABORTED|INCOMPLETE|RECONCILED) ;; *) return 1 ;; esac
}
_op_lock() {
  local requested="$1"
  [[ "${requested}" = /* && "${requested}" != / && "${requested}" != *$'\n'* ]] || return 2
  OP_ROOT="$(readlink -e -- "${requested}")" || return 2
  [ -d "${OP_ROOT}/releases" ] && [ -L "${OP_ROOT}/current" ] || return 2
  OP_SECRETS="$(dirname -- "${OP_ROOT}")/secrets"
  [ ! -L "${OP_ROOT}/operations" ] && [ ! -L "${OP_ROOT}/deploy.lock" ] || return 2
  [ ! -L "${OP_ROOT}/operations/active.state" ] || return 2
  install -d -m 0700 "${OP_ROOT}/operations" || return 1
  exec {OP_LOCK_FD}>"${OP_ROOT}/deploy.lock" || return 1
  if ! flock -n "${OP_LOCK_FD}"; then
    exec {OP_LOCK_FD}>&-
    _op_error 'outra ativacao/recuperacao ainda possui o mutex remoto'
    return 75
  fi
  # A detached preview job may outlive its CLI. Never start deployment/recovery
  # over an unresolved regularization, even after the original flock is gone.
  if [ -e "${OP_ROOT}/operations/preview-backfill.pending" ] || [ -L "${OP_ROOT}/operations/preview-backfill.pending" ]; then
    exec {OP_LOCK_FD}>&-
    _op_error 'regularizacao de previews pendente; apuracao explicita obrigatoria'
    return 76
  fi
}
_op_config_hashes() {
  (
    cd "${OP_SECRETS}" || return 1
    sha256sum production.env application-production.yml nginx-production-local.conf efi-webhook-allowlist.conf || return 1
    if [ -d efi ]; then
      find efi \( -type f -o -type l \) -print0 | sort -z | xargs -0 -r sha256sum
    fi
  )
}
_op_compose_hash() {
  local output
  output="$(env "TOPSV3_RELEASE_SHA=$1" docker compose \
    --env-file "${OP_SECRETS}/production.env" \
    -f "${OP_ROOT}/releases/$1/deploy/production/docker-compose.yml" \
    -p topsv3-production config --hash "$2")" || return 1
  output="${output##* }"
  [[ "${output}" =~ ^[a-f0-9]{64}$ ]] || return 1
  printf '%s\n' "${output}"
}
_op_effective_hash() {
  # The private snapshot stores hashes, not environment values or credentials.
  # Compose may reorder environment/mount arrays when recreating a container.
  # JSON per element preserves embedded newlines; typed rows preserve content.
  docker inspect "topsv3-production-$1" --format '{{range .Config.Env}}env {{json .}}{{println}}{{end}}{{range .Mounts}}mount {{json .}}{{println}}{{end}}ports {{json .HostConfig.PortBindings}}{{println}}network {{json .HostConfig.NetworkMode}}' |
    LC_ALL=C sort | sha256sum | cut -d ' ' -f 1
}
_op_verify_config_input() {
  local actual original planned
  (cd "${OP_SECRETS}" && grep -v '  production.env$' "${OP_DIR}/config.sha256" | sha256sum -c --status) || return 1
  actual="$(sha256sum "${OP_SECRETS}/production.env" | cut -d ' ' -f 1)" || return 1
  original="$(sha256sum "${OP_DIR}/env.copy" | cut -d ' ' -f 1)" || return 1
  planned="${original}"
  if [ -f "${OP_DIR}/env.expected" ]; then
    planned="$(sha256sum "${OP_DIR}/env.expected" | cut -d ' ' -f 1)" || return 1
  fi
  [[ "${actual}" = "${original}" || "${actual}" = "${planned}" ]]
}
op_expect_indexnow() {
  local key="$1"
  [[ "${key}" =~ ^[A-Za-z0-9-]{8,128}$ ]] || return 2
  _op_verify_config_input || return 1
  [ ! -e "${OP_DIR}/env.expected" ] || return 2
  (
    umask 077
    awk '!/^INDEXNOW_KEY=/' "${OP_DIR}/env.copy" > "${OP_DIR}/env.expected" || exit 1
    printf 'INDEXNOW_KEY=%s\n' "${key}" >> "${OP_DIR}/env.expected"
  )
}
op_expect_candidate() {
  local service reference image config_hash
  _op_verify_config_input || return 1
  if [ -f "${OP_DIR}/env.expected" ]; then
    cmp -s -- "${OP_DIR}/env.expected" "${OP_SECRETS}/production.env" || return 1
  fi
  [ ! -e "${OP_DIR}/candidate.images.tsv" ] || return 2
  : > "${OP_DIR}/candidate.images.tsv" || return 1
  : > "${OP_DIR}/candidate.compose.tsv" || return 1
  for service in backend frontend; do
    reference="topsv3-production-${service}:${OP_CANDIDATE}"
    image="$(docker image inspect "${reference}" --format '{{.Id}}')" || return 1
    [[ "${image}" =~ ^sha256:[a-f0-9]{64}$ ]] || return 1
    printf '%s\t%s\t%s\n' "${service}" "${image}" "${reference}" >> "${OP_DIR}/candidate.images.tsv" || return 1
  done
  grep $'^gateway\t' "${OP_DIR}/images.tsv" >> "${OP_DIR}/candidate.images.tsv" || return 1
  for service in backend frontend gateway; do
    config_hash="$(_op_compose_hash "${OP_CANDIDATE}" "${service}")" || return 1
    printf '%s\t%s\n' "${service}" "${config_hash}" >> "${OP_DIR}/candidate.compose.tsv" || return 1
  done
  _op_config_hashes > "${OP_DIR}/candidate.config.sha256" || return 1
  _op_snapshot_profile "${OP_CANDIDATE}" "${OP_DIR}/candidate.health-profile" || return 1
  grep -qx "${OP_CANDIDATE}|main-v1" "${OP_DIR}/candidate.health-profile"
}
_op_capture_images() {
  local service image reference
  for service in backend frontend gateway; do
    image="$(docker inspect "topsv3-production-${service}" --format '{{.Image}}')" || return 1
    reference="$(docker inspect "topsv3-production-${service}" --format '{{.Config.Image}}')" || return 1
    [[ "${image}" =~ ^sha256:[a-f0-9]{64}$ && "${reference}" != *$'\t'* && "${reference}" != *$'\n'* ]] || return 1
    [ "$(docker image inspect "${reference}" --format '{{.Id}}')" = "${image}" ] || return 1
    printf '%s\t%s\t%s\n' "${service}" "${image}" "${reference}"
  done
}
_op_postgres_identity() {
  docker inspect topsv3-production-postgres --format '{{.Id}} {{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}'
}
_op_capture_baseline() {
  local service desired actual effective
  OP_PREVIOUS_RELEASE="$(readlink -e "${OP_ROOT}/current")" || return 1
  OP_PREVIOUS_SHA="$(basename -- "${OP_PREVIOUS_RELEASE}")"
  _op_sha "${OP_PREVIOUS_SHA}" || return 1
  [ "${OP_PREVIOUS_RELEASE}" = "${OP_ROOT}/releases/${OP_PREVIOUS_SHA}" ] || return 1
  [ -f "${OP_PREVIOUS_RELEASE}/deploy/production/docker-compose.yml" ] || return 1
  [ "$(cat "${OP_PREVIOUS_RELEASE}/.release-sha")" = "${OP_PREVIOUS_SHA}" ] || return 1
  _op_snapshot_profile "${OP_PREVIOUS_SHA}" "${OP_DIR}/previous.health-profile" || return 1
  _op_capture_images > "${OP_DIR}/images.tsv" || return 1
  _op_config_hashes > "${OP_DIR}/config.sha256" || return 1
  cp -- "${OP_SECRETS}/production.env" "${OP_DIR}/env.copy" || return 1
  chmod 0600 "${OP_DIR}/env.copy" || return 1
  stat -c '%u:%g %a' "${OP_SECRETS}/production.env" > "${OP_DIR}/env.metadata" || return 1
  _op_postgres_identity > "${OP_DIR}/postgres.identity" || return 1
  : > "${OP_DIR}/runtime.tsv" || return 1
  : > "${OP_DIR}/compose.tsv" || return 1
  for service in backend frontend gateway; do
    desired="$(_op_compose_hash "${OP_PREVIOUS_SHA}" "${service}")" || return 1
    actual="$(docker inspect "topsv3-production-${service}" --format '{{index .Config.Labels "com.docker.compose.config-hash"}}')" || return 1
    [ "${actual}" = "${desired}" ] || {
      _op_error 'configuracao em disco nao corresponde ao runtime anterior; ativacao recusada'
      return 1
    }
    printf '%s\t%s\n' "${service}" "${desired}" >> "${OP_DIR}/compose.tsv" || return 1
    effective="$(_op_effective_hash "${service}")" || return 1
    printf '%s\t%s\n' "${service}" "${effective}" >> "${OP_DIR}/runtime.tsv" || return 1
  done
  _op_verify_runtime "${OP_PREVIOUS_SHA}" "${OP_DIR}/images.tsv" || return 1
  (cd "${OP_SECRETS}" && sha256sum -c --status "${OP_DIR}/config.sha256") || return 1
  cmp -s -- "${OP_DIR}/env.copy" "${OP_SECRETS}/production.env" || return 1
}
_op_verify_runtime() {
  local sha="$1" manifest="$2" service image reference files expected actual compose_manifest
  compose_manifest="${OP_DIR}/candidate.compose.tsv"
  [ "${sha}" != "${OP_PREVIOUS_SHA}" ] || compose_manifest="${OP_DIR}/compose.tsv"
  while IFS=$'\t' read -r service image reference; do
    [[ "${service}" =~ ^(backend|frontend|gateway)$ && "${image}" =~ ^sha256:[a-f0-9]{64}$ ]] || return 1
    [ "$(docker image inspect "${reference}" --format '{{.Id}}')" = "${image}" ] || return 1
    [ "$(docker inspect "topsv3-production-${service}" --format '{{.Image}}')" = "${image}" ] || return 1
    [ "$(docker inspect "topsv3-production-${service}" --format '{{.State.Running}}')" = true ] || return 1
    files="$(docker inspect "topsv3-production-${service}" --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}')" || return 1
    [ "${files}" = "${OP_ROOT}/releases/${sha}/deploy/production/docker-compose.yml" ] || return 1
    expected="$(awk -F '\t' -v service="${service}" '$1 == service { print $2 }' "${compose_manifest}")" || return 1
    actual="$(docker inspect "topsv3-production-${service}" --format '{{index .Config.Labels "com.docker.compose.config-hash"}}')" || return 1
    [ -n "${expected}" ] && [ "${actual}" = "${expected}" ] || return 1
    if [ "${sha}" = "${OP_PREVIOUS_SHA}" ]; then
      expected="$(awk -F '\t' -v service="${service}" '$1 == service { print $2 }' "${OP_DIR}/runtime.tsv")" || return 1
      [ "$(_op_effective_hash "${service}")" = "${expected}" ] || return 1
    fi
  done < "${manifest}"
  [ "$(wc -l < "${manifest}")" -eq 3 ] || return 1
  [ "$(_op_postgres_identity)" = "$(cat "${OP_DIR}/postgres.identity")" ] || return 1
  [ "$(docker inspect topsv3-production-postgres --format '{{.State.Health.Status}}')" = healthy ] || return 1
}
# Read-only content check, also usable by a precheck on an already obtained body:
# source scripts/deploy/proteger-operacao-production.sh
# _op_validate_content home /path/to/body.html
# No OP_* state, locks or journal; the caller must still check transport/HTTP.
_op_validate_content() {
  local kind="$1" body="$2"
  case "${kind}" in home|catalog) ;; *) return 2 ;; esac
  [ -f "${body}" ] && [ -r "${body}" ] && [ -s "${body}" ] || return 1
  LC_ALL=C awk -v kind="${kind}" '
    BEGIN { benign = "\\\"digest\\\":\\\"$undefined\\\"" }
    {
      if (/NEXT_HTTP_ERROR_FALLBACK|Application error|Internal Server Error|:E\{/) bad = 1
      rest = $0
      while (match(rest, /\\*"digest\\*"[[:space:]]*:/)) {
        before = substr(rest, 1, RSTART - 1)
        tail = substr(rest, RSTART)
        # Next 15.5.23: exact undefined property inside one escaped JSON string.
        # Require compact property/value boundaries; no raw JSON, extra escape
        # layer, prefix value, or truncated token inherits this exception.
        if (before !~ /[,{]$/ ||
            substr(tail, 1, length(benign)) != benign ||
            substr(tail, length(benign) + 1, 1) !~ /^[},]$/) bad = 1
        # Advance only past this key, never discard its line/script or any
        # later digest. Other error markers were checked on the original line.
        rest = substr(tail, RLENGTH + 1)
      }
      if (index($0, "<h1")) heading = 1
      if (kind == "home") {
        if (index($0, "Encontre ")) first = 1
        if (index($0, "perto de voc")) second = 1
      } else {
        if (index($0, "Anúncios de acompanhantes")) first = 1
        if (index($0, "Explore perfis publicados")) second = 1
      }
    }
    END { exit (bad || !heading || !first || !second) ? 1 : 0 }
  ' "${body}" || return 1
}
_op_http_probe() {
  local url="$1" kind="$2" correlation="$3" limit=7 result status duration rc=0 remaining="${4:-}"
  local body="${OP_DIR}/probe-${BASHPID}.body"
  case "${kind}" in readiness|backend-health|frontend-health) limit=2 ;; esac
  if [ -n "${remaining}" ]; then
    [[ "${remaining}" =~ ^[0-9]+$ ]] && [ "${remaining}" -gt 0 ] || return 1
    [ "${remaining}" -ge "${limit}" ] || limit="${remaining}"
  fi
  result="$(curl --silent --show-error --connect-timeout 1 --max-time "${limit}" \
    --header "X-Request-ID: ${correlation}" --output "${body}" \
    --write-out '%{http_code} %{time_total}' "${url}")" || rc=$?
  read -r status duration <<< "${result}"
  printf 'PROBE url=%s status=%s duration_s=%s correlation=%s curl_rc=%s\n' \
    "${url}" "${status:-000}" "${duration:-unknown}" "${correlation}" "${rc}"
  [ "${rc}" -eq 0 ] && [ "${status}" = 200 ] || return 1
  case "${kind}" in
    readiness|backend-health)
      grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${body}" || return 1
      grep -Eq '"app"[[:space:]]*:[[:space:]]*"topsdojob-v3-backend"' "${body}" || return 1 ;;
    frontend-health)
      grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "${body}" || return 1
      grep -Eq '"app"[[:space:]]*:[[:space:]]*"topsdojob-v3-frontend"' "${body}" || return 1 ;;
    home|catalog)
      _op_validate_content "${kind}" "${body}" || return 1 ;;
    *) return 2 ;;
  esac
  rm -f -- "${body}"
}
op_smoke() {
  local sha="$1" attempt manifest correlation readiness_deadline ready=0 recovery_deadline remaining pause profile profile_file profile_sha
  _op_sha "${sha}" || return 2
  if [ "${sha}" = "${OP_PREVIOUS_SHA}" ]; then
    manifest="${OP_DIR}/images.tsv"
    profile_file="${OP_DIR}/previous.health-profile"
  else
    manifest="${OP_DIR}/candidate.images.tsv"
    profile_file="${OP_DIR}/candidate.health-profile"
    [ -s "${manifest}" ] || return 1
    (cd "${OP_SECRETS}" && sha256sum -c --status "${OP_DIR}/candidate.config.sha256") || return 1
  fi
  [ -f "${profile_file}" ] && [ ! -L "${profile_file}" ] || return 1
  [ "$(wc -l < "${profile_file}")" -eq 1 ] || return 1
  IFS='|' read -r profile_sha profile < "${profile_file}"
  [ "${profile_sha}" = "${sha}" ] || return 1
  case "${profile}" in main-v1|legacy-a60) ;; *) return 1 ;; esac
  [ "${profile}" != legacy-a60 ] || _op_legacy_source_sha "${sha}" || return 1
  [ "${sha}" = "${OP_PREVIOUS_SHA}" ] || [ "${profile}" = main-v1 ] || return 1
  printf 'HEALTH_PROFILE sha=%s profile=%s snapshot=%s\n' "${sha}" "${profile}" "${profile_file##*/}"
  if [ "${OP_RECOVERING:-0}" -eq 1 ] && [ "${sha}" = "${OP_PREVIOUS_SHA}" ]; then
    # The old release historically became ready at ~32s but served home only
    # around 183s. Give recovery 300s, independent of candidate acceptance.
    # This is a probe-wait window, not a timeout for Docker/identity commands.
    # _op_restore has already recreated the services once; this loop only reads.
    recovery_deadline=$((SECONDS + 300))
    attempt=0
    while [ "${SECONDS}" -lt "${recovery_deadline}" ]; do
      [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
      attempt=$((attempt + 1))
      correlation="deploy-${OP_ID}-recovery-${attempt}"
      if _op_verify_runtime "${sha}" "${manifest}" &&
        _op_smoke_health "${profile}" "${correlation}-ready" "${recovery_deadline}" &&
        _op_http_probe http://127.0.0.1:23000/ home "${correlation}-home" "$((recovery_deadline - SECONDS))" &&
        _op_http_probe http://127.0.0.1:23000/anuncios catalog "${correlation}-catalog" "$((recovery_deadline - SECONDS))"; then
        [ "${SECONDS}" -lt "${recovery_deadline}" ] || return 1
        [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
        return 0
      fi
      remaining=$((recovery_deadline - SECONDS))
      [ "${remaining}" -gt 0 ] || break
      pause=15
      [ "${remaining}" -ge "${pause}" ] || pause="${remaining}"
      sleep "${pause}"
    done
    return 1
  fi
  # Boot readiness is cheap and independent of locality/SSR fan-out. Java may
  # need tens of seconds to boot, even when connection refusal is immediate.
  readiness_deadline=$((SECONDS + 90))
  while [ "${SECONDS}" -lt "${readiness_deadline}" ]; do
    [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
    if _op_smoke_health "${profile}" "deploy-${OP_ID}-boot" "${readiness_deadline}"; then
      ready=1
      break
    fi
    sleep 2
  done
  [ "${ready}" -eq 1 ] || return 1
  # At most four sequential batches. No parallel SSR probes or 60 heavy retries.
  for attempt in 1 2 3 4; do
    [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
    correlation="deploy-${OP_ID}-${attempt}"
    if _op_verify_runtime "${sha}" "${manifest}" &&
      _op_smoke_health "${profile}" "${correlation}-ready" &&
      _op_http_probe http://127.0.0.1:23000/ home "${correlation}-home" &&
      _op_http_probe http://127.0.0.1:23000/anuncios catalog "${correlation}-catalog"; then
      return 0
    fi
    [ "${attempt}" -eq 4 ] || sleep 3
  done
  return 1
}
op_phase() {
  [[ "$1" =~ ^[A-Z_]{2,40}$ ]] || return 2
  [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
  OP_PHASE="$1"
  [ "$1" != MIGRATING ] || OP_MIGRATION_STARTED=1
  _op_state
}
_op_signal() { [ "${OP_CANCEL_RC:-0}" -ne 0 ] || OP_CANCEL_RC="$1"; }
_op_watch_parent() {
  local owner="$1" owner_start="$2" parent="$3" parent_start="$4"
  exec {OP_LOCK_FD}>&-
  trap - EXIT INT TERM HUP PIPE
  while _op_alive "${owner}" "${owner_start}"; do
    if ! _op_alive "${parent}" "${parent_start}"; then
      kill -HUP "${owner}"
      return
    fi
    sleep 1
  done
}
_op_install_traps() {
  trap '_op_signal 130' INT
  trap '_op_signal 143' TERM
  trap '_op_signal 129' HUP
  trap '_op_signal 141' PIPE
  trap '_op_exit "$?"' EXIT
}
_op_stop_watch() {
  if _op_alive "${OP_WATCH_PID:-}" "${OP_WATCH_START:-}"; then
    kill -TERM "${OP_WATCH_PID}" || return 1
    local rc=0
    wait "${OP_WATCH_PID}" || rc=$?
    [ "${rc}" -eq 0 ] || [ "${rc}" -eq 143 ] || return "${rc}"
  fi
}
op_begin() {
  _op_sha "$2" || return 2
  _op_lock "$1" || return $?
  local state="${OP_ROOT}/operations/active.state" result parent_start
  if [ -e "${state}" ]; then
    if ! _op_validate_state "${state}"; then
      exec {OP_LOCK_FD}>&-
      _op_error 'journal invalido; nenhuma nova mutacao autorizada'
      return 76
    fi
    result="$(_op_read result "${state}")" || return 76
    case "${result}" in COMPLETED|ROLLED_BACK|ABORTED|RECONCILED) ;; *)
      _op_error 'estado anterior nao terminal; reconciliacao explicita obrigatoria'
      exec {OP_LOCK_FD}>&-
      return 76 ;;
    esac
  fi
  OP_CANDIDATE="$2"
  [ -f "${OP_ROOT}/releases/${OP_CANDIDATE}/.release-sha" ] || return 2
  [ "$(cat "${OP_ROOT}/releases/${OP_CANDIDATE}/.release-sha")" = "${OP_CANDIDATE}" ] || return 2
  OP_ID="$(cat /proc/sys/kernel/random/uuid)"
  _op_id "${OP_ID}" || return 1
  OP_DIR="${OP_ROOT}/operations/${OP_ID}"
  mkdir -m 0700 -- "${OP_DIR}" || return 1
  OP_PID="${BASHPID}"
  OP_START="$(_op_start "${OP_PID}")"
  OP_BOOT="$(cat /proc/sys/kernel/random/boot_id)"
  OP_PHASE=PREPARING OP_RESULT= OP_MUTATED=0 OP_AMBIGUOUS=0 OP_SNAPSHOT_READY=0
  OP_PREVIOUS_SHA= OP_PREVIOUS_RELEASE= OP_CHILD_PID= OP_CHILD_START=
  OP_CANCEL_RC=0 OP_ORIGINAL_RC=0 OP_RECOVERING=0 OP_MIGRATION_STARTED=0 OP_RECONCILE_ONLY=0 OP_POST_WORKFLOW=0
  OP_ACTIVE=1
  _op_install_traps
  _op_state || return 1
  _op_capture_baseline || return 1
  OP_SNAPSHOT_READY=1
  _op_state || return 1
  parent_start="$(_op_start "${PPID}")" || return 1
  _op_watch_parent "${OP_PID}" "${OP_START}" "${PPID}" "${parent_start}" &
  OP_WATCH_PID=$! OP_WATCH_START="$(_op_start "$!")"
  printf 'OPERATION id=%s candidate=%s previous=%s phase=%s\n' "${OP_ID}" "${OP_CANDIDATE}" "${OP_PREVIOUS_SHA}" "${OP_PHASE}"
}
op_run() {
  local kind="$1" rc=0 drain_started=-1 docker_command=0 argument
  shift
  [[ "${kind}" = mutating || "${kind}" = readonly ]] || return 2
  [ "${OP_CANCEL_RC:-0}" -eq 0 ] || [ "${OP_RECOVERING:-0}" -eq 1 ] || return "${OP_CANCEL_RC}"
  [ -z "${OP_CHILD_PID:-}" ] || return 2
  # Docker's exit 1 also covers a lost API response. A dead CLI does not prove
  # that the daemon stopped its mutation; that failure needs explicit recovery.
  for argument in "$@"; do
    if [ "${argument##*/}" = docker ]; then docker_command=1; break; fi
  done
  if [ "${kind}" = mutating ]; then OP_MUTATED=1; fi
  # Persist intent before spawn. A lost owner in this window is not safe to replay.
  _op_state || return 1
  if [ "${kind}" = readonly ]; then
    setsid -- "$@" </dev/null &
  else
    setsid -- "$@" <&0 &
  fi
  OP_CHILD_PID=$!
  # A fast child can finish before /proc is sampled; wait still owns its status.
  OP_CHILD_START="$(_op_start "${OP_CHILD_PID}" 2>/dev/null)" || OP_CHILD_START=
  _op_state || return 1
  while _op_running "${OP_CHILD_PID}" "${OP_CHILD_START}"; do
    if [ "${OP_CANCEL_RC:-0}" -ne 0 ]; then
      [ "${drain_started}" -ge 0 ] || drain_started="${SECONDS}"
      if [ "$((SECONDS - drain_started))" -ge 120 ]; then
        OP_AMBIGUOUS=1
        _op_state || return 1
        return "${OP_CANCEL_RC}"
      fi
    fi
    sleep 0.1
  done
  wait "${OP_CHILD_PID}" || rc=$?
  # Descendants keep the inherited flock. Never undo while a mutator group lives.
  drain_started="${SECONDS}"
  while kill -0 -- "-${OP_CHILD_PID}" 2>/dev/null; do
    if [ "$((SECONDS - drain_started))" -ge 120 ]; then
      OP_AMBIGUOUS=1
      _op_state || return 1
      return 125
    fi
    sleep 0.1
  done
  if [ "${kind}" = mutating ] && { [ "${rc}" -ge 128 ] || [ "${rc}" -eq 124 ] || [ "${rc}" -eq 125 ]; }; then
    OP_AMBIGUOUS=1
  fi
  if [ "${kind}" = mutating ] && [ "${docker_command}" -eq 1 ] && [ "${rc}" -ne 0 ]; then
    OP_AMBIGUOUS=1
    _op_error 'Docker mutante falhou; quiescencia do daemon nao comprovada, sem rollback automatico concorrente'
  fi
  OP_CHILD_PID= OP_CHILD_START=
  _op_state || return 1
  if [ "${OP_RECOVERING:-0}" -eq 0 ] && [ "${OP_CANCEL_RC:-0}" -ne 0 ]; then return "${OP_CANCEL_RC}"; fi
  return "${rc}"
}
_op_restoration_identity() {
  local hashes
  hashes="$(sha256sum "${OP_DIR}/images.tsv" "${OP_DIR}/config.sha256" "${OP_DIR}/compose.tsv" "${OP_DIR}/runtime.tsv" "${OP_DIR}/previous.health-profile" | sha256sum | cut -d ' ' -f 1)" || return 1
  printf '%s|%s|%s\n' "${OP_ID}" "${OP_PREVIOUS_SHA}" "${hashes}"
}
_op_verify_restored_snapshot() {
  [ -f "${OP_DIR}/restoration.complete" ] && [ ! -L "${OP_DIR}/restoration.complete" ] || return 1
  [ "$(cat "${OP_DIR}/restoration.complete")" = "$(_op_restoration_identity)" ] || return 1
  [ "$(readlink -e "${OP_ROOT}/current")" = "${OP_PREVIOUS_RELEASE}" ] || return 1
  (cd "${OP_SECRETS}" && sha256sum -c --status "${OP_DIR}/config.sha256") || return 1
  _op_verify_runtime "${OP_PREVIOUS_SHA}" "${OP_DIR}/images.tsv"
}
_op_restore() {
  local service image reference owner mode link
  _op_sha "${OP_PREVIOUS_SHA}" || return 1
  [ "${OP_PREVIOUS_RELEASE}" = "${OP_ROOT}/releases/${OP_PREVIOUS_SHA}" ] || return 1
  if [ -e "${OP_DIR}/restoration.complete" ]; then
    # INCOMPLETE may mean only the functional window failed after restoration.
    # Prove the same physical snapshot, then observe; never recreate it again.
    _op_verify_restored_snapshot || return 1
    op_smoke "${OP_PREVIOUS_SHA}"
    return $?
  fi
  # Other mounted configuration is not mutated here. Divergence is external,
  # not permission to overwrite somebody else's configuration.
  _op_verify_config_input || return 1
  while IFS=$'\t' read -r service image reference; do
    [ "$(docker image inspect "${reference}" --format '{{.Id}}')" = "${image}" ] || return 1
  done < "${OP_DIR}/images.tsv"
  read -r owner mode < "${OP_DIR}/env.metadata"
  [[ "${owner}" =~ ^[0-9]+:[0-9]+$ && "${mode}" =~ ^[0-7]{3,4}$ ]] || return 1
  # The deploy user reads its 0600 snapshot on the host. The container receives
  # only controlled stdin, so root needs no DAC bypass or private-directory mount.
  op_run mutating docker run --rm -i --pull never --network none --read-only \
    --cap-drop ALL --cap-add CHOWN --security-opt no-new-privileges:true \
    --volume "${OP_SECRETS}:/secrets:rw" \
    nginx:1.27-alpine sh -c '
      set -eu
      temporary="$(mktemp /secrets/production.env.recovery.XXXXXX)"
      trap '\''rm -f -- "${temporary}"'\'' EXIT
      cat > "${temporary}"
      chmod "$2" "${temporary}"
      chown "$1" "${temporary}"
      mv -f -- "${temporary}" /secrets/production.env
      trap - EXIT
    ' sh "${owner}" "${mode}" < "${OP_DIR}/env.copy" || return 1
  (cd "${OP_SECRETS}" && sha256sum -c --status "${OP_DIR}/config.sha256") || return 1
  # Keep the captured Compose untouched, including historical health dependencies.
  # Separate calls let the gateway serve startup callbacks before final health.
  for service in backend frontend gateway; do
    op_run mutating env "TOPSV3_RELEASE_SHA=${OP_PREVIOUS_SHA}" docker compose \
      --env-file "${OP_SECRETS}/production.env" \
      -f "${OP_PREVIOUS_RELEASE}/deploy/production/docker-compose.yml" -p topsv3-production \
      up -d --no-deps --force-recreate --no-build --pull never "${service}" || return 1
  done
  _op_verify_runtime "${OP_PREVIOUS_SHA}" "${OP_DIR}/images.tsv" || return 1
  link="${OP_ROOT}/.rollback-${OP_ID}"
  if [ -L "${link}" ]; then
    [ "$(readlink -- "${link}")" = "${OP_PREVIOUS_RELEASE}" ] || return 1
  else
    [ ! -e "${link}" ] || return 1
    ln -s -- "${OP_PREVIOUS_RELEASE}" "${link}" || return 1
  fi
  mv -Tf -- "${link}" "${OP_ROOT}/current" || return 1
  [ "$(readlink -e "${OP_ROOT}/current")" = "${OP_PREVIOUS_RELEASE}" ] || return 1
  _op_restoration_identity > "${OP_DIR}/restoration.pending" || return 1
  mv -- "${OP_DIR}/restoration.pending" "${OP_DIR}/restoration.complete" || return 1
  local cancelled="${OP_CANCEL_RC}" smoke_rc=0
  OP_CANCEL_RC=0
  op_smoke "${OP_PREVIOUS_SHA}" || smoke_rc=$?
  OP_CANCEL_RC="${cancelled}"
  return "${smoke_rc}"
}
_op_exit() {
  local rc="$1" recovery_rc=0
  [ "${BASHPID}" = "${OP_PID:-}" ] || return "${rc}"
  [ "${OP_ACTIVE:-0}" -eq 1 ] || return "${rc}"
  trap - EXIT
  [ "${OP_CANCEL_RC:-0}" -eq 0 ] || rc="${OP_CANCEL_RC}"
  [ "${rc}" -ne 0 ] || rc=1
  if [ "${OP_POST_WORKFLOW:-0}" -eq 1 ] && [ "${OP_ORIGINAL_RC:-0}" -eq 0 ]; then
    # A later manual failure is not the original workflow's successful exit.
    (set -o noclobber; printf 'failure_rc=%s\nphase=%s\n' "${rc}" "${OP_PHASE}" > "${OP_DIR}/post-workflow.failure") || _op_error 'falha posterior ja registrada; evidencia original preservada'
  else
    [ "${OP_ORIGINAL_RC:-0}" -ne 0 ] || OP_ORIGINAL_RC="${rc}"
  fi
  if [ -n "${OP_CHILD_PID:-}" ] || [ "${OP_AMBIGUOUS:-0}" -eq 1 ] || [ "${OP_RECONCILE_ONLY:-0}" -eq 1 ]; then
    OP_RESULT=INCOMPLETE OP_PHASE=RECOVERY_INCOMPLETE
  elif [ "${OP_MUTATED}" -eq 0 ] || [ "${OP_SNAPSHOT_READY}" -eq 0 ]; then
    OP_RESULT=ABORTED OP_PHASE=ABORTED
  else
    OP_RECOVERING=1 OP_PHASE=RECOVERING
    _op_state || recovery_rc=$?
    if [ "${recovery_rc}" -eq 0 ]; then _op_restore || recovery_rc=$?; fi
    if [ "${recovery_rc}" -eq 0 ]; then
      OP_RESULT=ROLLED_BACK OP_PHASE=ROLLED_BACK
    else
      OP_RESULT=INCOMPLETE OP_PHASE=RECOVERY_INCOMPLETE
      _op_error "recuperacao incompleta (rc=${recovery_rc}); reconciliacao explicita necessaria"
    fi
  fi
  _op_state || _op_error 'nao foi possivel persistir resultado; tratar como estado ambiguo'
  if declare -F cleanup_database_gate >/dev/null; then
    cleanup_database_gate || _op_error 'limpeza do snapshot temporario falhou'
  fi
  _op_stop_watch || _op_error 'vigia nao terminou normalmente'
  OP_ACTIVE=0
  # Closing our copy does not release a lock inherited by a living mutator.
  exec {OP_LOCK_FD}>&-
  exit "${rc}"
}
op_finish() {
  [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
  [ -z "${OP_CHILD_PID:-}" ] && [ "${OP_AMBIGUOUS:-0}" -eq 0 ] || return 1
  [ "$(readlink -e "${OP_ROOT}/current")" = "${OP_ROOT}/releases/${OP_CANDIDATE}" ] || return 1
  _op_verify_runtime "${OP_CANDIDATE}" "${OP_DIR}/candidate.images.tsv" || return 1
  (cd "${OP_SECRETS}" && sha256sum -c --status "${OP_DIR}/candidate.config.sha256") || return 1
  [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
  OP_RESULT=COMPLETED OP_PHASE=COMPLETED
  _op_state || return 1
  _op_stop_watch || return 1
  [ "${OP_CANCEL_RC:-0}" -eq 0 ] || return "${OP_CANCEL_RC}"
  OP_ACTIVE=0
  trap - EXIT INT TERM HUP PIPE
  exec {OP_LOCK_FD}>&-
}
_op_manual() {
  local mode="$1" root="$2" id="$3" expected="${4:-}" confirmation="${5:-}" state old_pid old_start old_boot old_result
  _op_id "${id}" || return 2
  if [ "${mode}" = rollback ]; then confirmation="${4:-}"; fi
  [ "${confirmation}" = --confirm-daemon-quiescent ] || {
    _op_error 'confirmacao explicita da quiescencia do Docker daemon e obrigatoria; PID ausente nao e prova'
    return 2
  }
  _op_lock "${root}" || return $?
  state="${OP_ROOT}/operations/active.state"
  _op_validate_state "${state}" || return 76
  [ "$(_op_read id "${state}")" = "${id}" ] || return 76
  OP_ID="${id}" OP_DIR="${OP_ROOT}/operations/${id}"
  [ -d "${OP_DIR}" ] && [ ! -L "${OP_DIR}" ] || return 76
  OP_CANDIDATE="$(_op_read candidate "${state}")" OP_PREVIOUS_SHA="$(_op_read previous "${state}")"
  _op_sha "${OP_CANDIDATE}" && _op_sha "${OP_PREVIOUS_SHA}" || return 76
  OP_PREVIOUS_RELEASE="${OP_ROOT}/releases/${OP_PREVIOUS_SHA}"
  old_pid="$(_op_read pid "${state}")" old_start="$(_op_read start "${state}")" old_boot="$(_op_read boot "${state}")"
  OP_BOOT="${old_boot}"
  ! _op_alive "${old_pid}" "${old_start}" || return 75
  OP_CHILD_PID="$(_op_read child_pid "${state}")" OP_CHILD_START="$(_op_read child_start "${state}")"
  ! _op_alive "${OP_CHILD_PID}" "${OP_CHILD_START}" || return 75
  OP_PID="${BASHPID}"
  OP_START="$(_op_start "${OP_PID}")" OP_BOOT="$(cat /proc/sys/kernel/random/boot_id)"
  OP_CHILD_PID= OP_CHILD_START= OP_CANCEL_RC=0 OP_ORIGINAL_RC="$(_op_read original_rc "${state}")"
  old_result="$(_op_read result "${state}")"
  OP_POST_WORKFLOW=0
  [ "${OP_ORIGINAL_RC}" -ne 0 ] || OP_POST_WORKFLOW=1
  OP_MUTATED="$(_op_read mutated "${state}")" OP_MIGRATION_STARTED="$(_op_read migration_started "${state}")"
  OP_SNAPSHOT_READY="$(_op_read snapshot "${state}")" OP_AMBIGUOUS=0 OP_RECOVERING=1 OP_ACTIVE=1 OP_RECONCILE_ONLY=0
  [ "${mode}" != reconcile ] || OP_RECONCILE_ONLY=1
  # Classify terminal reobservation before traps/journal/profile checks: a failure
  # in those checks must not authorize another restoration or functional window.
  if [ "${mode}" = rollback ] && [[ "${old_result}" = ROLLED_BACK || "${old_result}" = RECONCILED ]] \
    && [ "$(readlink -e "${OP_ROOT}/current")" = "${OP_PREVIOUS_RELEASE}" ]; then
    OP_RECONCILE_ONLY=1
  fi
  OP_PHASE=RECONCILING OP_RESULT=
  _op_install_traps
  _op_state || return 1
  # Older journals lacked this file. Reconstruct only the explicitly known
  # release contract under the same lock; never derive a profile from HTTP.
  [ -e "${OP_DIR}/previous.health-profile" ] || _op_snapshot_profile "${OP_PREVIOUS_SHA}" "${OP_DIR}/previous.health-profile" || return 1
  if [ "${mode}" = rollback ]; then
    OP_PHASE=RECOVERING
    _op_state || return 1
    if [[ "${old_result}" = ROLLED_BACK || "${old_result}" = RECONCILED ]] && [ "$(readlink -e "${OP_ROOT}/current")" = "${OP_PREVIOUS_RELEASE}" ]; then
      (cd "${OP_SECRETS}" && sha256sum -c --status "${OP_DIR}/config.sha256") || return 1
      op_smoke "${OP_PREVIOUS_SHA}" || return 1
    else
      _op_restore || { OP_AMBIGUOUS=1; return 1; }
    fi
    OP_PHASE=ROLLED_BACK OP_RESULT=ROLLED_BACK
  else
    _op_sha "${expected}" || return 2
    [[ "${expected}" = "${OP_PREVIOUS_SHA}" || "${expected}" = "${OP_CANDIDATE}" ]] || return 2
    [ "$(readlink -e "${OP_ROOT}/current")" = "${OP_ROOT}/releases/${expected}" ] || return 1
    local hashes="${OP_DIR}/config.sha256"
    [ "${expected}" != "${OP_CANDIDATE}" ] || hashes="${OP_DIR}/candidate.config.sha256"
    (cd "${OP_SECRETS}" && sha256sum -c --status "${hashes}") || return 1
    op_smoke "${expected}" || return 1
    OP_PHASE=RECONCILED OP_RESULT=RECONCILED
  fi
  _op_state || return 1
  OP_ACTIVE=0
  trap - EXIT INT TERM HUP PIPE
  exec {OP_LOCK_FD}>&-
  printf 'RECOVERY id=%s result=%s database_restored=false migration_started=%s\n' "${OP_ID}" "${OP_RESULT}" "${OP_MIGRATION_STARTED}"
}
if [[ "${BASH_SOURCE[0]}" = "$0" ]]; then
  set -euo pipefail
  case "${1:-}" in
    rollback|reconcile) _op_manual "$@" ;;
    *) _op_error 'uso: rollback ROOT OPERATION_ID --confirm-daemon-quiescent | reconcile ROOT OPERATION_ID EXPECTED_SHA --confirm-daemon-quiescent'; exit 2 ;;
  esac
fi
