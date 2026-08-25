#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
validator="${script_dir}/validar-gate-banco-production.sh"
temp_dir="$(mktemp -d)"
trap 'rm -rf -- "${temp_dir}"' EXIT

write_snapshot() {
  local destination="$1"
  local users="$2"
  local anuncios="$3"
  local pendentes="$4"
  local publicados="$5"
  local metricas="$6"
  local flyway="$7"
  local health="$8"
  local arquivos="$9"
  local movimentos="${10}"

  cat > "$destination" <<EOF
ACTIVITY|eventos_metricas_estimados|${metricas}
INVARIANT|anuncio_midia_sem_anuncio|0
INVARIANT|anuncio_midia_sem_arquivo|0
INVARIANT|anuncio_sem_usuario|0
INVARIANT|ativacao_sem_beneficio|0
INVARIANT|ativacao_sem_usuario|0
INVARIANT|constraints_nao_validadas|0
INVARIANT|credencial_sem_usuario|0
INVARIANT|documento_sem_arquivo|0
INVARIANT|documento_sem_usuario|0
INVARIANT|movimento_idempotencia_duplicada|0
INVARIANT|movimento_saldo_incoerente|0
INVARIANT|movimento_sem_usuario|0
INVARIANT|saldo_credito_negativo|0
INVARIANT|saldo_credito_sem_usuario|0
INVARIANT|tabelas_criticas_ausentes|0
META|database_identity|database:1:system
META|flyway_version|${flyway}
META|health|${health}
STATUS|anuncio.APROVADO|0
STATUS|anuncio.BLOQUEADO|0
STATUS|anuncio.PAUSADO|0
STATUS|anuncio.PENDENTE_REVISAO|${pendentes}
STATUS|anuncio.PUBLICADO|${publicados}
STATUS|anuncio.RASCUNHO|0
STATUS|anuncio.REJEITADO|0
STATUS|anuncio.REMOVIDO|0
TABLE|anuncio|${anuncios}
TABLE|arquivo_midia|${arquivos}
TABLE|ativacao_beneficio|200
TABLE|credencial_usuario|${users}
TABLE|documento_usuario|200
TABLE|movimento_credito|${movimentos}
TABLE|usuario|${users}
EOF
}

expect_pass() {
  local name="$1"
  local before="$2"
  local after="$3"
  if ! bash "$validator" "$before" "$after" 051 > "${temp_dir}/${name}.log" 2>&1; then
    echo "FALHA: ${name} deveria passar" >&2
    cat "${temp_dir}/${name}.log" >&2
    exit 1
  fi
  echo "PASS: ${name}"
}

expect_fail() {
  local name="$1"
  local before="$2"
  local after="$3"
  if bash "$validator" "$before" "$after" 051 > "${temp_dir}/${name}.log" 2>&1; then
    echo "FALHA: ${name} deveria falhar" >&2
    cat "${temp_dir}/${name}.log" >&2
    exit 1
  fi
  echo "PASS: ${name} rejeitado"
}

baseline="${temp_dir}/baseline.snapshot"
write_snapshot "$baseline" 100 100 70 30 35000 051 UP 1000 500

growth="${temp_dir}/growth.snapshot"
write_snapshot "$growth" 101 100 70 30 35000 051 UP 1000 500
expect_pass crescimento_legitimo "$baseline" "$growth"

status_change="${temp_dir}/status-change.snapshot"
write_snapshot "$status_change" 100 100 60 40 35000 051 UP 1000 500
expect_pass mudanca_status_anuncio "$baseline" "$status_change"

metric_growth="${temp_dir}/metric-growth.snapshot"
write_snapshot "$metric_growth" 100 100 70 30 35150 051 UP 1000 500
expect_pass crescimento_metricas "$baseline" "$metric_growth"

small_reduction="${temp_dir}/small-reduction.snapshot"
write_snapshot "$small_reduction" 100 100 70 30 35000 051 UP 995 500
expect_pass reducao_operacional_pequena "$baseline" "$small_reduction"

truncated="${temp_dir}/truncated.snapshot"
write_snapshot "$truncated" 100 100 70 30 35000 051 UP 0 500
expect_fail truncamento "$baseline" "$truncated"

mass_loss="${temp_dir}/mass-loss.snapshot"
write_snapshot "$mass_loss" 100 100 70 30 35000 051 UP 700 500
expect_fail perda_macica "$baseline" "$mass_loss"

missing_table="${temp_dir}/missing-table.snapshot"
grep -v '^TABLE|arquivo_midia|' "$baseline" > "$missing_table"
expect_fail tabela_critica_ausente "$baseline" "$missing_table"

unexpected_flyway="${temp_dir}/unexpected-flyway.snapshot"
write_snapshot "$unexpected_flyway" 100 100 70 30 35000 052 UP 1000 500
expect_fail flyway_inesperado "$baseline" "$unexpected_flyway"

flyway_transition="${temp_dir}/flyway-transition.snapshot"
write_snapshot "$flyway_transition" 100 100 70 30 35000 052 UP 1000 500
if ! bash "$validator" "$baseline" "$flyway_transition" 051 052 \
  > "${temp_dir}/transicao-flyway.log" 2>&1; then
  echo "FALHA: transicao Flyway 051 para 052 deveria passar" >&2
  cat "${temp_dir}/transicao-flyway.log" >&2
  exit 1
fi
echo "PASS: transicao_flyway_051_052"

health_down="${temp_dir}/health-down.snapshot"
write_snapshot "$health_down" 100 100 70 30 35000 051 DOWN 1000 500
expect_fail health_indisponivel "$baseline" "$health_down"

database_changed="${temp_dir}/database-changed.snapshot"
sed 's/^META|database_identity|.*/META|database_identity|other:2:system/' "$baseline" > "$database_changed"
expect_fail database_trocado "$baseline" "$database_changed"

orphan_relation="${temp_dir}/orphan-relation.snapshot"
sed 's/^INVARIANT|credencial_sem_usuario|0$/INVARIANT|credencial_sem_usuario|1/' "$baseline" > "$orphan_relation"
expect_fail relacionamento_orfao "$baseline" "$orphan_relation"

ledger_reduction="${temp_dir}/ledger-reduction.snapshot"
write_snapshot "$ledger_reduction" 100 100 70 30 35000 051 UP 1000 499
expect_fail reducao_ledger "$baseline" "$ledger_reduction"

echo 'DATABASE_SAFETY_GATE_TESTS=PASS'
