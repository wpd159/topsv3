\set ON_ERROR_STOP on

-- Reconciliacao das metricas legadas em um PostgreSQL V3 descartavel.
-- A origem e um snapshot local chamado source_snapshot; producao nunca e
-- acessada por este script. DRY_RUN nao realiza escrita operacional.

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

SELECT NOT EXISTS (
  SELECT 1 FROM pg_extension WHERE extname = 'postgres_fdw'
) AS metricas_criou_postgres_fdw
\gset

BEGIN;

CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE SCHEMA legacy_metricas;
CREATE SERVER legacy_metricas_source
  FOREIGN DATA WRAPPER postgres_fdw
  OPTIONS (host '/var/run/postgresql', dbname 'source_snapshot');
DO $$
BEGIN
  EXECUTE format(
    'CREATE USER MAPPING FOR %I SERVER legacy_metricas_source OPTIONS (user %L)',
    current_user,
    current_user
  );
END $$;

IMPORT FOREIGN SCHEMA public LIMIT TO (
  anuncios,
  anuncio_view_log,
  cliques_whatsapp
) FROM SERVER legacy_metricas_source INTO legacy_metricas;

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
    RAISE EXCEPTION 'snapshot nao registrado para reconciliacao de metricas';
  END IF;

  IF fingerprint_registrado <> contexto.snapshot_fingerprint THEN
    RAISE EXCEPTION 'snapshotId ja registrado com fingerprint divergente';
  END IF;
END $$;

CREATE TEMP TABLE historico_visualizacao_origem ON COMMIT DROP AS
WITH eventos AS (
  SELECT
    v.anuncio_id,
    count(*)::bigint AS total_eventos,
    min(v.visto_em) AS menor_data,
    max(v.visto_em) AS maior_data,
    md5(string_agg(
      v.id::text || ':' || v.visto_em::text,
      '|' ORDER BY v.id
    )) AS eventos_hash
  FROM legacy_metricas.anuncio_view_log v
  GROUP BY v.anuncio_id
)
SELECT
  a.id::text AS anuncio_origem_id,
  a.visualizacoes::bigint AS contador_total,
  coalesce(e.total_eventos, 0)::bigint AS eventos_detalhados,
  (a.visualizacoes::bigint - coalesce(e.total_eventos, 0)::bigint)
    AS saldo_historico_legado,
  e.menor_data AT TIME ZONE 'America/Sao_Paulo' AS menor_evento_em,
  e.maior_data AT TIME ZONE 'America/Sao_Paulo' AS maior_evento_em,
  md5(
    'TOPSDOJOB_PRODUCAO|anuncios|' || a.id::text
    || '|visualizacoes|' || a.visualizacoes::text
    || '|eventos|' || coalesce(e.total_eventos, 0)::text
    || '|eventosHash|' || coalesce(e.eventos_hash, md5(''))
  ) AS origem_hash,
  a.criado_em AT TIME ZONE 'America/Sao_Paulo' AS criado_em,
  m.entidade_v3_id AS anuncio_id,
  CASE
    WHEN av3.id IS NOT NULL THEN 'MAPEADO'
    WHEN a.criado_em AT TIME ZONE 'America/Sao_Paulo' > ie.iniciado_em
      THEN 'FORA_DO_SNAPSHOT'
    ELSE 'NAO_MAPEADO_BLOQUEANTE'
  END AS classificacao
FROM legacy_metricas.anuncios a
CROSS JOIN historico_visualizacao_contexto c
JOIN importacao_execucao ie ON ie.id = c.execucao_id
LEFT JOIN eventos e ON e.anuncio_id = a.id
LEFT JOIN importacao_mapeamento m
  ON m.execucao_id = c.execucao_id
 AND m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
 AND m.tabela_origem = 'anuncios'
 AND m.id_origem = a.id::text
 AND m.entidade_tipo = 'ANUNCIO'
 AND m.status = 'MAPEADO'
LEFT JOIN anuncio av3 ON av3.id = m.entidade_v3_id;

CREATE TEMP TABLE historico_visualizacao_evento_origem ON COMMIT DROP AS
SELECT
  v.id::text AS evento_origem_id,
  v.anuncio_id::text AS anuncio_origem_id,
  m.entidade_v3_id AS anuncio_id,
  md5('legacy:anuncio-view-log:' || v.id::text)::uuid AS evento_id,
  'import:anuncio_view_log:' || v.id::text AS request_id,
  v.visto_em AT TIME ZONE 'America/Sao_Paulo' AS criado_em,
  CASE
    WHEN m.entidade_v3_id IS NOT NULL THEN 'MAPEADO'
    ELSE 'NAO_MAPEADO_BLOQUEANTE'
  END AS classificacao
FROM legacy_metricas.anuncio_view_log v
CROSS JOIN historico_visualizacao_contexto c
LEFT JOIN importacao_mapeamento m
  ON m.execucao_id = c.execucao_id
 AND m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
 AND m.tabela_origem = 'anuncios'
 AND m.id_origem = v.anuncio_id::text
 AND m.entidade_tipo = 'ANUNCIO'
 AND m.status = 'MAPEADO';

CREATE TEMP TABLE historico_clique_origem ON COMMIT DROP AS
SELECT
  w.id::text AS clique_origem_id,
  w.anuncio_id::text AS anuncio_origem_id,
  m.entidade_v3_id AS anuncio_id,
  md5('legacy:cliques-whatsapp:' || w.id::text)::uuid AS clique_id,
  'import:cliques_whatsapp:' || w.id::text AS request_id,
  w.data_clique AT TIME ZONE 'America/Sao_Paulo' AS criado_em,
  w.data_clique::date AS dia_local,
  CASE
    WHEN m.entidade_v3_id IS NOT NULL THEN 'MAPEADO'
    ELSE 'NAO_MAPEADO_BLOQUEANTE'
  END AS classificacao
FROM legacy_metricas.cliques_whatsapp w
CROSS JOIN historico_visualizacao_contexto c
LEFT JOIN importacao_mapeamento m
  ON m.execucao_id = c.execucao_id
 AND m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
 AND m.tabela_origem = 'anuncios'
 AND m.id_origem = w.anuncio_id::text
 AND m.entidade_tipo = 'ANUNCIO'
 AND m.status = 'MAPEADO';

DO $$
DECLARE
  contexto record;
  fora_do_snapshot bigint;
  nao_mapeados_bloqueantes bigint;
  eventos_nao_mapeados bigint;
  cliques_nao_mapeados bigint;
  saldos_negativos bigint;
  apply_autorizado boolean;
BEGIN
  SELECT * INTO contexto FROM historico_visualizacao_contexto;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem
    WHERE contador_total IS NULL OR contador_total < 0
  ) THEN
    RAISE EXCEPTION 'origem possui contador de visualizacoes nulo ou negativo';
  END IF;

  SELECT
    count(*) FILTER (WHERE classificacao = 'FORA_DO_SNAPSHOT'),
    count(*) FILTER (WHERE classificacao = 'NAO_MAPEADO_BLOQUEANTE'),
    count(*) FILTER (WHERE saldo_historico_legado < 0)
  INTO fora_do_snapshot, nao_mapeados_bloqueantes, saldos_negativos
  FROM historico_visualizacao_origem;

  SELECT count(*) INTO eventos_nao_mapeados
  FROM historico_visualizacao_evento_origem
  WHERE classificacao <> 'MAPEADO';

  SELECT count(*) INTO cliques_nao_mapeados
  FROM historico_clique_origem
  WHERE classificacao <> 'MAPEADO';

  SELECT coalesce(resumo_json ->> 'historicoVisualizacoesApplyAutorizado', 'false') = 'true'
  INTO apply_autorizado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF contexto.modo = 'APLICAR'
     AND (
       fora_do_snapshot > 0
       OR nao_mapeados_bloqueantes > 0
       OR eventos_nao_mapeados > 0
       OR cliques_nao_mapeados > 0
       OR saldos_negativos > 0
       OR NOT apply_autorizado
     ) THEN
    RAISE EXCEPTION
      'APPLY proibido: fora do snapshot %, anuncios nao mapeados %, eventos nao mapeados %, cliques nao mapeados %, saldos negativos %, autorizacao %',
      fora_do_snapshot,
      nao_mapeados_bloqueantes,
      eventos_nao_mapeados,
      cliques_nao_mapeados,
      saldos_negativos,
      apply_autorizado;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem o
    JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
    JOIN importacao_execucao e ON e.id = contexto.execucao_id
    WHERE o.classificacao = 'MAPEADO'
      AND (
        i.total_visualizacoes <> o.saldo_historico_legado
        OR i.snapshot_fingerprint <> contexto.snapshot_fingerprint
        OR i.origem_hash <> o.origem_hash
        OR i.execucao_id <> contexto.execucao_id
        OR i.snapshot_corte_em <> e.iniciado_em
      )
  ) THEN
    RAISE EXCEPTION 'historico ja importado diverge em saldo, fingerprint, hash ou corte temporal';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_evento_origem o
    JOIN evento_visualizacao e ON e.id = o.evento_id
    WHERE e.anuncio_id <> o.anuncio_id
       OR e.criado_em <> o.criado_em
       OR e.request_id IS DISTINCT FROM o.request_id
       OR e.visitante_hash IS NOT NULL
       OR e.ip_hash IS NOT NULL
       OR e.user_agent_hash IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'evento de visualizacao ja importado diverge da origem sanitizada';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_clique_origem o
    JOIN clique_whatsapp c ON c.id = o.clique_id
    WHERE c.anuncio_id <> o.anuncio_id
       OR c.criado_em <> o.criado_em
       OR c.request_id IS DISTINCT FROM o.request_id
       OR NOT c.permitido
       OR c.visitante_hash IS NOT NULL
       OR c.ip_hash IS NOT NULL
       OR c.user_agent_hash IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'clique WhatsApp ja importado diverge da origem sanitizada';
  END IF;
END $$;

CREATE TEMP TABLE historico_metricas_resultado (
  agregados_inseridos bigint NOT NULL,
  eventos_inseridos bigint NOT NULL,
  cliques_inseridos bigint NOT NULL
) ON COMMIT DROP;

WITH candidatos AS (
  SELECT
    o.anuncio_id,
    o.saldo_historico_legado,
    o.origem_hash,
    c.execucao_id,
    c.snapshot_fingerprint,
    e.iniciado_em AS snapshot_corte_em,
    e.criado_em
  FROM historico_visualizacao_origem o
  CROSS JOIN historico_visualizacao_contexto c
  JOIN importacao_execucao e ON e.id = c.execucao_id
  WHERE o.classificacao = 'MAPEADO'
    AND o.saldo_historico_legado >= 0
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
    c.saldo_historico_legado,
    c.snapshot_fingerprint,
    c.origem_hash,
    c.snapshot_corte_em,
    c.criado_em,
    c.criado_em
  FROM candidatos c
  ON CONFLICT (anuncio_id) DO NOTHING
  RETURNING 1
), total AS (
  SELECT count(*)::bigint AS quantidade FROM inseridos
)
INSERT INTO historico_metricas_resultado (
  agregados_inseridos,
  eventos_inseridos,
  cliques_inseridos
)
SELECT quantidade, 0, 0 FROM total;

WITH inseridos AS (
  INSERT INTO evento_visualizacao (
    id,
    anuncio_id,
    visitante_hash,
    ip_hash,
    user_agent_hash,
    referer_hash,
    origem_pais,
    origem_uf,
    origem_cidade,
    dispositivo,
    request_id,
    criado_em
  )
  SELECT
    o.evento_id,
    o.anuncio_id,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    o.request_id,
    o.criado_em
  FROM historico_visualizacao_evento_origem o
  CROSS JOIN historico_visualizacao_contexto c
  WHERE o.classificacao = 'MAPEADO'
    AND c.modo = 'APLICAR'
  ON CONFLICT (id) DO NOTHING
  RETURNING 1
)
UPDATE historico_metricas_resultado
SET eventos_inseridos = (SELECT count(*) FROM inseridos);

WITH inseridos AS (
  INSERT INTO clique_whatsapp (
    id,
    anuncio_id,
    visitante_hash,
    ip_hash,
    user_agent_hash,
    origem_pais,
    origem_uf,
    origem_cidade,
    dispositivo,
    permitido,
    motivo_bloqueio,
    request_id,
    criado_em
  )
  SELECT
    o.clique_id,
    o.anuncio_id,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    true,
    NULL,
    o.request_id,
    o.criado_em
  FROM historico_clique_origem o
  CROSS JOIN historico_visualizacao_contexto c
  WHERE o.classificacao = 'MAPEADO'
    AND c.modo = 'APLICAR'
  ON CONFLICT (id) DO NOTHING
  RETURNING 1
)
UPDATE historico_metricas_resultado
SET cliques_inseridos = (SELECT count(*) FROM inseridos);

DO $$
DECLARE
  contexto record;
BEGIN
  SELECT * INTO contexto FROM historico_visualizacao_contexto;

  IF contexto.modo <> 'APLICAR' THEN
    RETURN;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem o
    LEFT JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
    WHERE o.classificacao = 'MAPEADO'
      AND (
        i.id IS NULL
        OR i.total_visualizacoes <> o.saldo_historico_legado
        OR i.snapshot_fingerprint <> contexto.snapshot_fingerprint
        OR i.origem_hash <> o.origem_hash
      )
  ) THEN
    RAISE EXCEPTION 'reconciliacao final do saldo historico divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_evento_origem o
    LEFT JOIN evento_visualizacao e ON e.id = o.evento_id
    WHERE o.classificacao = 'MAPEADO'
      AND (
        e.id IS NULL
        OR e.anuncio_id <> o.anuncio_id
        OR e.criado_em <> o.criado_em
        OR e.request_id IS DISTINCT FROM o.request_id
      )
  ) THEN
    RAISE EXCEPTION 'reconciliacao final dos eventos de visualizacao divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_clique_origem o
    LEFT JOIN clique_whatsapp c ON c.id = o.clique_id
    WHERE o.classificacao = 'MAPEADO'
      AND (
        c.id IS NULL
        OR c.anuncio_id <> o.anuncio_id
        OR c.criado_em <> o.criado_em
        OR c.request_id IS DISTINCT FROM o.request_id
      )
  ) THEN
    RAISE EXCEPTION 'reconciliacao final dos cliques WhatsApp divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM historico_visualizacao_origem o
    JOIN agregado_visualizacao_inicial i ON i.anuncio_id = o.anuncio_id
    WHERE o.classificacao = 'MAPEADO'
      AND i.total_visualizacoes + (
        SELECT count(*)
        FROM evento_visualizacao e
        WHERE e.anuncio_id = o.anuncio_id
          AND e.request_id LIKE 'import:anuncio_view_log:%'
      ) <> o.contador_total
  ) THEN
    RAISE EXCEPTION 'total canonico nao fecha com o contador da origem';
  END IF;

  IF (
    SELECT count(*)
    FROM clique_whatsapp
    WHERE request_id LIKE 'import:cliques_whatsapp:%'
  ) <> (
    SELECT count(*)
    FROM historico_clique_origem
    WHERE classificacao = 'MAPEADO'
  ) THEN
    RAISE EXCEPTION 'total de cliques WhatsApp importado diverge da origem';
  END IF;
END $$;

SELECT
  'SALDO_NEGATIVO' AS codigo,
  anuncio_origem_id,
  contador_total,
  eventos_detalhados,
  saldo_historico_legado
FROM historico_visualizacao_origem
WHERE saldo_historico_legado < 0
ORDER BY anuncio_origem_id;

SELECT
  classificacao AS codigo,
  anuncio_origem_id,
  criado_em,
  contador_total
FROM historico_visualizacao_origem
WHERE classificacao <> 'MAPEADO'
ORDER BY anuncio_origem_id;

SELECT
  c.modo,
  count(*) AS anuncios_origem,
  count(*) FILTER (WHERE o.classificacao <> 'FORA_DO_SNAPSHOT') AS anuncios_escopo,
  count(*) FILTER (WHERE o.classificacao = 'MAPEADO') AS anuncios_mapeados,
  count(*) FILTER (WHERE o.classificacao = 'FORA_DO_SNAPSHOT') AS anuncios_fora_do_snapshot,
  count(*) FILTER (WHERE o.classificacao = 'NAO_MAPEADO_BLOQUEANTE')
    AS anuncios_nao_mapeados_bloqueantes,
  count(*) FILTER (
    WHERE o.classificacao = 'MAPEADO' AND o.saldo_historico_legado = 0
  ) AS contador_explicado_integralmente,
  count(*) FILTER (
    WHERE o.classificacao = 'MAPEADO' AND o.saldo_historico_legado > 0
  ) AS saldo_historico_positivo,
  count(*) FILTER (
    WHERE o.classificacao = 'MAPEADO' AND o.saldo_historico_legado < 0
  ) AS saldo_historico_negativo,
  coalesce(sum(o.saldo_historico_legado) FILTER (
    WHERE o.classificacao = 'MAPEADO' AND o.saldo_historico_legado > 0
  ), 0) AS soma_saldos_positivos,
  coalesce(sum(o.contador_total) FILTER (
    WHERE o.classificacao = 'MAPEADO'
  ), 0) AS visualizacoes_canonicas,
  coalesce(sum(o.eventos_detalhados) FILTER (
    WHERE o.classificacao = 'MAPEADO'
  ), 0) AS eventos_detalhados,
  min(o.menor_evento_em) AS menor_evento_em,
  max(o.maior_evento_em) AS maior_evento_em,
  (SELECT count(*) FROM historico_clique_origem WHERE classificacao = 'MAPEADO')
    AS cliques_whatsapp,
  (SELECT min(criado_em) FROM historico_clique_origem WHERE classificacao = 'MAPEADO')
    AS menor_clique_em,
  (SELECT max(criado_em) FROM historico_clique_origem WHERE classificacao = 'MAPEADO')
    AS maior_clique_em,
  r.agregados_inseridos,
  r.eventos_inseridos,
  r.cliques_inseridos
FROM historico_visualizacao_origem o
CROSS JOIN historico_visualizacao_contexto c
CROSS JOIN historico_metricas_resultado r
GROUP BY c.modo, r.agregados_inseridos, r.eventos_inseridos, r.cliques_inseridos;

COMMIT;

DROP SCHEMA legacy_metricas CASCADE;
DROP SERVER legacy_metricas_source CASCADE;
\if :metricas_criou_postgres_fdw
DROP EXTENSION postgres_fdw;
\endif
