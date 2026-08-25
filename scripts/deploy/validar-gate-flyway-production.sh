#!/usr/bin/env bash
set -euo pipefail

die() {
  echo "ERRO_GATE_FLYWAY: $*" >&2
  exit 1
}

normalize_version() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]] || die "versao Flyway invalida"
  printf '%d' "$((10#${value}))"
}

validate_count() {
  local label="$1"
  local value="$2"
  [[ "$value" =~ ^[0-9]+$ ]] || die "${label} deve ser inteiro nao negativo"
}

expected_version() {
  local migration_dir="$1"
  local file name version_text numeric highest=-1 selected=''
  declare -A versions=()

  [[ -d "$migration_dir" ]] || die "diretorio de migrations ausente"

  while IFS= read -r -d '' file; do
    name="$(basename "$file")"
    if [[ "$name" =~ ^R__.+\.sql$ ]]; then
      continue
    fi
    [[ "$name" =~ ^V([0-9]+)__.+\.sql$ ]] || die "migration versionada com nome invalido"
    version_text="${BASH_REMATCH[1]}"
    numeric="$(normalize_version "$version_text")"
    [[ -z "${versions[$numeric]+present}" ]] || die "versao Flyway numerica duplicada"
    versions["$numeric"]="$name"
    if (( numeric > highest )); then
      highest="$numeric"
      selected="$version_text"
    fi
  done < <(find "$migration_dir" -maxdepth 1 -type f -name '*.sql' -print0)

  [[ -n "$selected" ]] || die "nenhuma migration versionada encontrada"
  printf '%s\n' "$selected"
}

validate_state() {
  local mode="$1"
  local expected_raw="$2"
  local applied_raw="$3"
  local failed_count="$4"
  local expected_count="$5"
  local backup_status="$6"
  local expected applied pending

  expected="$(normalize_version "$expected_raw")"
  validate_count "quantidade de migrations falhas" "$failed_count"
  validate_count "quantidade da versao esperada" "$expected_count"
  [[ "$failed_count" -eq 0 ]] || die "existe migration falha no historico"

  if [[ "$applied_raw" == 'AUSENTE' ]]; then
    applied=0
  else
    applied="$(normalize_version "$applied_raw")"
  fi

  (( applied <= expected )) || die "banco acima da versao do repositorio"
  if (( applied < expected )); then
    pending=true
    [[ "$expected_count" -eq 0 ]] || die "versao esperada aplicada fora de ordem"
  else
    pending=false
    [[ "$expected_count" -eq 1 ]] || die "versao esperada deve estar aplicada exatamente uma vez"
  fi

  case "$mode" in
    inspect)
      printf 'MIGRATIONS_PENDING=%s\n' "$pending"
      printf 'FLYWAY_INSPECTION=PASS\n'
      ;;
    before)
      if [[ "$pending" == true ]]; then
        [[ "$backup_status" == 'VALIDATED' ]] || die "backup validado obrigatorio antes do Flyway"
      else
        [[ "$backup_status" == 'NOT_REQUIRED' || "$backup_status" == 'VALIDATED' ]] \
          || die "estado de backup invalido"
      fi
      printf 'MIGRATIONS_PENDING=%s\n' "$pending"
      printf 'FLYWAY_PRE_MIGRATION_GATE=PASS\n'
      ;;
    after)
      [[ "$pending" == false ]] || die "versao final diferente da esperada"
      [[ "$backup_status" == 'NOT_REQUIRED' || "$backup_status" == 'VALIDATED' ]] \
        || die "estado de backup invalido"
      printf 'FLYWAY_POST_MIGRATION_GATE=PASS\n'
      ;;
    *)
      die "modo desconhecido"
      ;;
  esac
}

if [[ $# -lt 1 ]]; then
  die "informe o modo"
fi

case "$1" in
  expected)
    [[ $# -eq 2 ]] || die "uso: $0 expected DIRETORIO_MIGRATIONS"
    expected_version "$2"
    ;;
  inspect|before|after)
    [[ $# -eq 6 ]] || die "uso: $0 MODO ESPERADA APLICADA FALHAS CONTAGEM_ESPERADA BACKUP"
    validate_state "$@"
    ;;
  *)
    die "modo desconhecido"
    ;;
esac
