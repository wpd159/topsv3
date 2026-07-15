\set ON_ERROR_STOP on

-- Reconciliacao repetivel do saldo operacional aceito no staging.
-- Requer snapshot_id e snapshot_fingerprint do mesmo dry-run.

DROP TABLE IF EXISTS dryrun_ledger_context;
CREATE TEMP TABLE dryrun_ledger_context AS
SELECT
  md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id,
  :'snapshot_id'::text AS snapshot_id,
  :'snapshot_fingerprint'::text AS snapshot_fingerprint;

DO $$
DECLARE
  contexto record;
  fingerprint_registrado text;
BEGIN
  SELECT * INTO contexto FROM dryrun_ledger_context;

  SELECT resumo_json ->> 'snapshotFingerprint'
  INTO fingerprint_registrado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF fingerprint_registrado IS NULL THEN
    RAISE EXCEPTION 'snapshot nao registrado para reconciliacao do ledger';
  END IF;

  IF fingerprint_registrado <> contexto.snapshot_fingerprint THEN
    RAISE EXCEPTION 'snapshotId ja registrado com fingerprint divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_credito s
    JOIN movimento_credito m ON m.usuario_id = s.entidade_v3_id
    WHERE s.execucao_id = contexto.execucao_id
      AND s.tabela_origem = 'creditos_usuario'
      AND s.status = 'PROCESSADO'
      AND (s.payload_normalizado_json ->> 'saldoOperacional')::integer > 0
      AND m.tipo = 'MIGRACAO_SALDO_INICIAL'
      AND coalesce(m.metadata_json ->> 'snapshotFingerprint', '')
          <> contexto.snapshot_fingerprint
  ) THEN
    RAISE EXCEPTION 'usuario ja possui saldo inicial de outro snapshot';
  END IF;
END $$;

DROP TABLE IF EXISTS dryrun_ledger_resultado;
CREATE TEMP TABLE dryrun_ledger_resultado (movimentos_criados bigint NOT NULL);

WITH candidatos AS (
  SELECT
    s.entidade_v3_id AS usuario_id,
    (s.payload_normalizado_json ->> 'saldoOperacional')::integer AS saldo,
    s.payload_normalizado_json ->> 'usuarioLegadoHash' AS usuario_legado_hash,
    (s.payload_normalizado_json ->> 'divergenciaHistorica')::boolean AS divergencia_historica,
    c.execucao_id,
    c.snapshot_id,
    c.snapshot_fingerprint,
    e.iniciado_em AS snapshot_at
  FROM stg_credito s
  CROSS JOIN dryrun_ledger_context c
  JOIN importacao_execucao e ON e.id = c.execucao_id
  WHERE s.execucao_id = c.execucao_id
    AND s.tabela_origem = 'creditos_usuario'
    AND s.status = 'PROCESSADO'
    AND s.entidade_v3_id IS NOT NULL
    AND (s.payload_normalizado_json ->> 'saldoOperacional')::integer > 0
), inseridos AS (
INSERT INTO movimento_credito (
  id, usuario_id, tipo, direcao, quantidade, saldo_antes, saldo_depois,
  origem, referencia_tipo, referencia_id, idempotency_key,
  ator_usuario_id, observacao, criado_em, request_id, metadata_json
)
SELECT
  md5('ledger:saldo-inicial:' || c.snapshot_fingerprint || ':' || c.usuario_id)::uuid,
  c.usuario_id,
  'MIGRACAO_SALDO_INICIAL',
  'CREDITO',
  c.saldo,
  0,
  c.saldo,
  'IMPORTACAO',
  'SNAPSHOT_IMPORTACAO',
  c.execucao_id,
  'import:saldo-inicial:' || md5(c.snapshot_id || ':' || c.usuario_id),
  NULL,
  'Saldo inicial importado do snapshot operacional sanitizado.',
  c.snapshot_at,
  NULL,
  jsonb_build_object(
    'snapshotId', c.snapshot_id,
    'snapshotFingerprint', c.snapshot_fingerprint,
    'usuarioLegadoHash', c.usuario_legado_hash,
    'saldoImportado', c.saldo,
    'divergenciaHistorica', c.divergencia_historica,
    'versaoImportador', 'ledger-saldo-inicial-v1'
  )
FROM candidatos c
ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
RETURNING 1
)
INSERT INTO dryrun_ledger_resultado (movimentos_criados)
SELECT count(*) FROM inseridos;

DO $$
DECLARE
  contexto record;
  esperados bigint;
  criados bigint;
  saldo_esperado bigint;
  saldo_criado bigint;
BEGIN
  SELECT * INTO contexto FROM dryrun_ledger_context;

  SELECT count(*), coalesce(sum((payload_normalizado_json ->> 'saldoOperacional')::bigint), 0)
  INTO esperados, saldo_esperado
  FROM stg_credito
  WHERE execucao_id = contexto.execucao_id
    AND tabela_origem = 'creditos_usuario'
    AND status = 'PROCESSADO'
    AND (payload_normalizado_json ->> 'saldoOperacional')::integer > 0;

  SELECT count(*), coalesce(sum(quantidade), 0)
  INTO criados, saldo_criado
  FROM movimento_credito
  WHERE tipo = 'MIGRACAO_SALDO_INICIAL'
    AND referencia_id = contexto.execucao_id;

  IF criados <> esperados OR saldo_criado <> saldo_esperado THEN
    RAISE EXCEPTION 'reconciliacao do saldo inicial divergente: movimentos %, esperado %, saldo %, esperado %',
      criados, esperados, saldo_criado, saldo_esperado;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_credito s
    JOIN movimento_credito m ON m.usuario_id = s.entidade_v3_id
    WHERE s.execucao_id = contexto.execucao_id
      AND s.tabela_origem = 'creditos_usuario'
      AND (
        s.status <> 'PROCESSADO'
        OR (s.payload_normalizado_json ->> 'saldoOperacional')::integer <= 0
      )
      AND m.tipo = 'MIGRACAO_SALDO_INICIAL'
      AND m.referencia_id = contexto.execucao_id
  ) THEN
    RAISE EXCEPTION 'saldo inelegivel recebeu movimento inicial';
  END IF;
END $$;

SELECT
  'LEDGER_SALDO_INICIAL|novos=' || r.movimentos_criados
  || '|movimentos=' || count(*)
  || '|saldo=' || coalesce(sum(quantidade), 0)
FROM movimento_credito m
CROSS JOIN dryrun_ledger_context c
CROSS JOIN dryrun_ledger_resultado r
WHERE m.tipo = 'MIGRACAO_SALDO_INICIAL'
  AND c.execucao_id = m.referencia_id
GROUP BY r.movimentos_criados;
