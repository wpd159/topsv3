\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

CREATE TEMP TABLE validar_context AS
SELECT :'r2_document_bucket'::text AS r2_document_bucket;

SELECT 'USUARIOS|' || count(*) FROM usuario;
SELECT 'ANUNCIOS|' || count(*) FROM anuncio;
SELECT 'ANUNCIOS_PUBLICADOS|' || count(*) FROM anuncio WHERE status = 'PUBLICADO';
SELECT 'ANUNCIOS_FORA_CATALOGO|' || count(*) FROM anuncio WHERE status <> 'PUBLICADO';
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
SELECT 'MIDIA_SEM_EVIDENCIA_PUBLICA|' || count(*)
FROM stg_midia WHERE pendencia_codigo = 'MIDIA_SEM_EVIDENCIA_PUBLICA_ANONIMA';
SELECT 'MIDIA_PRIVADA_QUARENTENA|' || count(*)
FROM stg_midia WHERE pendencia_codigo = 'MIDIA_PRIVADA_SEM_VERIFICACAO_R2';
SELECT 'MIDIA_NAO_LIVRE_QUARENTENA|' || count(*)
FROM stg_midia WHERE pendencia_codigo = 'MIDIA_NAO_LIVRE_QUARENTENA';
SELECT 'MIDIA_PLACEHOLDER_QUARENTENA|' || count(*)
FROM stg_midia WHERE pendencia_codigo = 'MIDIA_PLACEHOLDER_INSTITUCIONAL_QUARENTENA';
SELECT 'MIDIA_PUBLICA_R2_QUARENTENA|' || count(*)
FROM stg_midia WHERE pendencia_codigo IN (
  'MIDIA_PUBLICA_R2_QUARENTENA',
  'MIDIA_PUBLICA_R2_CHECKSUM_DIVERGENTE'
);
SELECT 'MIDIA_R2_PUBLICA|' || count(*)
FROM arquivo_midia
WHERE storage_provider = 'R2'
  AND bucket = :'r2_public_bucket'
  AND chave_objeto LIKE 'hml/midias-aprovadas/importacao/sha256/%';
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
SELECT 'MIDIA_RESTRITA_COM_PROVIDER_PUBLICO_RUNTIME|' || count(*)
FROM anuncio_midia am
JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
WHERE am.visibilidade_midia = 'RESTRITA_18'
  AND ar.storage_provider = 'R2';
SELECT 'SEO_INDEXAVEL_POR_EVIDENCIA|' || count(*)
FROM seo_url WHERE tipo = 'ANUNCIO' AND indexavel AND incluir_sitemap;
SELECT 'SEO_INDEXAVEL_SEM_MIDIA_REAL|' || count(*)
FROM seo_url s
WHERE s.tipo = 'ANUNCIO'
  AND s.indexavel
  AND NOT EXISTS (
    SELECT 1
    FROM anuncio_midia am
    WHERE am.anuncio_id = s.entidade_id
      AND am.tipo = 'FOTO'
      AND am.status = 'PUBLICAVEL'
      AND am.visibilidade_midia = 'LIVRE'
  );
SELECT 'STORIES_QUARENTENA|' || count(*) FROM stg_story;
SELECT 'KYC_CANONICO|' || count(*) FROM documento_usuario;
SELECT 'KYC_PENDENTE|' || count(*) FROM documento_usuario WHERE status = 'PENDENTE';
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
  AND a.chave_objeto LIKE 'hml/documentos/importacao/sha256/%';
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
SELECT 'PREMIUM_IMPORTADO|' || count(*) FROM ativacao_beneficio WHERE origem = 'IMPORTACAO';
SELECT 'PREMIUM_SEM_GRUPO|' || count(*)
FROM ativacao_beneficio
WHERE origem = 'IMPORTACAO' AND grupo_ativacao_id IS NULL;
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
BEGIN
  IF EXISTS (
    SELECT 1
    FROM documento_usuario d
    JOIN arquivo_midia a ON a.id = d.arquivo_midia_id
    WHERE d.status <> 'PENDENTE'
       OR d.validado_por IS NOT NULL
       OR d.validado_em IS NOT NULL
       OR d.revisado_por IS NOT NULL
       OR d.revisado_em IS NOT NULL
       OR d.parte <> 'UNICO'
       OR a.status_arquivo <> 'PENDENTE'
       OR a.storage_provider <> 'R2'
       OR a.bucket <> (SELECT r2_document_bucket FROM validar_context)
       OR a.chave_objeto NOT LIKE 'hml/documentos/importacao/sha256/%'
       OR a.mime_type <> 'application/pdf'
       OR a.tamanho_bytes NOT BETWEEN 1 AND 12582912
       OR a.sha256 !~ '^[0-9a-f]{64}$'
  ) THEN
    RAISE EXCEPTION 'documento KYC promovido fora do contrato privado e pendente';
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
  FROM ativacao_beneficio WHERE origem = 'IMPORTACAO'
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
  FROM ativacao_beneficio WHERE origem = 'IMPORTACAO'
)
SELECT 'FINGERPRINT|' || md5(string_agg(valor, '|' ORDER BY valor)) FROM hashes;
