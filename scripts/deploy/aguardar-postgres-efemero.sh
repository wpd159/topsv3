#!/usr/bin/env bash
set -euo pipefail

die() {
  echo "ERRO_POSTGRES_EFEMERO: $*" >&2
  exit 1
}

if [[ $# -ne 3 ]]; then
  die "uso: $0 CONTAINER USUARIO BANCO"
fi

container="$1"
database_user="$2"
database="$3"
max_attempts="${POSTGRES_EFEMERO_MAX_ATTEMPTS:-120}"
stable_probes_required="${POSTGRES_EFEMERO_STABLE_PROBES:-3}"
timeout_seconds="${POSTGRES_EFEMERO_TIMEOUT_SECONDS:-120}"
[[ "$timeout_seconds" =~ ^[0-9]+$ && "$timeout_seconds" -ge 1 && "$timeout_seconds" -le 300 ]] || die "deadline invalido"
# /proc/uptime is elapsed boot time on both Linux and MSYS; unlike SECONDS,
# it is independent of wall-clock adjustments. Failure is fail-closed.
monotonic_millis() {
  local elapsed idle whole fraction
  IFS=' ' read -r elapsed idle < /proc/uptime || return 1
  [[ "$elapsed" =~ ^[0-9]+[.][0-9]+$ ]] || return 1
  whole="${elapsed%%.*}" fraction="${elapsed#*.}000"
  printf '%s\n' "$((10#$whole * 1000 + 10#${fraction:0:3}))"
}
ready_started_ms="$(monotonic_millis)" || die "relogio monotonic indisponivel"
ready_deadline_ms=$((ready_started_ms + timeout_seconds * 1000))
init_marker='PostgreSQL init process complete; ready for start up.'

[[ "$container" =~ ^[A-Za-z0-9_.-]+$ ]] || die "container invalido"
[[ "$database_user" =~ ^[A-Za-z0-9_.-]+$ ]] || die "usuario invalido"
[[ "$database" =~ ^[A-Za-z0-9_.-]+$ ]] || die "banco invalido"
[[ "$max_attempts" =~ ^[0-9]+$ && "$max_attempts" -ge 1 && "$max_attempts" -le 300 ]] \
  || die "limite de tentativas invalido"
[[ "$stable_probes_required" =~ ^[0-9]+$ \
  && "$stable_probes_required" -ge 3 \
  && "$stable_probes_required" -le 10 ]] \
  || die "quantidade de probes estaveis invalida"

bounded_docker() {
  local now remaining limit limit_seconds
  now="$(monotonic_millis)" || return 124
  remaining=$((ready_deadline_ms - now))
  (( remaining > 1000 )) || return 124
  limit=$((remaining - 1000))
  (( limit <= 5000 )) || limit=5000
  printf -v limit_seconds '%d.%03d' "$((limit / 1000))" "$((limit % 1000))"
  # The final second is reserved for CLI termination, inside the same deadline.
  # A timed-out Docker request is not proof that its daemon work has drained.
  timeout --kill-after=1s "${limit_seconds}s" docker "$@"
}

bounded_docker inspect "$container" >/dev/null 2>&1 || die "container nao encontrado ou prazo esgotado"

sanitized_diagnostics() {
  local logs diagnostic_running init_events ready_events shutdown_events
  logs="${last_logs:-}"
  diagnostic_running="${running:-indisponivel}"
  init_events="$(grep -Fc -- "$init_marker" <<< "$logs" || true)"
  ready_events="$(grep -Fc -- 'database system is ready to accept connections' <<< "$logs" || true)"
  shutdown_events="$(grep -Fc -- 'database system is shut down' <<< "$logs" || true)"
  printf 'POSTGRES_EFEMERO_DIAGNOSTICO running=%s init=%s ready=%s shutdown=%s\n' \
    "$diagnostic_running" "$init_events" "$ready_events" "$shutdown_events" >&2
}

marker_seen=0
stable_probes=0
for attempt in $(seq 1 "$max_attempts"); do
  now="$(monotonic_millis)" || die "relogio monotonic indisponivel"
  (( now < ready_deadline_ms - 1000 )) || break
  running="$(bounded_docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)"
  if [[ "$running" != true ]]; then
    sanitized_diagnostics
    die "container deixou de executar"
  fi

  if [[ "$marker_seen" -eq 0 ]]; then
    if last_logs="$(bounded_docker logs "$container" 2>&1)" && grep -Fq -- "$init_marker" <<< "$last_logs"; then marker_seen=1; fi
  fi

  if [[ "$marker_seen" -eq 1 ]]; then
    probe_result=""
    if bounded_docker exec "$container" pg_isready \
        --host=127.0.0.1 --port=5432 --username="$database_user" --dbname="$database" >/dev/null 2>&1 \
      && probe_result="$(bounded_docker exec --env PGCONNECT_TIMEOUT=2 --env 'PGOPTIONS=-c statement_timeout=2000' "$container" psql \
        --no-psqlrc --set=ON_ERROR_STOP=1 --no-password \
        --host=127.0.0.1 --port=5432 --username="$database_user" --dbname="$database" -At \
        --command 'SELECT 1' </dev/null)" \
      && [[ "$probe_result" == 1 ]] \
      && [[ "$(bounded_docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)" == true ]] \
      && now="$(monotonic_millis)" \
      && (( now < ready_deadline_ms )); then
      stable_probes=$((stable_probes + 1))
      if [[ "$stable_probes" -ge "$stable_probes_required" ]]; then
        now="$(monotonic_millis)" || die "relogio monotonic indisponivel"
        (( now < ready_deadline_ms )) || break
        printf 'POSTGRES_EFEMERO_READY=PASS probes=%s deadline_s=%s\n' "$stable_probes" "$timeout_seconds"
        exit 0
      fi
    else
      stable_probes=0
    fi
  fi

  now="$(monotonic_millis)" || die "relogio monotonic indisponivel"
  remaining_ms=$((ready_deadline_ms - now))
  (( remaining_ms > 0 )) || break
  (( remaining_ms <= 1000 )) || remaining_ms=1000
  printf -v pause_seconds '%d.%03d' "$((remaining_ms / 1000))" "$((remaining_ms % 1000))"
  sleep "$pause_seconds"
done

sanitized_diagnostics
die "PostgreSQL efemero nao ficou pronto via TCP e SQL em ${timeout_seconds}s; inicializacao final nao estabilizou"
