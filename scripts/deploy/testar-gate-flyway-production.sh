#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
gate="${script_dir}/validar-gate-flyway-production.sh"
temp_dir="$(mktemp -d)"
trap 'rm -rf -- "${temp_dir}"' EXIT

expect_pass() {
  local name="$1"
  shift
  if ! "$@" > "${temp_dir}/${name}.log" 2>&1; then
    echo "FALHA: ${name} deveria passar" >&2
    cat "${temp_dir}/${name}.log" >&2
    exit 1
  fi
  echo "PASS: ${name}"
}

expect_fail() {
  local name="$1"
  shift
  if "$@" > "${temp_dir}/${name}.log" 2>&1; then
    echo "FALHA: ${name} deveria falhar" >&2
    cat "${temp_dir}/${name}.log" >&2
    exit 1
  fi
  echo "PASS: ${name} rejeitado"
}

migrations="${temp_dir}/migrations"
mkdir -p "$migrations"
touch "${migrations}/V001__base.sql" "${migrations}/V051__estado_atual.sql"
expect_pass versao_esperada_051 bash -c "test \"\$(bash '$gate' expected '$migrations')\" = 051"
expect_pass banco_051_repositorio_051 \
  bash "$gate" before 051 051 0 1 NOT_REQUIRED

touch "${migrations}/V052__indice_busca.sql"
expect_pass versao_esperada_052 bash -c "test \"\$(bash '$gate' expected '$migrations')\" = 052"
expect_pass banco_051_repositorio_052_inspecao \
  bash "$gate" inspect 052 051 0 0 NOT_CHECKED
expect_pass banco_051_repositorio_052_backup \
  bash "$gate" before 052 051 0 0 VALIDATED
expect_pass banco_051_repositorio_052_final \
  bash "$gate" after 052 052 0 1 VALIDATED

expect_fail backup_ausente \
  bash "$gate" before 052 051 0 0 MISSING
expect_fail backup_invalido \
  bash "$gate" before 052 051 0 0 CORRUPTED
expect_fail migration_falha \
  bash "$gate" inspect 052 051 1 0 NOT_CHECKED
expect_fail banco_acima_repositorio \
  bash "$gate" inspect 051 052 0 0 NOT_CHECKED
expect_fail versao_final_divergente \
  bash "$gate" after 052 051 0 0 VALIDATED

touch "${migrations}/R__refresh_busca.sql"
expect_pass repeatable_nao_altera_versao \
  bash -c "test \"\$(bash '$gate' expected '$migrations')\" = 052"

echo 'FLYWAY_DYNAMIC_GATE_TESTS=PASS'
