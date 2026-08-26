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

docker inspect "$container" >/dev/null 2>&1 || die "container nao encontrado"

sanitized_diagnostics() {
  local logs running init_events ready_events shutdown_events
  logs="$(docker logs "$container" 2>&1 || true)"
  running="$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || printf 'indisponivel')"
  init_events="$(grep -Fc -- "$init_marker" <<< "$logs" || true)"
  ready_events="$(grep -Fc -- 'database system is ready to accept connections' <<< "$logs" || true)"
  shutdown_events="$(grep -Fc -- 'database system is shut down' <<< "$logs" || true)"
  printf 'POSTGRES_EFEMERO_DIAGNOSTICO running=%s init=%s ready=%s shutdown=%s\n' \
    "$running" "$init_events" "$ready_events" "$shutdown_events" >&2
}

marker_seen=0
stable_probes=0
for attempt in $(seq 1 "$max_attempts"); do
  running="$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)"
  if [[ "$running" != true ]]; then
    sanitized_diagnostics
    die "container deixou de executar"
  fi

  if [[ "$marker_seen" -eq 0 ]] \
    && docker logs "$container" 2>&1 | grep -Fq -- "$init_marker"; then
    marker_seen=1
  fi

  if [[ "$marker_seen" -eq 1 ]]; then
    probe_result=""
    if docker exec "$container" pg_isready \
        -U "$database_user" -d "$database" >/dev/null 2>&1 \
      && probe_result="$(docker exec "$container" psql \
        --no-psqlrc --set=ON_ERROR_STOP=1 \
        -U "$database_user" -d "$database" -At \
        --command 'SELECT 1;' </dev/null 2>/dev/null)" \
      && [[ "$probe_result" == 1 ]] \
      && [[ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)" == true ]]; then
      stable_probes=$((stable_probes + 1))
      if [[ "$stable_probes" -ge "$stable_probes_required" ]]; then
        printf 'POSTGRES_EFEMERO_READY=PASS probes=%s\n' "$stable_probes"
        exit 0
      fi
    else
      stable_probes=0
    fi
  fi

  sleep 1
done

sanitized_diagnostics
die "inicializacao final nao estabilizou"
