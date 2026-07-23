\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

\if :{?r2_public_media_bucket}
\else
DO $$ BEGIN RAISE EXCEPTION 'parametro R2 obrigatorio ausente: r2_public_media_bucket'; END $$;
\endif
\if :{?r2_public_media_prefix}
\else
DO $$ BEGIN RAISE EXCEPTION 'parametro R2 obrigatorio ausente: r2_public_media_prefix'; END $$;
\endif
\if :{?r2_private_media_bucket}
\else
DO $$ BEGIN RAISE EXCEPTION 'parametro R2 obrigatorio ausente: r2_private_media_bucket'; END $$;
\endif
\if :{?r2_private_media_prefix}
\else
DO $$ BEGIN RAISE EXCEPTION 'parametro R2 obrigatorio ausente: r2_private_media_prefix'; END $$;
\endif
\if :{?r2_document_bucket}
\else
DO $$ BEGIN RAISE EXCEPTION 'parametro R2 obrigatorio ausente: r2_document_bucket'; END $$;
\endif
\if :{?r2_document_prefix}
\else
DO $$ BEGIN RAISE EXCEPTION 'parametro R2 obrigatorio ausente: r2_document_prefix'; END $$;
\endif

CREATE TEMP TABLE validar_context AS
SELECT
  :'r2_public_media_bucket'::text AS r2_public_media_bucket,
  :'r2_public_media_prefix'::text AS r2_public_media_prefix,
  :'r2_private_media_bucket'::text AS r2_private_media_bucket,
  :'r2_private_media_prefix'::text AS r2_private_media_prefix,
  :'r2_document_bucket'::text AS r2_document_bucket,
  :'r2_document_prefix'::text AS r2_document_prefix;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM validar_context
    WHERE length(trim(r2_public_media_bucket)) = 0
       OR length(trim(r2_private_media_bucket)) = 0
       OR length(trim(r2_document_bucket)) = 0
       OR r2_public_media_prefix !~ '^hml/[A-Za-z0-9._/-]+/$'
       OR r2_private_media_prefix !~ '^hml/[A-Za-z0-9._/-]+/$'
       OR r2_document_prefix !~ '^hml/[A-Za-z0-9._/-]+/$'
       OR r2_public_media_prefix LIKE '%..%'
       OR r2_private_media_prefix LIKE '%..%'
       OR r2_document_prefix LIKE '%..%'
  ) THEN
    RAISE EXCEPTION 'configuracao R2 de destino invalida';
  END IF;
END $$;

SELECT 'USUARIOS|' || count(*) FROM usuario;
SELECT 'ANUNCIOS|' || count(*) FROM anuncio;
SELECT 'ANUNCIOS_PUBLICADOS|' || count(*) FROM anuncio WHERE status = 'PUBLICADO';
SELECT 'ANUNCIOS_FORA_CATALOGO|' || count(*) FROM anuncio WHERE status <> 'PUBLICADO';
SELECT 'ANUNCIOS_AGUARDANDO_MODERACAO|' || count(*)
FROM anuncio
WHERE status = 'PENDENTE_REVISAO' AND status_moderacao = 'PENDENTE';
SELECT 'ANUNCIOS_IMPORTADOS_PUBLICADOS_INDEVIDAMENTE|' || count(*)
FROM anuncio
WHERE origem_importacao_id IS NOT NULL
  AND status = 'PUBLICADO';
SELECT 'ANUNCIOS_IMPORTADOS_MODERADOS_INDEVIDAMENTE|' || count(*)
FROM anuncio
WHERE origem_importacao_id IS NOT NULL
  AND status <> 'REMOVIDO'
  AND (status <> 'PENDENTE_REVISAO' OR status_moderacao <> 'PENDENTE');
SELECT 'SLUG_DUPLICADO_EXCESSO|' || coalesce(sum(c - 1), 0)
FROM (SELECT count(*) c FROM anuncio GROUP BY slug HAVING count(*) > 1) q;
SELECT 'FK_NAO_VALIDADA|' || count(*)
FROM pg_constraint
WHERE contype = 'f' AND NOT convalidated;
SELECT 'SERVICOS_UNICOS|' || count(*) FROM anuncio_servicos;
SELECT 'LOCAIS_UNICOS|' || count(*) FROM anuncio_local_atendimento;
SELECT 'VINCULO_SERVICO_DUPLICADO|' || coalesce(sum(c - 1), 0)
FROM (SELECT count(*) c FROM anuncio_servicos GROUP BY anuncio_id, servico HAVING count(*) > 1) q;
SELECT 'VINCULO_LOCAL_DUPLICADO|' || coalesce(sum(c - 1), 0)
FROM (SELECT count(*) c FROM anuncio_local_atendimento GROUP BY anuncio_id, local_atendimento HAVING count(*) > 1) q;
SELECT 'PRIMEIRA_PUBLICACAO_RECUPERADA|' || count(*)
FROM stg_anuncio
WHERE payload_normalizado_json ->> 'primeiraPublicacaoOrigem' = 'REVISAO_APROVADA';
SELECT 'PRIMEIRA_PUBLICACAO_INFERIDA|' || count(*)
FROM stg_anuncio
WHERE payload_normalizado_json ->> 'primeiraPublicacaoOrigem' = 'CRIADO_EM_INFERIDO';
SELECT 'MIDIA_LIVRE|' || count(*) FROM anuncio_midia WHERE visibilidade_midia = 'LIVRE';
SELECT 'MIDIA_RESTRITA_18|' || count(*) FROM anuncio_midia WHERE visibilidade_midia = 'RESTRITA_18';
SELECT 'MIDIA_R2_PUBLICA_IMPORTADA|' || count(*)
FROM arquivo_midia
WHERE storage_provider = 'R2'
  AND bucket = :'r2_public_media_bucket'
  AND chave_objeto LIKE :'r2_public_media_prefix' || 'importacao/sha256/%';
SELECT 'MIDIA_R2_PRIVADA_IMPORTADA|' || count(*)
FROM anuncio_midia am
JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
WHERE ar.storage_provider = 'R2'
  AND ar.bucket = :'r2_private_media_bucket'
  AND ar.chave_objeto LIKE :'r2_private_media_prefix' || 'importacao/anuncios/%';
SELECT 'MIDIA_LOGICA_ORIGEM|' || (resumo_json ->> 'midiasR2PrivadasLogicasOrigem')
FROM importacao_execucao;
SELECT 'MIDIA_LOGICA_IMPORTADA|' || (resumo_json ->> 'midiasR2PrivadasLogicasImportadas')
FROM importacao_execucao;
SELECT 'MIDIA_LOGICA_QUARENTENA|' || (resumo_json ->> 'midiasR2PrivadasLogicasQuarentena')
FROM importacao_execucao;
SELECT 'MIDIA_LOGICA_DIVERGENTE|' || (resumo_json ->> 'midiasR2PrivadasLogicasDivergentes')
FROM importacao_execucao;
SELECT 'MIDIA_ORIGEM_AUSENTE|' || count(*)
FROM importacao_pendencia
WHERE codigo = 'MIDIA_ORIGEM_AUSENTE';
SELECT 'MIDIA_PRIVADA_DESTINO_INVALIDA|' || count(*)
FROM anuncio_midia am
JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
CROSS JOIN validar_context c
WHERE ar.storage_provider = 'R2'
  AND (
    ar.bucket <> c.r2_private_media_bucket
    OR ar.chave_objeto NOT LIKE c.r2_private_media_prefix || 'importacao/anuncios/%'
    OR substring(ar.chave_objeto FROM length(c.r2_private_media_prefix) + 1)
        !~ '^importacao/anuncios/[1-9][0-9]*/midias/[0-9a-f]{64}/[a-z0-9-]+/[0-9a-f]{64}\.[a-z0-9]+$'
    OR ar.sha256 !~ '^[0-9a-f]{64}$'
    OR ar.chave_objeto NOT LIKE '%/' || ar.sha256 || '.%'
  );
SELECT 'FOTO_FORA_DE_PENDENTE_PRIVADA|' || count(*)
FROM anuncio_midia am
JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
WHERE am.tipo = 'FOTO'
  AND (
    am.status <> 'PENDENTE'
    OR am.visibilidade_midia IS NOT NULL
    OR ar.status_arquivo <> 'PENDENTE'
  );
SELECT 'VIDEO_FORA_DE_PENDENTE_RESTRITA|' || count(*)
FROM anuncio_midia am
JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
WHERE am.tipo = 'VIDEO'
  AND (
    am.status <> 'PENDENTE'
    OR am.visibilidade_midia <> 'RESTRITA_18'
    OR ar.status_arquivo <> 'PENDENTE'
  );
SELECT 'MIDIA_R2_CHECKSUM_AUSENTE|' || count(*)
FROM arquivo_midia
WHERE storage_provider = 'R2'
  AND (sha256 IS NULL OR sha256 !~ '^[0-9a-f]{64}$');
SELECT 'MIDIA_R2_CHAVE_DUPLICADA|' || coalesce(sum(c - 1), 0)
FROM (
  SELECT count(*) c
  FROM arquivo_midia
  WHERE storage_provider = 'R2'
  GROUP BY bucket, chave_objeto
  HAVING count(*) > 1
) q;
SELECT 'MIDIA_RESTRITA_EM_BUCKET_PUBLICO|' || count(*)
FROM anuncio_midia am
JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
WHERE am.visibilidade_midia = 'RESTRITA_18'
  AND ar.bucket = :'r2_public_media_bucket';
SELECT 'SEO_INDEXAVEL_POR_EVIDENCIA|' || count(*)
FROM seo_url WHERE tipo = 'ANUNCIO' AND indexavel AND incluir_sitemap;
SELECT 'SEO_INDEXAVEL_SEM_MIDIA_REAL|' || count(*)
FROM seo_url s
WHERE s.tipo = 'ANUNCIO'
  AND s.indexavel
  AND NOT EXISTS (
    SELECT 1
    FROM anuncio_midia am
    JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
    CROSS JOIN validar_context c
    WHERE am.anuncio_id = s.entidade_id
      AND am.tipo = 'FOTO'
      AND am.status = 'PUBLICAVEL'
      AND am.visibilidade_midia = 'LIVRE'
      AND ar.storage_provider = 'R2'
      AND ar.bucket = c.r2_public_media_bucket
      AND ar.chave_objeto LIKE c.r2_public_media_prefix || 'importacao/sha256/%'
  );
SELECT 'VISUALIZACAO_SALDO_INICIAL|' || coalesce(sum(total_visualizacoes), 0)
FROM agregado_visualizacao_inicial;
SELECT 'VISUALIZACAO_EVENTOS_IMPORTADOS|' || count(*)
FROM evento_visualizacao
WHERE request_id LIKE 'import:anuncio_view_log:%';
SELECT 'VISUALIZACAO_TOTAL_CANONICO_IMPORTADO|' || coalesce(sum(total), 0)
FROM (
  SELECT
    i.anuncio_id,
    i.total_visualizacoes + count(e.id) AS total
  FROM agregado_visualizacao_inicial i
  LEFT JOIN evento_visualizacao e
    ON e.anuncio_id = i.anuncio_id
   AND e.request_id LIKE 'import:anuncio_view_log:%'
  GROUP BY i.anuncio_id, i.total_visualizacoes
) q;
SELECT 'CLIQUES_WHATSAPP_IMPORTADOS|' || count(*)
FROM clique_whatsapp
WHERE request_id LIKE 'import:cliques_whatsapp:%';
SELECT 'METRICAS_EVENTOS_ORFAOS|' || (
  (SELECT count(*) FROM evento_visualizacao e
   LEFT JOIN anuncio a ON a.id = e.anuncio_id
   WHERE a.id IS NULL)
  +
  (SELECT count(*) FROM clique_whatsapp c
   LEFT JOIN anuncio a ON a.id = c.anuncio_id
   WHERE a.id IS NULL)
);
SELECT 'STORIES_QUARENTENA|' || count(*) FROM stg_story;
SELECT 'KYC_CANONICO|' || count(*) FROM documento_usuario;
SELECT 'KYC_PENDENTE|' || count(*) FROM documento_usuario WHERE status = 'PENDENTE';
SELECT 'KYC_APROVADO|' || count(*) FROM documento_usuario WHERE status = 'VALIDADO';
SELECT 'KYC_REJEITADO|' || count(*) FROM documento_usuario WHERE status = 'REJEITADO';
SELECT 'KYC_USUARIOS_APROVADO|' || count(DISTINCT entidade_v3_id)
FROM stg_usuario WHERE payload_normalizado_json ->> 'kycStatusOrigem' = 'APROVADO';
SELECT 'KYC_USUARIOS_PENDENTE|' || count(DISTINCT entidade_v3_id)
FROM stg_usuario WHERE payload_normalizado_json ->> 'kycStatusOrigem' = 'PENDENTE';
SELECT 'KYC_USUARIOS_REPROVADO|' || count(DISTINCT entidade_v3_id)
FROM stg_usuario WHERE payload_normalizado_json ->> 'kycStatusOrigem' = 'REPROVADO';
SELECT 'KYC_USUARIOS_NAO_INICIADO|' || count(DISTINCT entidade_v3_id)
FROM stg_usuario WHERE payload_normalizado_json ->> 'kycStatusOrigem' = 'NAO_INICIADO';
SELECT 'KYC_DUPLICATA_CONSOLIDADA|' || count(*)
FROM stg_midia
WHERE tabela_origem = 'usuario_documentos'
  AND payload_normalizado_json ->> 'conteudoDuplicadoConsolidado' = 'true';
SELECT 'KYC_HTTP_QUARENTENA|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_REFERENCIA_HTTP_QUARENTENA';
SELECT 'KYC_PRIVADO_NAO_VERIFICADO|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_REFERENCIA_PRIVADA_NAO_VERIFICADA';
SELECT 'KYC_PARTE_NAO_COMPROVADA|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_PARTE_DOCUMENTAL_NAO_COMPROVADA';
SELECT 'KYC_TIPO_INVALIDO|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_TIPO_OU_CONTEUDO_INVALIDO';
SELECT 'KYC_REFERENCIA_DUPLICADA|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_REFERENCIA_DUPLICADA';
SELECT 'KYC_DOCUMENTO_ORFAO|' || count(*)
FROM documento_usuario d
LEFT JOIN usuario u ON u.id = d.usuario_id
LEFT JOIN arquivo_midia a ON a.id = d.arquivo_midia_id
WHERE u.id IS NULL OR a.id IS NULL;
SELECT 'KYC_STORAGE_PRIVADO|' || count(*)
FROM documento_usuario d
JOIN arquivo_midia a ON a.id = d.arquivo_midia_id
WHERE a.storage_provider = 'R2'
  AND a.bucket = :'r2_document_bucket'
  AND a.chave_objeto LIKE :'r2_document_prefix' || 'importacao/%/sha256/%';
SELECT 'MOVIMENTOS_SALDO_INICIAL|' || count(*)
FROM movimento_credito WHERE tipo = 'MIGRACAO_SALDO_INICIAL';
SELECT 'USUARIOS_SALDO_INICIAL|' || count(DISTINCT usuario_id)
FROM movimento_credito WHERE tipo = 'MIGRACAO_SALDO_INICIAL';
SELECT 'SALDO_INICIAL_LEDGER|' || coalesce(sum(quantidade), 0)
FROM movimento_credito WHERE tipo = 'MIGRACAO_SALDO_INICIAL';
SELECT 'SALDOS_OPERACIONAIS_VALIDOS|' || count(*)
FROM stg_credito
WHERE tabela_origem = 'creditos_usuario' AND status = 'PROCESSADO';
SELECT 'SALDOS_POSITIVOS_ELEGIVEIS|' || count(*)
FROM stg_credito
WHERE tabela_origem = 'creditos_usuario'
  AND status = 'PROCESSADO'
  AND (payload_normalizado_json ->> 'saldoOperacional')::integer > 0;
SELECT 'SALDOS_ZERO_SEM_MOVIMENTO|' || count(*)
FROM stg_credito WHERE pendencia_codigo = 'SALDO_ZERO_SEM_MOVIMENTO';
SELECT 'MOVIMENTOS_SEM_DATA_CONFIAVEL|' || count(*)
FROM stg_credito WHERE pendencia_codigo = 'MOVIMENTO_SEM_DATA_CONFIAVEL';
SELECT 'HISTORICOS_SEM_DATA_TOTAL|' || count(*)
FROM stg_credito
WHERE tabela_origem = 'historico_creditos'
  AND NOT (payload_normalizado_json ->> 'dataConfiavel')::boolean;
SELECT 'HISTORICOS_FORA_LEDGER|' || count(*)
FROM stg_credito
WHERE tabela_origem = 'historico_creditos' AND status <> 'PROCESSADO';
SELECT 'SALDOS_DIVERGENTES|' || count(*)
FROM importacao_pendencia WHERE codigo = 'CREDITO_SALDO_DIVERGENTE';
SELECT 'SALDO_ATUAL_DIVERGENTE|' || coalesce(sum(
  (payload_normalizado_json ->> 'saldoOperacional')::bigint
), 0)
FROM stg_credito
WHERE tabela_origem = 'creditos_usuario'
  AND (payload_normalizado_json ->> 'divergenciaHistorica')::boolean;
SELECT 'HISTORICO_DIVERGENTE|' || coalesce(sum(
  (payload_normalizado_json ->> 'historicoTotal')::bigint
), 0)
FROM stg_credito
WHERE tabela_origem = 'creditos_usuario'
  AND (payload_normalizado_json ->> 'divergenciaHistorica')::boolean;
SELECT 'REFERENCIAS_CREDITO_ORFAS|' || count(*)
FROM stg_credito WHERE pendencia_codigo = 'USUARIO_ORFAO_QUARENTENA';
SELECT 'PAGAMENTOS_STAGING|' || count(*) FROM stg_pagamento;
SELECT 'PAGAMENTOS_CANONICOS|' || count(*) FROM pagamento;
SELECT 'PREMIUM_IMPORTADO|' || count(*)
FROM ativacao_beneficio
WHERE idempotency_key LIKE 'import:ativacao-premium:%';
SELECT 'PREMIUM_SEM_GRUPO|' || count(*)
FROM ativacao_beneficio
WHERE idempotency_key LIKE 'import:ativacao-premium:%'
  AND grupo_ativacao_id IS NULL;
SELECT 'PREMIUM_VIGENTE|' || count(*)
FROM ativacao_beneficio a
CROSS JOIN importacao_execucao e
WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
  AND a.inicio_em <= e.iniciado_em
  AND a.fim_em > e.iniciado_em;
SELECT 'PREMIUM_EXPIRADO|' || count(*)
FROM ativacao_beneficio a
CROSS JOIN importacao_execucao e
WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
  AND a.fim_em <= e.iniciado_em;
SELECT 'PREMIUM_QUARENTENA|' || count(*)
FROM stg_premium WHERE status <> 'PROCESSADO';
SELECT 'FAVORITOS|' || count(*) FROM favorito_anuncio;
SELECT 'REFERENCIAS_ORFAS_QUARENTENA|' || count(*)
FROM importacao_pendencia WHERE codigo = 'USUARIO_ORFAO_QUARENTENA';
SELECT 'SNAPSHOT_ID|' || (resumo_json ->> 'snapshotId')
FROM importacao_execucao;
SELECT 'SNAPSHOT_FINGERPRINT|' || (resumo_json ->> 'snapshotFingerprint')
FROM importacao_execucao;

DO $$
DECLARE
  visualizacoes_origem bigint;
  eventos_origem bigint;
  cliques_origem bigint;
  visualizacoes_destino bigint;
  midias_logicas_origem bigint;
  midias_logicas_importadas bigint;
  midias_logicas_quarentena bigint;
  midias_logicas_divergentes bigint;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM anuncio
    WHERE origem_importacao_id IS NOT NULL
      AND status = 'PUBLICADO'
  ) OR EXISTS (
    SELECT 1
    FROM seo_url
    WHERE tipo = 'ANUNCIO' AND (indexavel OR incluir_sitemap)
  ) OR EXISTS (
    SELECT 1
    FROM documento_busca_anuncio
    WHERE status_publicacao = 'PUBLICAVEL' OR tem_midia_valida
  ) THEN
    RAISE EXCEPTION 'anuncio importado foi publicado, indexado ou adicionado ao catalogo';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM anuncio_midia am
    JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
    CROSS JOIN validar_context c
    WHERE ar.storage_provider <> 'R2'
       OR ar.bucket <> c.r2_private_media_bucket
       OR ar.chave_objeto NOT LIKE c.r2_private_media_prefix || 'importacao/anuncios/%'
       OR ar.status_arquivo <> 'PENDENTE'
       OR am.status <> 'PENDENTE'
       OR (am.tipo = 'FOTO' AND am.visibilidade_midia IS NOT NULL)
       OR (am.tipo = 'VIDEO' AND am.visibilidade_midia <> 'RESTRITA_18')
  ) THEN
    RAISE EXCEPTION 'foto ou video importado fora do contrato privado e pendente';
  END IF;

  SELECT
    (resumo_json ->> 'midiasR2PrivadasLogicasOrigem')::bigint,
    (resumo_json ->> 'midiasR2PrivadasLogicasImportadas')::bigint,
    (resumo_json ->> 'midiasR2PrivadasLogicasQuarentena')::bigint,
    (resumo_json ->> 'midiasR2PrivadasLogicasDivergentes')::bigint
  INTO
    midias_logicas_origem,
    midias_logicas_importadas,
    midias_logicas_quarentena,
    midias_logicas_divergentes
  FROM importacao_execucao;

  IF midias_logicas_origem
      <> midias_logicas_importadas + midias_logicas_quarentena
     OR midias_logicas_divergentes <> 0
     OR (
       SELECT count(*)
       FROM importacao_pendencia
       WHERE codigo = 'MIDIA_ORIGEM_AUSENTE'
     ) <> midias_logicas_quarentena THEN
    RAISE EXCEPTION 'reconciliacao persistida de midias logicas possui divergencia';
  END IF;

  IF (
    SELECT count(*)
    FROM anuncio_midia am
    JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
    CROSS JOIN validar_context c
    WHERE ar.storage_provider = 'R2'
      AND ar.bucket = c.r2_private_media_bucket
      AND ar.chave_objeto LIKE c.r2_private_media_prefix || 'importacao/anuncios/%'
  ) <> midias_logicas_importadas THEN
    RAISE EXCEPTION 'quantidade de midias operacionais diverge das recuperaveis';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM importacao_pendencia p
    LEFT JOIN anuncio a
      ON a.id = md5('legacy:anuncio:' || split_part(p.id_origem, ':', 1))::uuid
    WHERE p.codigo = 'MIDIA_ORIGEM_AUSENTE'
      AND (
        a.id IS NULL
        OR a.status <> 'PENDENTE_REVISAO'
        OR a.status_moderacao <> 'PENDENTE'
      )
  ) OR EXISTS (
    SELECT 1
    FROM importacao_pendencia p
    JOIN anuncio_midia m
      ON m.id = md5('legacy:anuncio-midia:' || p.id_origem)::uuid
    WHERE p.codigo = 'MIDIA_ORIGEM_AUSENTE'
  ) OR EXISTS (
    SELECT 1
    FROM stg_midia s
    WHERE s.pendencia_codigo = 'MIDIA_ORIGEM_AUSENTE'
      AND (
        s.status <> 'PENDENTE_REVISAO'
        OR s.entidade_v3_id IS NOT NULL
      )
  ) THEN
    RAISE EXCEPTION 'quarentena de midia ausente criou entidade operacional ou liberou anuncio';
  END IF;

  IF (
    SELECT count(*)
    FROM anuncio
    WHERE origem_importacao_id IS NOT NULL
  ) <> (
    SELECT (resumo_json ->> 'anunciosOrigem')::bigint
    FROM importacao_execucao
  ) THEN
    RAISE EXCEPTION 'quarentena de midia impediu a importacao de anuncio da origem';
  END IF;

  SELECT
    (resumo_json ->> 'visualizacoesCanonicasOrigem')::bigint,
    (resumo_json ->> 'eventosVisualizacaoOrigem')::bigint,
    (resumo_json ->> 'cliquesWhatsappOrigem')::bigint
  INTO visualizacoes_origem, eventos_origem, cliques_origem
  FROM importacao_execucao;

  SELECT coalesce(sum(total), 0)
  INTO visualizacoes_destino
  FROM (
    SELECT
      i.anuncio_id,
      i.total_visualizacoes + count(e.id) AS total
    FROM agregado_visualizacao_inicial i
    LEFT JOIN evento_visualizacao e
      ON e.anuncio_id = i.anuncio_id
     AND e.request_id LIKE 'import:anuncio_view_log:%'
    GROUP BY i.anuncio_id, i.total_visualizacoes
  ) q;

  IF visualizacoes_destino <> visualizacoes_origem
     OR (
       SELECT count(*) FROM evento_visualizacao
       WHERE request_id LIKE 'import:anuncio_view_log:%'
     ) <> eventos_origem
     OR (
       SELECT count(*) FROM clique_whatsapp
       WHERE request_id LIKE 'import:cliques_whatsapp:%'
     ) <> cliques_origem THEN
    RAISE EXCEPTION 'metricas importadas divergem das contagens do snapshot';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM evento_visualizacao
    WHERE request_id LIKE 'import:anuncio_view_log:%'
      AND (
        visitante_hash IS NOT NULL
        OR ip_hash IS NOT NULL
        OR user_agent_hash IS NOT NULL
        OR referer_hash IS NOT NULL
      )
  ) OR EXISTS (
    SELECT 1
    FROM clique_whatsapp
    WHERE request_id LIKE 'import:cliques_whatsapp:%'
      AND (
        visitante_hash IS NOT NULL
        OR ip_hash IS NOT NULL
        OR user_agent_hash IS NOT NULL
      )
  ) THEN
    RAISE EXCEPTION 'metrica legada importou identificador pessoal desnecessario';
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM stg_usuario
    WHERE tabela_origem = 'usuarios'
      AND payload_normalizado_json ->> 'kycStatusOrigem'
          NOT IN ('APROVADO', 'PENDENTE', 'REPROVADO', 'NAO_INICIADO')
  ) OR (
    SELECT count(*) FROM stg_usuario WHERE tabela_origem = 'usuarios'
  ) <> (
    SELECT count(DISTINCT entidade_v3_id) FROM stg_usuario WHERE tabela_origem = 'usuarios'
  ) THEN
    RAISE EXCEPTION 'mapeamento KYC de usuarios possui status invalido ou contagem duplicada';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM credencial_usuario
    WHERE usuario_id = md5('dryrun:kyc-migration-actor')::uuid
  ) THEN
    RAISE EXCEPTION 'ator tecnico da migracao KYC recebeu credencial';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM documento_usuario d
    JOIN arquivo_midia a ON a.id = d.arquivo_midia_id
    WHERE d.status NOT IN ('PENDENTE', 'VALIDADO', 'REJEITADO')
       OR (d.status = 'VALIDADO' AND (d.validado_por IS NULL OR d.validado_em IS NULL))
       OR (d.status <> 'VALIDADO' AND (d.validado_por IS NOT NULL OR d.validado_em IS NOT NULL))
       OR d.parte NOT IN ('UNICO', 'FRENTE', 'VERSO')
       OR a.status_arquivo <> CASE d.status
            WHEN 'VALIDADO' THEN 'VALIDADO'
            WHEN 'REJEITADO' THEN 'REJEITADO'
            ELSE 'PENDENTE'
          END
       OR a.storage_provider <> 'R2'
       OR a.bucket <> (SELECT r2_document_bucket FROM validar_context)
       OR a.chave_objeto NOT LIKE
            (SELECT r2_document_prefix FROM validar_context) || 'importacao/%/sha256/%'
       OR substring(a.chave_objeto FROM length(
            (SELECT r2_document_prefix FROM validar_context)) + 1)
            !~ '^importacao/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*/sha256/[0-9a-f]{2}/[0-9a-f]{64}\.(pdf|jpg|png)$'
       OR a.chave_objeto NOT LIKE
            (SELECT r2_document_prefix FROM validar_context) || 'importacao/%/sha256/'
            || left(a.sha256, 2) || '/' || a.sha256 || '.%'
       OR NOT (
         (a.mime_type = 'application/pdf' AND d.parte = 'UNICO')
         OR (a.mime_type IN ('image/jpeg', 'image/png') AND d.parte IN ('FRENTE', 'VERSO'))
       )
       OR a.tamanho_bytes NOT BETWEEN 1 AND 12582912
       OR a.sha256 !~ '^[0-9a-f]{64}$'
  ) THEN
    RAISE EXCEPTION 'documento KYC promovido fora do contrato privado, historico e tipado';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM documento_usuario d
    LEFT JOIN usuario u ON u.id = d.usuario_id
    LEFT JOIN arquivo_midia a ON a.id = d.arquivo_midia_id
    WHERE u.id IS NULL OR a.id IS NULL
  ) THEN
    RAISE EXCEPTION 'documento KYC orfao no fluxo operacional';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM importacao_mapeamento
    WHERE tabela_origem = 'usuario_documentos'
    GROUP BY execucao_id, id_origem
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'referencia KYC reaplicada na mesma execucao';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_midia
    WHERE tabela_origem = 'usuario_documentos'
      AND payload_normalizado_json ->> 'urlPublicaGerada' <> 'false'
  ) THEN
    RAISE EXCEPTION 'documento KYC recebeu indicacao de URL publica';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_usuario s
    WHERE s.payload_normalizado_json ->> 'kycStatusOrigem' = 'APROVADO'
      AND NOT EXISTS (
        SELECT 1
        FROM documento_usuario d
        WHERE d.usuario_id = s.entidade_v3_id
          AND d.status = 'VALIDADO'
      )
  ) THEN
    RAISE EXCEPTION 'usuario KYC aprovado na origem ficaria sem aprovacao operacional';
  END IF;
END $$;

DO $$
DECLARE
  esperadas bigint;
  promovidas bigint;
BEGIN
  SELECT count(*) INTO esperadas
  FROM stg_premium
  WHERE status = 'PROCESSADO';

  SELECT count(*) INTO promovidas
  FROM ativacao_beneficio
  WHERE idempotency_key LIKE 'import:ativacao-premium:%';

  IF promovidas <> esperadas THEN
    RAISE EXCEPTION 'ativacoes Premium promovidas % divergem do staging seguro %', promovidas, esperadas;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_premium
    WHERE status = 'PROCESSADO'
      AND (
        pendencia_codigo IS NOT NULL
        OR payload_normalizado_json ->> 'versaoImportador' <> 'premium-historico-v1'
        OR payload_normalizado_json ->> 'origemV3' <> 'CREDITO'
        OR coalesce((payload_normalizado_json ->> 'creditosCobrados')::integer, 0) <= 0
      )
  ) THEN
    RAISE EXCEPTION 'staging Premium processado sem origem paga comprovada';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM stg_premium
    WHERE payload_normalizado_json ->> 'codigoLegado' = 'STORIES'
      AND status = 'PROCESSADO'
  ) THEN
    RAISE EXCEPTION 'Story legado foi convertido em Premium comum';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM ativacao_beneficio a
    LEFT JOIN grupo_ativacao_beneficio g ON g.id = a.grupo_ativacao_id
    LEFT JOIN beneficio_premium b ON b.id = a.beneficio_id
    LEFT JOIN usuario u ON u.id = a.usuario_id
    LEFT JOIN anuncio n ON n.id = a.anuncio_id
    WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
      AND (
        g.id IS NULL
        OR b.id IS NULL
        OR u.id IS NULL
        OR n.id IS NULL
        OR n.usuario_id <> a.usuario_id
        OR g.usuario_id <> a.usuario_id
        OR g.anuncio_id <> a.anuncio_id
        OR a.origem <> 'CREDITO'
        OR g.origem <> 'CREDITO'
        OR a.origem <> g.origem
        OR a.custo_creditos_snapshot <= 0
        OR NOT b.ativo
        OR a.inicio_em IS NULL
        OR a.fim_em IS NULL
        OR a.fim_em <= a.inicio_em
        OR g.validade_inicio_em <> a.inicio_em
        OR g.validade_fim_em <> a.fim_em
      )
  ) THEN
    RAISE EXCEPTION 'ativacao Premium promovida fora do contrato conservador';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM ativacao_beneficio a
    JOIN grupo_ativacao_beneficio g ON g.id = a.grupo_ativacao_id
    CROSS JOIN importacao_execucao e
    WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
      AND (
        (a.inicio_em > e.iniciado_em AND (a.status <> 'AGENDADA' OR g.status <> 'PLANEJADO'))
        OR (a.inicio_em <= e.iniciado_em AND a.fim_em > e.iniciado_em
            AND (a.status <> 'ATIVA' OR g.status <> 'ATIVO'))
        OR (a.fim_em <= e.iniciado_em AND (a.status <> 'EXPIRADA' OR g.status <> 'EXPIRADO'))
      )
  ) THEN
    RAISE EXCEPTION 'estado Premium importado diverge da janela temporal do snapshot';
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
    RAISE EXCEPTION 'ativacoes Premium importadas duplicadas ou sobrepostas';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM movimento_credito m
    JOIN ativacao_beneficio a
      ON m.referencia_tipo = 'ATIVACAO_BENEFICIO'
     AND m.referencia_id = a.id
    WHERE a.idempotency_key LIKE 'import:ativacao-premium:%'
  ) THEN
    RAISE EXCEPTION 'importacao Premium historica criou movimento de credito';
  END IF;
END $$;

DO $$
DECLARE
  movimentos bigint;
  usuarios bigint;
  saldo_movimentos bigint;
  saldo_staging bigint;
BEGIN
  SELECT count(*), count(DISTINCT usuario_id), coalesce(sum(quantidade), 0)
  INTO movimentos, usuarios, saldo_movimentos
  FROM movimento_credito
  WHERE tipo = 'MIGRACAO_SALDO_INICIAL';

  SELECT coalesce(sum((payload_normalizado_json ->> 'saldoOperacional')::bigint), 0)
  INTO saldo_staging
  FROM stg_credito
  WHERE tabela_origem = 'creditos_usuario'
    AND status = 'PROCESSADO'
    AND (payload_normalizado_json ->> 'saldoOperacional')::integer > 0;

  IF movimentos <> usuarios THEN
    RAISE EXCEPTION 'mais de um saldo inicial por usuario';
  END IF;

  IF saldo_movimentos <> saldo_staging THEN
    RAISE EXCEPTION 'saldo inicial nao fecha com staging operacional aceito';
  END IF;

  IF EXISTS (
    SELECT 1 FROM movimento_credito
    WHERE tipo = 'MIGRACAO_SALDO_INICIAL'
      AND (
        origem <> 'IMPORTACAO'
        OR direcao <> 'CREDITO'
        OR saldo_antes <> 0
        OR metadata_json ->> 'versaoImportador' <> 'ledger-saldo-inicial-v1'
      )
  ) THEN
    RAISE EXCEPTION 'movimento inicial fora do contrato canonico';
  END IF;

  IF EXISTS (
    SELECT 1 FROM stg_credito s
    JOIN movimento_credito m ON m.referencia_tipo = 'HISTORICO_CREDITOS_LEGADO'
    WHERE s.tabela_origem = 'historico_creditos'
  ) THEN
    RAISE EXCEPTION 'historico legado foi promovido indevidamente';
  END IF;
END $$;

WITH hashes AS (
  SELECT 'usuario' dominio,
         md5(coalesce(string_agg(
           concat_ws(':', id, nome, email_normalizado, status, tipo_conta,
                     coalesce(data_nascimento::text, '')),
           '|' ORDER BY id), '')) valor
  FROM usuario
  UNION ALL
  SELECT 'localidade', md5(coalesce(string_agg(
    concat_ws(':', tipo, id, pai, nome, slug), '|' ORDER BY tipo, id), ''))
  FROM (
    SELECT 'E' tipo, id, '' pai, nome, uf slug FROM estado
    UNION ALL SELECT 'C', id, estado_id::text, nome, slug FROM cidade
    UNION ALL SELECT 'B', id, cidade_id::text, nome, slug FROM bairro
  ) q
  UNION ALL
  SELECT 'anuncio', md5(coalesce(string_agg(
    concat_ws(':', id, usuario_id, slug, status, status_moderacao,
              categoria, coalesce(preco::text, ''),
              coalesce(publicado_em::text, '')),
    '|' ORDER BY id), ''))
  FROM anuncio
  UNION ALL
  SELECT 'midia', md5(coalesce(string_agg(
    concat_ws(':', am.id, am.anuncio_id, am.arquivo_midia_id, am.tipo,
              am.finalidade, am.ordem, am.status, am.visibilidade_midia,
              ar.storage_provider, ar.bucket, ar.chave_objeto,
              ar.sha256, ar.tamanho_bytes, ar.mime_type),
    '|' ORDER BY am.id), ''))
  FROM anuncio_midia am JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
  UNION ALL
  SELECT 'visualizacao', md5(coalesce(string_agg(
    concat_ws(':', i.anuncio_id, i.total_visualizacoes,
              i.snapshot_fingerprint, i.origem_hash, i.snapshot_corte_em,
              coalesce(e.eventos, 0)),
    '|' ORDER BY i.anuncio_id), ''))
  FROM agregado_visualizacao_inicial i
  LEFT JOIN (
    SELECT anuncio_id, count(*) AS eventos
    FROM evento_visualizacao
    WHERE request_id LIKE 'import:anuncio_view_log:%'
    GROUP BY anuncio_id
  ) e ON e.anuncio_id = i.anuncio_id
  UNION ALL
  SELECT 'clique-whatsapp', md5(coalesce(string_agg(
    concat_ws(':', id, anuncio_id, criado_em, request_id),
    '|' ORDER BY id), ''))
  FROM clique_whatsapp
  WHERE request_id LIKE 'import:cliques_whatsapp:%'
  UNION ALL
  SELECT 'kyc', md5(coalesce(string_agg(
    concat_ws(':', d.id, d.usuario_id, d.arquivo_midia_id, d.envio_id,
              d.parte, d.status, ar.bucket, ar.chave_objeto,
              ar.sha256, ar.tamanho_bytes, ar.mime_type),
    '|' ORDER BY d.id), ''))
  FROM documento_usuario d JOIN arquivo_midia ar ON ar.id = d.arquivo_midia_id
  UNION ALL
  SELECT 'ledger', md5(coalesce(string_agg(
    concat_ws(':', id, usuario_id, tipo, direcao, quantidade,
              saldo_antes, saldo_depois, criado_em),
    '|' ORDER BY id), ''))
  FROM movimento_credito WHERE origem = 'IMPORTACAO'
  UNION ALL
  SELECT 'premium', md5(coalesce(string_agg(
    concat_ws(':', id, beneficio_id, usuario_id, anuncio_id,
              grupo_ativacao_id, inicio_em, fim_em, status,
              custo_creditos_snapshot),
    '|' ORDER BY id), ''))
  FROM ativacao_beneficio WHERE idempotency_key LIKE 'import:ativacao-premium:%'
  UNION ALL
  SELECT 'staging', md5(coalesce(string_agg(
    concat_ws(':', tabela, total, processados, pendentes),
    '|' ORDER BY tabela), ''))
  FROM (
    SELECT 'usuario' tabela, count(*) total,
           count(*) FILTER (WHERE status = 'PROCESSADO') processados,
           count(*) FILTER (WHERE status <> 'PROCESSADO') pendentes FROM stg_usuario
    UNION ALL SELECT 'anuncio', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_anuncio
    UNION ALL SELECT 'localidade', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_localidade
    UNION ALL SELECT 'midia', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_midia
    UNION ALL SELECT 'story', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_story
    UNION ALL SELECT 'pagamento', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_pagamento
    UNION ALL SELECT 'credito', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_credito
    UNION ALL SELECT 'premium', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_premium
    UNION ALL SELECT 'url', count(*), count(*) FILTER (WHERE status = 'PROCESSADO'), count(*) FILTER (WHERE status <> 'PROCESSADO') FROM stg_url
  ) q
)
SELECT 'HASH_' || upper(dominio) || '|' || valor FROM hashes ORDER BY dominio;

WITH hashes AS (
  SELECT md5(coalesce(string_agg(
    concat_ws(':', id, nome, email_normalizado, status, tipo_conta,
              coalesce(data_nascimento::text, '')),
    '|' ORDER BY id), '')) valor FROM usuario
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', id, usuario_id, slug, status, status_moderacao,
              categoria, coalesce(preco::text, ''),
              coalesce(publicado_em::text, '')),
    '|' ORDER BY id), '')) FROM anuncio
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', am.id, am.anuncio_id, am.arquivo_midia_id, am.tipo,
              am.finalidade, am.ordem, am.status, am.visibilidade_midia,
              ar.storage_provider, ar.bucket, ar.chave_objeto,
              ar.sha256, ar.tamanho_bytes, ar.mime_type),
    '|' ORDER BY am.id), ''))
  FROM anuncio_midia am JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', i.anuncio_id, i.total_visualizacoes,
              i.snapshot_fingerprint, i.origem_hash, i.snapshot_corte_em,
              coalesce(e.eventos, 0)),
    '|' ORDER BY i.anuncio_id), ''))
  FROM agregado_visualizacao_inicial i
  LEFT JOIN (
    SELECT anuncio_id, count(*) AS eventos
    FROM evento_visualizacao
    WHERE request_id LIKE 'import:anuncio_view_log:%'
    GROUP BY anuncio_id
  ) e ON e.anuncio_id = i.anuncio_id
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', id, anuncio_id, criado_em, request_id),
    '|' ORDER BY id), ''))
  FROM clique_whatsapp
  WHERE request_id LIKE 'import:cliques_whatsapp:%'
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', d.id, d.usuario_id, d.arquivo_midia_id, d.envio_id,
              d.parte, d.status, ar.bucket, ar.chave_objeto,
              ar.sha256, ar.tamanho_bytes, ar.mime_type),
    '|' ORDER BY d.id), ''))
  FROM documento_usuario d JOIN arquivo_midia ar ON ar.id = d.arquivo_midia_id
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', id, usuario_id, tipo, direcao, quantidade,
              saldo_antes, saldo_depois, criado_em),
    '|' ORDER BY id), ''))
  FROM movimento_credito WHERE origem = 'IMPORTACAO'
  UNION ALL
  SELECT md5(coalesce(string_agg(
    concat_ws(':', id, beneficio_id, usuario_id, anuncio_id,
              grupo_ativacao_id, inicio_em, fim_em, status,
              custo_creditos_snapshot),
    '|' ORDER BY id), ''))
  FROM ativacao_beneficio WHERE idempotency_key LIKE 'import:ativacao-premium:%'
)
SELECT 'FINGERPRINT|' || md5(string_agg(valor, '|' ORDER BY valor)) FROM hashes;
