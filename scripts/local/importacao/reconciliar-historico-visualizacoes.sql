\set ON_ERROR_STOP on

-- Reconciliacao do contador legado. DRY_RUN nao realiza escrita persistente.
-- APLICAR exige snapshot final, mapa completo e autorizacao registrada na execucao.

\if :{?snapshot_id}
\else
  \echo 'parametro obrigatorio ausente: snapshot_id'
  \quit 3
\endif
\if :{?snapshot_fingerprint}
\else
  \echo 'parametro obrigatorio ausente: snapshot_fingerprint'
  \quit 3
\endif
\if :{?modo}
\else
  \echo 'parametro obrigatorio ausente: modo'
  \quit 3
\endif

BEGIN;

DROP TABLE IF EXISTS pg_temp.historico_visualizacao_contexto;
CREATE TEMP TABLE historico_visualizacao_contexto ON COMMIT DROP AS
SELECT
  md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id,
  :'snapshot_id'::text AS snapshot_id,
  :'snapshot_fingerprint'::text AS snapshot_fingerprint,
  upper(:'modo'::text) AS modo;

DO $$
DECLARE
  contexto record;
  fingerprint_registrado text;
BEGIN
  SELECT * INTO contexto FROM historico_visualizacao_contexto;

  IF contexto.modo NOT IN ('DRY_RUN', 'APLICAR') THEN
    RAISE EXCEPTION 'modo invalido; use DRY_RUN ou APLICAR';
  END IF;

  SELECT resumo_json ->> 'snapshotFingerprint'
  INTO fingerprint_registrado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF fingerprint_registrado IS NULL THEN
    RAISE EXCEPTION 'snapshot nao registrado para reconciliacao de visualizacoes';
  END IF;

  IF fingerprint_registrado <> contexto.snapshot_fingerprint THEN
    RAISE EXCEPTION 'snapshotId ja registrado com fingerprint divergente';
  END IF;
END $$;

DROP TABLE IF EXISTS pg_temp.historico_visualizacao_origem;
CREATE TEMP TABLE historico_visualizacao_origem ON COMMIT DROP AS
SELECT
  a.id::text AS anuncio_origem_id,
  a.visualizacoes::bigint AS total_visualizacoes,
  md5(
    'TOPSDOJOB_PRODUCAO|anuncios|' || a.id::text
    || '|visualizacoes|' || a.visualizacoes::text
  ) AS origem_hash,
  a.criado_em AT TIME ZONE 'America/Sao_Paulo' AS criado_em,
  m.entidade_v3_id AS anuncio_id,
  CASE
    WHEN av3.id IS NOT NULL THEN 'MAPEADO'
    WHEN a.criado_em AT TIME ZONE 'America/Sao_Paulo' > e.iniciado_em
      THEN 'FORA_DO_SNAPSHOT'
    ELSE 'NAO_MAPEADO_BLOQUEANTE'
  END AS classificacao
FROM legacy.anuncios a
CROSS JOIN historico_visualizacao_contexto c
JOIN importacao_execucao e ON e.id = c.execucao_id
LEFT JOIN importacao_mapeamento m
  ON m.execucao_id = c.execucao_id
 AND m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
 AND m.tabela_origem = 'anuncios'
 AND m.id_origem = a.id::text
 AND m.entidade_tipo = 'ANUNCIO'
 AND m.status = 'MAPEADO'
LEFT JOIN anuncio av3 ON av3.id = m.entidade_v3_id;

DO $$
DECLARE
  contexto record;
  fora_do_snapshot bigint;
  nao_mapeados_bloqueantes bigint;
  apply_autorizado boolean;
BEGIN
  SELECT * INTO contexto FROM historico_visualizacao_contexto;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem
    WHERE total_visualizacoes IS NULL OR total_visualizacoes < 0
  ) THEN
    RAISE EXCEPTION 'origem possui total de visualizacoes nulo ou negativo';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem o
    JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
    CROSS JOIN importacao_execucao e
    WHERE e.id = contexto.execucao_id
      AND o.classificacao = 'MAPEADO'
      AND (
        i.total_visualizacoes <> o.total_visualizacoes
        OR i.snapshot_fingerprint <> contexto.snapshot_fingerprint
        OR i.origem_hash <> o.origem_hash
        OR i.execucao_id <> contexto.execucao_id
        OR i.snapshot_corte_em <> e.iniciado_em
      )
  ) THEN
    RAISE EXCEPTION 'historico ja importado diverge em total, fingerprint, hash ou corte temporal';
  END IF;

  SELECT
    count(*) FILTER (WHERE classificacao = 'FORA_DO_SNAPSHOT'),
    count(*) FILTER (WHERE classificacao = 'NAO_MAPEADO_BLOQUEANTE')
  INTO fora_do_snapshot, nao_mapeados_bloqueantes
  FROM historico_visualizacao_origem
  ;

  SELECT coalesce(resumo_json ->> 'historicoVisualizacoesApplyAutorizado', 'false') = 'true'
  INTO apply_autorizado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF contexto.modo = 'APLICAR'
     AND (fora_do_snapshot > 0 OR nao_mapeados_bloqueantes > 0 OR NOT apply_autorizado) THEN
    RAISE EXCEPTION
      'APPLY proibido: fora do snapshot %, nao mapeados bloqueantes %, autorizacao %',
      fora_do_snapshot,
      nao_mapeados_bloqueantes,
      apply_autorizado;
  END IF;
END $$;

DROP TABLE IF EXISTS pg_temp.historico_visualizacao_resultado;
CREATE TEMP TABLE historico_visualizacao_resultado (
  inseridos bigint NOT NULL
) ON COMMIT DROP;

WITH candidatos AS (
  SELECT
    o.anuncio_id,
    o.total_visualizacoes,
    o.origem_hash,
    c.execucao_id,
    c.snapshot_fingerprint,
    e.iniciado_em AS snapshot_corte_em,
    e.criado_em
  FROM historico_visualizacao_origem o
  CROSS JOIN historico_visualizacao_contexto c
  JOIN importacao_execucao e ON e.id = c.execucao_id
  WHERE o.classificacao = 'MAPEADO'
    AND c.modo = 'APLICAR'
), inseridos AS (
  INSERT INTO agregado_visualizacao_inicial (
    id,
    anuncio_id,
    execucao_id,
    total_visualizacoes,
    snapshot_fingerprint,
    origem_hash,
    snapshot_corte_em,
    criado_em,
    atualizado_em
  )
  SELECT
    md5('metricas:visualizacao-inicial:' || c.anuncio_id)::uuid,
    c.anuncio_id,
    c.execucao_id,
    c.total_visualizacoes,
    c.snapshot_fingerprint,
    c.origem_hash,
    c.snapshot_corte_em,
    c.criado_em,
    c.criado_em
  FROM candidatos c
  ON CONFLICT (anuncio_id) DO NOTHING
  RETURNING 1
)
INSERT INTO historico_visualizacao_resultado (inseridos)
SELECT count(*) FROM inseridos;

DO $$
DECLARE
  contexto record;
  anuncios_esperados bigint;
  anuncios_reconciliados bigint;
  total_esperado bigint;
  total_reconciliado bigint;
BEGIN
  SELECT * INTO contexto FROM historico_visualizacao_contexto;

  IF contexto.modo = 'APLICAR' AND EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem o
    LEFT JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
    CROSS JOIN importacao_execucao e
    WHERE e.id = contexto.execucao_id
      AND o.classificacao = 'MAPEADO'
      AND (
        i.id IS NULL
        OR i.total_visualizacoes <> o.total_visualizacoes
        OR i.snapshot_fingerprint <> contexto.snapshot_fingerprint
        OR i.origem_hash <> o.origem_hash
        OR i.execucao_id <> contexto.execucao_id
        OR i.snapshot_corte_em <> e.iniciado_em
      )
  ) THEN
    RAISE EXCEPTION 'reconciliacao final do historico de visualizacoes divergente';
  END IF;

  IF contexto.modo = 'APLICAR' THEN
    SELECT count(*), coalesce(sum(total_visualizacoes), 0)
    INTO anuncios_esperados, total_esperado
    FROM historico_visualizacao_origem
    WHERE classificacao = 'MAPEADO';

    SELECT count(*), coalesce(sum(i.total_visualizacoes), 0)
    INTO anuncios_reconciliados, total_reconciliado
    FROM historico_visualizacao_origem o
    JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
    WHERE o.classificacao = 'MAPEADO';

    IF anuncios_reconciliados <> anuncios_esperados
       OR total_reconciliado <> total_esperado THEN
      RAISE EXCEPTION
        'reconciliacao total divergente: anuncios %/%; visualizacoes %/%',
        anuncios_reconciliados,
        anuncios_esperados,
        total_reconciliado,
        total_esperado;
    END IF;
  END IF;
END $$;

SELECT
  classificacao AS codigo,
  anuncio_origem_id,
  criado_em,
  total_visualizacoes
FROM historico_visualizacao_origem
WHERE classificacao <> 'MAPEADO'
ORDER BY anuncio_origem_id;

SELECT
  c.modo,
  count(*) AS anuncios_origem,
  count(*) FILTER (WHERE o.classificacao <> 'FORA_DO_SNAPSHOT') AS anuncios_escopo,
  count(*) FILTER (WHERE o.classificacao = 'MAPEADO') AS anuncios_mapeados,
  count(*) FILTER (WHERE o.classificacao = 'FORA_DO_SNAPSHOT') AS anuncios_fora_do_snapshot,
  count(*) FILTER (WHERE o.classificacao = 'NAO_MAPEADO_BLOQUEANTE') AS anuncios_nao_mapeados_bloqueantes,
  coalesce(sum(o.total_visualizacoes) FILTER (
    WHERE o.classificacao <> 'FORA_DO_SNAPSHOT'
  ), 0) AS visualizacoes_escopo,
  coalesce(sum(o.total_visualizacoes) FILTER (
    WHERE o.classificacao = 'MAPEADO'
  ), 0) AS visualizacoes_reconciliaveis,
  coalesce(sum(o.total_visualizacoes) FILTER (
    WHERE o.classificacao = 'FORA_DO_SNAPSHOT'
  ), 0) AS visualizacoes_fora_do_snapshot,
  coalesce(sum(o.total_visualizacoes), 0) AS visualizacoes_origem,
  r.inseridos,
  count(i.id) FILTER (WHERE o.classificacao = 'MAPEADO') - r.inseridos AS preservados
FROM historico_visualizacao_origem o
CROSS JOIN historico_visualizacao_contexto c
CROSS JOIN historico_visualizacao_resultado r
LEFT JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
GROUP BY c.modo, r.inseridos;

COMMIT;
