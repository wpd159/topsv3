\set ON_ERROR_STOP on

-- Reconcilia apenas ativacoes Premium com origem comprovada no staging.
-- Requer snapshot_id e snapshot_fingerprint do dry-run saneado correspondente.

DROP TABLE IF EXISTS dryrun_premium_context;
CREATE TEMP TABLE dryrun_premium_context AS
SELECT
  md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id,
  :'snapshot_id'::text AS snapshot_id,
  :'snapshot_fingerprint'::text AS snapshot_fingerprint,
  count(*)::bigint AS movimentos_ledger_antes,
  md5(coalesce(string_agg(
    concat_ws(':', id, usuario_id, tipo, direcao, quantidade,
              saldo_antes, saldo_depois, criado_em),
    '|' ORDER BY id), '')) AS hash_ledger_antes
FROM movimento_credito;

DO $$
DECLARE
  contexto record;
  fingerprint_registrado text;
BEGIN
  SELECT * INTO contexto FROM dryrun_premium_context;

  SELECT resumo_json ->> 'snapshotFingerprint'
  INTO fingerprint_registrado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF fingerprint_registrado IS NULL THEN
    RAISE EXCEPTION 'snapshot nao registrado para reconciliacao Premium';
  END IF;

  IF fingerprint_registrado <> contexto.snapshot_fingerprint THEN
    RAISE EXCEPTION 'snapshotId ja registrado com fingerprint divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_premium s
    WHERE s.execucao_id = contexto.execucao_id
      AND s.status = 'PROCESSADO'
      AND (
        s.payload_normalizado_json ->> 'versaoImportador' <> 'premium-historico-v1'
        OR s.payload_normalizado_json ->> 'origemV3' <> 'CREDITO'
        OR s.payload_normalizado_json ->> 'codigoV3' IS NULL
        OR s.payload_normalizado_json ->> 'usuarioV3Id' IS NULL
        OR s.payload_normalizado_json ->> 'anuncioV3Id' IS NULL
        OR s.payload_normalizado_json ->> 'beneficioId' IS NULL
        OR s.payload_normalizado_json ->> 'grupoId' IS NULL
        OR s.payload_normalizado_json ->> 'ativacaoId' IS NULL
        OR (s.payload_normalizado_json ->> 'creditosCobrados')::integer <= 0
        OR (s.payload_normalizado_json ->> 'fim')::timestamptz
           <= (s.payload_normalizado_json ->> 'inicio')::timestamptz
      )
  ) THEN
    RAISE EXCEPTION 'staging Premium processado fora do contrato conservador';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_premium s
    JOIN grupo_ativacao_beneficio g
      ON g.idempotency_key = 'import:grupo-premium:' || s.id_origem
    WHERE s.execucao_id = contexto.execucao_id
      AND s.status = 'PROCESSADO'
      AND (
        g.id <> (s.payload_normalizado_json ->> 'grupoId')::uuid
        OR g.usuario_id <> (s.payload_normalizado_json ->> 'usuarioV3Id')::uuid
        OR g.anuncio_id <> (s.payload_normalizado_json ->> 'anuncioV3Id')::uuid
        OR g.origem <> 'CREDITO'
        OR g.validade_inicio_em <> (s.payload_normalizado_json ->> 'inicio')::timestamptz
        OR g.validade_fim_em <> (s.payload_normalizado_json ->> 'fim')::timestamptz
      )
  ) THEN
    RAISE EXCEPTION 'grupo Premium existente diverge do snapshot';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_premium s
    JOIN ativacao_beneficio a
      ON a.idempotency_key = 'import:ativacao-premium:' || s.id_origem
    WHERE s.execucao_id = contexto.execucao_id
      AND s.status = 'PROCESSADO'
      AND (
        a.id <> (s.payload_normalizado_json ->> 'ativacaoId')::uuid
        OR a.usuario_id <> (s.payload_normalizado_json ->> 'usuarioV3Id')::uuid
        OR a.anuncio_id <> (s.payload_normalizado_json ->> 'anuncioV3Id')::uuid
        OR a.beneficio_id <> (s.payload_normalizado_json ->> 'beneficioId')::uuid
        OR a.origem <> 'CREDITO'
        OR a.inicio_em <> (s.payload_normalizado_json ->> 'inicio')::timestamptz
        OR a.fim_em <> (s.payload_normalizado_json ->> 'fim')::timestamptz
        OR a.custo_creditos_snapshot <> (s.payload_normalizado_json ->> 'creditosCobrados')::integer
      )
  ) THEN
    RAISE EXCEPTION 'ativacao Premium existente diverge do snapshot';
  END IF;
END $$;

DROP TABLE IF EXISTS dryrun_premium_resultado;
CREATE TEMP TABLE dryrun_premium_resultado (
  grupos_criados bigint NOT NULL,
  ativacoes_criadas bigint NOT NULL
);

WITH contexto AS (
  SELECT c.*, e.iniciado_em AS snapshot_at
  FROM dryrun_premium_context c
  JOIN importacao_execucao e ON e.id = c.execucao_id
), candidatos AS (
  SELECT
    s.id_origem,
    (s.payload_normalizado_json ->> 'grupoId')::uuid AS grupo_id,
    (s.payload_normalizado_json ->> 'ativacaoId')::uuid AS ativacao_id,
    (s.payload_normalizado_json ->> 'usuarioV3Id')::uuid AS usuario_id,
    (s.payload_normalizado_json ->> 'anuncioV3Id')::uuid AS anuncio_id,
    (s.payload_normalizado_json ->> 'beneficioId')::uuid AS beneficio_id,
    (s.payload_normalizado_json ->> 'inicio')::timestamptz AS inicio_em,
    (s.payload_normalizado_json ->> 'fim')::timestamptz AS fim_em,
    s.payload_normalizado_json ->> 'grupoStatus' AS grupo_status,
    s.payload_normalizado_json ->> 'ativacaoStatus' AS ativacao_status,
    (s.payload_normalizado_json ->> 'creditosCobrados')::integer AS creditos_cobrados,
    c.snapshot_at
  FROM stg_premium s
  CROSS JOIN contexto c
  WHERE s.execucao_id = c.execucao_id
    AND s.status = 'PROCESSADO'
), grupos_inseridos AS (
  INSERT INTO grupo_ativacao_beneficio (
    id, tipo, origem, usuario_id, anuncio_id, ator_usuario_id,
    campanha_codigo, validade_inicio_em, validade_fim_em, status,
    idempotency_key, observacao, criado_em, atualizado_em
  )
  SELECT
    c.grupo_id,
    'IMPORTACAO',
    'CREDITO',
    c.usuario_id,
    c.anuncio_id,
    NULL,
    NULL,
    c.inicio_em,
    c.fim_em,
    c.grupo_status,
    'import:grupo-premium:' || c.id_origem,
    'Beneficio historico reconciliado sem novo debito.',
    c.inicio_em,
    c.snapshot_at
  FROM candidatos c
  ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
  RETURNING 1
), ativacoes_inseridas AS (
  INSERT INTO ativacao_beneficio (
    id, beneficio_id, opcao_id, usuario_id, anuncio_id,
    grupo_ativacao_id, origem, ator_usuario_id, campanha_codigo,
    inicio_em, fim_em, status, custo_creditos_snapshot, preco_snapshot,
    idempotency_key, revogada_em, motivo_revogacao, criado_em
  )
  SELECT
    c.ativacao_id,
    c.beneficio_id,
    NULL,
    c.usuario_id,
    c.anuncio_id,
    c.grupo_id,
    'CREDITO',
    NULL,
    NULL,
    c.inicio_em,
    c.fim_em,
    c.ativacao_status,
    c.creditos_cobrados,
    NULL,
    'import:ativacao-premium:' || c.id_origem,
    NULL,
    NULL,
    c.inicio_em
  FROM candidatos c
  ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
  RETURNING 1
)
INSERT INTO dryrun_premium_resultado (grupos_criados, ativacoes_criadas)
SELECT
  (SELECT count(*) FROM grupos_inseridos),
  (SELECT count(*) FROM ativacoes_inseridas);

DO $$
DECLARE
  contexto record;
  esperadas bigint;
  grupos bigint;
  ativacoes bigint;
  movimentos_depois bigint;
  hash_ledger_depois text;
BEGIN
  SELECT * INTO contexto FROM dryrun_premium_context;

  SELECT count(*) INTO esperadas
  FROM stg_premium
  WHERE execucao_id = contexto.execucao_id
    AND status = 'PROCESSADO';

  SELECT count(*) INTO grupos
  FROM grupo_ativacao_beneficio g
  JOIN stg_premium s
    ON g.idempotency_key = 'import:grupo-premium:' || s.id_origem
  WHERE s.execucao_id = contexto.execucao_id
    AND s.status = 'PROCESSADO';

  SELECT count(*) INTO ativacoes
  FROM ativacao_beneficio a
  JOIN stg_premium s
    ON a.idempotency_key = 'import:ativacao-premium:' || s.id_origem
  WHERE s.execucao_id = contexto.execucao_id
    AND s.status = 'PROCESSADO';

  IF grupos <> esperadas OR ativacoes <> esperadas THEN
    RAISE EXCEPTION 'reconciliacao Premium divergente: grupos %, ativacoes %, esperado %',
      grupos, ativacoes, esperadas;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM ativacao_beneficio a
    JOIN beneficio_premium b ON b.id = a.beneficio_id
    JOIN grupo_ativacao_beneficio g ON g.id = a.grupo_ativacao_id
    WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
      AND (
        a.origem <> 'CREDITO'
        OR g.origem <> 'CREDITO'
        OR a.custo_creditos_snapshot <= 0
        OR a.fim_em <= a.inicio_em
        OR NOT b.ativo
        OR a.status NOT IN ('AGENDADA', 'ATIVA', 'EXPIRADA')
        OR g.status NOT IN ('PLANEJADO', 'ATIVO', 'EXPIRADO')
      )
  ) THEN
    RAISE EXCEPTION 'ativacao Premium promovida fora do contrato canonico';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM ativacao_beneficio a
    JOIN ativacao_beneficio b
      ON a.id < b.id
     AND a.anuncio_id = b.anuncio_id
     AND a.beneficio_id = b.beneficio_id
     AND a.inicio_em < b.fim_em
     AND b.inicio_em < a.fim_em
    WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
      AND b.idempotency_key LIKE 'import:ativacao-premium:%'
  ) THEN
    RAISE EXCEPTION 'ativacoes Premium importadas sobrepostas';
  END IF;

  SELECT count(*), md5(coalesce(string_agg(
    concat_ws(':', id, usuario_id, tipo, direcao, quantidade,
              saldo_antes, saldo_depois, criado_em),
    '|' ORDER BY id), ''))
  INTO movimentos_depois, hash_ledger_depois
  FROM movimento_credito;

  IF movimentos_depois <> contexto.movimentos_ledger_antes
     OR hash_ledger_depois <> contexto.hash_ledger_antes THEN
    RAISE EXCEPTION 'reconciliacao Premium alterou o ledger';
  END IF;

  UPDATE importacao_execucao e
  SET resumo_json = e.resumo_json || jsonb_build_object(
        'premiumPromovido', ativacoes,
        'premiumVigente', (
          SELECT count(*)
          FROM ativacao_beneficio a
          JOIN stg_premium s
            ON a.idempotency_key = 'import:ativacao-premium:' || s.id_origem
          WHERE s.execucao_id = contexto.execucao_id
            AND s.status = 'PROCESSADO'
            AND a.inicio_em <= e.iniciado_em
            AND a.fim_em > e.iniciado_em
        ),
        'premiumExpirado', (
          SELECT count(*)
          FROM ativacao_beneficio a
          JOIN stg_premium s
            ON a.idempotency_key = 'import:ativacao-premium:' || s.id_origem
          WHERE s.execucao_id = contexto.execucao_id
            AND s.status = 'PROCESSADO'
            AND a.fim_em <= e.iniciado_em
        ),
        'premiumReconciliadorVersao', 'premium-historico-v1'
      )
  WHERE e.id = contexto.execucao_id;
END $$;

SELECT
  'PREMIUM_HISTORICO|novosGrupos=' || r.grupos_criados
  || '|novasAtivacoes=' || r.ativacoes_criadas
  || '|ativacoes=' || count(*)
  || '|vigentes=' || count(*) FILTER (
    WHERE a.status = 'ATIVA' AND a.inicio_em <= e.iniciado_em AND a.fim_em > e.iniciado_em
  )
  || '|expiradas=' || count(*) FILTER (WHERE a.fim_em <= e.iniciado_em)
FROM ativacao_beneficio a
CROSS JOIN dryrun_premium_context c
CROSS JOIN importacao_execucao e
CROSS JOIN dryrun_premium_resultado r
WHERE e.id = c.execucao_id
  AND a.idempotency_key LIKE 'import:ativacao-premium:%'
GROUP BY r.grupos_criados, r.ativacoes_criadas;
