\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

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
SELECT 'KYC_HTTP_QUARENTENA|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_REFERENCIA_HTTP_QUARENTENA';
SELECT 'KYC_PRIVADO_NAO_VERIFICADO|' || count(*)
FROM importacao_pendencia WHERE codigo = 'KYC_REFERENCIA_PRIVADA_NAO_VERIFICADA';
SELECT 'MOVIMENTOS_LEDGER|' || count(*) FROM movimento_credito WHERE origem = 'IMPORTACAO';
SELECT 'USUARIOS_LEDGER|' || count(*) FROM saldo_credito_usuario;
SELECT 'SALDO_LEDGER|' || coalesce(sum(saldo_atual), 0) FROM saldo_credito_usuario;
SELECT 'MOVIMENTOS_LEDGER_QUARENTENA|' || count(*)
FROM stg_credito WHERE pendencia_codigo = 'LEDGER_SEQUENCIA_NEGATIVA';
SELECT 'MOVIMENTOS_SEM_DATA_CONFIAVEL|' || count(*)
FROM stg_credito WHERE pendencia_codigo = 'MOVIMENTO_SEM_DATA_CONFIAVEL';
SELECT 'SALDOS_MUTAVEIS_QUARENTENA|' || count(*)
FROM stg_credito WHERE pendencia_codigo = 'SALDO_MUTAVEL_NAO_AUTORITATIVO';
SELECT 'SALDOS_DIVERGENTES|' || count(*)
FROM importacao_pendencia WHERE codigo = 'CREDITO_SALDO_DIVERGENTE';
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
