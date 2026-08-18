#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Uso: $0 SNAPSHOT_ANTES SNAPSHOT_DEPOIS VERSAO_FLYWAY_ESPERADA" >&2
  exit 2
fi

snapshot_before="$1"
snapshot_after="$2"
expected_flyway="$3"

# Guardrail final contra perda macica. Pequenas reducoes continuam sujeitas aos
# invariantes relacionais; uma queda simultaneamente >= 50 linhas e >= 20% falha.
readonly MASS_LOSS_MINIMUM_ROWS=50
readonly MASS_LOSS_PERCENT=20

readonly -a CRITICAL_TABLES=(
  anuncio
  arquivo_midia
  ativacao_beneficio
  credencial_usuario
  documento_usuario
  movimento_credito
  usuario
)

readonly -a ANUNCIO_STATUSES=(
  APROVADO
  BLOQUEADO
  PAUSADO
  PENDENTE_REVISAO
  PUBLICADO
  RASCUNHO
  REJEITADO
  REMOVIDO
)

readonly -a REQUIRED_INVARIANTS=(
  tabelas_criticas_ausentes
  constraints_nao_validadas
  anuncio_sem_usuario
  anuncio_midia_sem_anuncio
  anuncio_midia_sem_arquivo
  credencial_sem_usuario
  documento_sem_usuario
  documento_sem_arquivo
  ativacao_sem_usuario
  ativacao_sem_beneficio
  movimento_sem_usuario
  movimento_saldo_incoerente
  movimento_idempotencia_duplicada
  saldo_credito_sem_usuario
  saldo_credito_negativo
)

die() {
  echo "ERRO_GATE_BANCO: $*" >&2
  exit 1
}

is_non_negative_integer() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

load_snapshot() {
  local file="$1"
  local array_name="$2"
  local -n output="$array_name"
  local kind name value extra key

  [[ -f "$file" ]] || die "snapshot ausente"
  [[ -s "$file" ]] || die "snapshot vazio"

  while IFS='|' read -r kind name value extra || [[ -n "${kind}${name}${value}${extra}" ]]; do
    kind="${kind%$'\r'}"
    name="${name%$'\r'}"
    value="${value%$'\r'}"
    extra="${extra%$'\r'}"

    [[ -n "$kind" && -n "$name" && -n "$value" ]] || die "linha de snapshot incompleta"
    [[ -z "$extra" ]] || die "linha de snapshot possui campos inesperados"
    [[ "$kind" =~ ^(META|TABLE|STATUS|INVARIANT|ACTIVITY)$ ]] || die "tipo de snapshot desconhecido"
    [[ "$name" =~ ^[A-Za-z0-9_.-]+$ ]] || die "nome de metrica invalido"

    if [[ "$kind" != "META" ]]; then
      is_non_negative_integer "$value" || die "contagem nao numerica em ${kind}/${name}"
    fi

    key="${kind}|${name}"
    [[ -z "${output[$key]+present}" ]] || die "entrada duplicada em ${kind}/${name}"
    output["$key"]="$value"
  done < "$file"
}

require_key() {
  local array_name="$1"
  local key="$2"
  local -n values="$array_name"
  [[ -n "${values[$key]+present}" ]] || die "entrada obrigatoria ausente: ${key}"
}

validate_snapshot_integrity() {
  local label="$1"
  local array_name="$2"
  local -n values="$array_name"
  local invariant status status_sum=0

  require_key "$array_name" 'META|database_identity'
  require_key "$array_name" 'META|flyway_version'
  require_key "$array_name" 'META|health'
  [[ "${values['META|flyway_version']}" == "$expected_flyway" ]] \
    || die "Flyway inesperado no snapshot ${label}"
  [[ "${values['META|health']}" == 'UP' ]] \
    || die "health indisponivel no snapshot ${label}"

  for invariant in "${REQUIRED_INVARIANTS[@]}"; do
    require_key "$array_name" "INVARIANT|${invariant}"
    [[ "${values["INVARIANT|${invariant}"]}" -eq 0 ]] \
      || die "invariante estrutural violado no snapshot ${label}: ${invariant}"
  done

  for status in "${ANUNCIO_STATUSES[@]}"; do
    require_key "$array_name" "STATUS|anuncio.${status}"
    status_sum=$((status_sum + values["STATUS|anuncio.${status}"]))
  done
  require_key "$array_name" 'TABLE|anuncio'
  [[ "$status_sum" -eq "${values['TABLE|anuncio']}" ]] \
    || die "total de anuncios diverge da soma dos estados canonicos no snapshot ${label}"
}

declare -A before=()
declare -A after=()
load_snapshot "$snapshot_before" before
load_snapshot "$snapshot_after" after
validate_snapshot_integrity antes before
validate_snapshot_integrity depois after

[[ "${before['META|database_identity']}" == "${after['META|database_identity']}" ]] \
  || die "instancia ou database mudou durante o deploy"

for table in "${CRITICAL_TABLES[@]}"; do
  require_key before "TABLE|${table}"
  require_key after "TABLE|${table}"

  before_count="${before["TABLE|${table}"]}"
  after_count="${after["TABLE|${table}"]}"
  delta=$((after_count - before_count))

  if (( before_count > 0 && after_count == 0 )); then
    die "tabela critica ${table} foi esvaziada"
  fi

  if [[ "$table" == 'movimento_credito' ]] && (( after_count < before_count )); then
    die "ledger canonico perdeu movimentos"
  fi

  if (( after_count < before_count )); then
    loss=$((before_count - after_count))
    if (( loss >= MASS_LOSS_MINIMUM_ROWS && loss * 100 >= before_count * MASS_LOSS_PERCENT )); then
      die "queda destrutiva detectada em ${table}"
    fi
  fi

  if (( delta != 0 )); then
    printf 'ATIVIDADE_LEGITIMA_OBSERVADA tabela=%s antes=%d depois=%d delta=%+d\n' \
      "$table" "$before_count" "$after_count" "$delta"
  fi
done

changed_statuses=0
for status in "${ANUNCIO_STATUSES[@]}"; do
  before_status="${before["STATUS|anuncio.${status}"]}"
  after_status="${after["STATUS|anuncio.${status}"]}"
  if [[ "$before_status" -ne "$after_status" ]]; then
    changed_statuses=$((changed_statuses + 1))
  fi
done
if (( changed_statuses > 0 )); then
  printf 'ATIVIDADE_LEGITIMA_OBSERVADA categoria=status_anuncio estados_alterados=%d\n' "$changed_statuses"
fi

for key in "${!before[@]}"; do
  [[ "$key" == ACTIVITY\|* ]] || continue
  require_key after "$key"
  if [[ "${before[$key]}" -ne "${after[$key]}" ]]; then
    printf 'ATIVIDADE_LEGITIMA_OBSERVADA categoria=%s antes=%d depois=%d delta=%+d\n' \
      "${key#ACTIVITY|}" "${before[$key]}" "${after[$key]}" "$((after[$key] - before[$key]))"
  fi
done

echo 'DATABASE_SAFETY_GATE=PASS'
