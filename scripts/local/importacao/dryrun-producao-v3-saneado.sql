\set ON_ERROR_STOP on

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

-- Transformacao conservadora do snapshot legado para um banco V3 descartavel.
-- Pre-condicoes:
--   1. source_snapshot existe no mesmo PostgreSQL local;
--   2. os TSVs /tmp/dryrun-r2-public-media.tsv e
--      /tmp/dryrun-r2-kyc-documents.tsv contem resultados sanitizados;
--   3. snapshot_at, snapshot_id, snapshot_fingerprint, buckets/prefixos R2 e
--      a origem publica preservada sao informados pelo chamador.
-- Este arquivo nao contem dados, credenciais ou acesso a producao.

CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE SCHEMA legacy;
CREATE SERVER legacy_source
  FOREIGN DATA WRAPPER postgres_fdw
  OPTIONS (host '/var/run/postgresql', dbname 'source_snapshot');
CREATE USER MAPPING FOR CURRENT_USER
  SERVER legacy_source
  OPTIONS (user 'topsv3dry');

IMPORT FOREIGN SCHEMA public LIMIT TO (
  usuarios,
  anuncios,
  estado,
  cidade,
  bairro,
  anuncio_servicos,
  anuncio_local_atendimento,
  anuncio_fotos,
  anuncio_videos,
  protected_media_assets,
  content_classifications,
  anuncio_revisions,
  stories,
  usuario_documentos,
  advertiser_verification_requests,
  usuario_favoritos,
  feature_ativacao,
  feature_catalogo,
  feature_catalogo_duracoes,
  creditos_usuario,
  historico_creditos,
  pagamentos_mp,
  suporte_mensagens
) FROM SERVER legacy_source INTO legacy;

CREATE TEMP TABLE dryrun_r2_public_media (
  anuncio_origem_id bigint NOT NULL,
  reference_hash text NOT NULL,
  object_key text,
  sha256 text,
  tamanho_bytes bigint NOT NULL,
  mime_type text,
  largura integer NOT NULL,
  altura integer NOT NULL,
  status text NOT NULL,
  motivo text,
  PRIMARY KEY (anuncio_origem_id, reference_hash)
);

\copy dryrun_r2_public_media (anuncio_origem_id, reference_hash, object_key, sha256, tamanho_bytes, mime_type, largura, altura, status, motivo) FROM '/tmp/dryrun-r2-public-media.tsv' WITH (FORMAT text, DELIMITER E'\t', NULL '')

CREATE TEMP TABLE dryrun_r2_kyc_documents (
  usuario_v3_id uuid NOT NULL,
  reference_hash text NOT NULL,
  canonical_reference_hash text NOT NULL,
  object_key text,
  sha256 text,
  tamanho_bytes bigint NOT NULL,
  mime_type text,
  extensao text,
  parte text NOT NULL,
  envio_hash text,
  status text NOT NULL,
  motivo text,
  kyc_status text NOT NULL,
  PRIMARY KEY (usuario_v3_id, reference_hash)
);

\copy dryrun_r2_kyc_documents (usuario_v3_id, reference_hash, canonical_reference_hash, object_key, sha256, tamanho_bytes, mime_type, extensao, parte, envio_hash, status, motivo, kyc_status) FROM '/tmp/dryrun-r2-kyc-documents.tsv' WITH (FORMAT text, DELIMITER E'\t', NULL '')

BEGIN;

CREATE TEMP TABLE dryrun_context AS
SELECT
  md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id,
  :'snapshot_at'::timestamptz AS snapshot_at,
  :'snapshot_id'::text AS snapshot_id,
  :'snapshot_fingerprint'::text AS snapshot_fingerprint,
  :'r2_public_media_bucket'::text AS r2_public_media_bucket,
  :'r2_public_media_prefix'::text AS r2_public_media_prefix,
  :'r2_private_media_bucket'::text AS r2_private_media_bucket,
  :'r2_private_media_prefix'::text AS r2_private_media_prefix,
  :'r2_preserved_public_bucket'::text AS r2_preserved_public_bucket,
  regexp_replace(:'r2_preserved_public_base_url'::text, '/+$', '') AS r2_preserved_public_base_url,
  :'r2_preserved_public_prefix'::text AS r2_preserved_public_prefix,
  :'r2_document_bucket'::text AS r2_document_bucket,
  :'r2_document_prefix'::text AS r2_document_prefix,
  md5(concat_ws('|',
    :'r2_public_media_bucket', :'r2_public_media_prefix',
    :'r2_private_media_bucket', :'r2_private_media_prefix',
    :'r2_document_bucket', :'r2_document_prefix'
  )) AS storage_destination_fingerprint,
  md5('dryrun:kyc-migration-actor')::uuid AS kyc_migration_actor_id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM dryrun_context
    WHERE length(trim(r2_public_media_bucket)) = 0
       OR length(trim(r2_private_media_bucket)) = 0
       OR length(trim(r2_document_bucket)) = 0
       OR r2_public_media_bucket !~ '^[A-Za-z0-9][A-Za-z0-9._-]*$'
       OR r2_private_media_bucket !~ '^[A-Za-z0-9][A-Za-z0-9._-]*$'
       OR r2_document_bucket !~ '^[A-Za-z0-9][A-Za-z0-9._-]*$'
       OR r2_public_media_prefix !~ '^hml/[A-Za-z0-9._/-]+/$'
       OR r2_private_media_prefix !~ '^hml/[A-Za-z0-9._/-]+/$'
       OR r2_document_prefix !~ '^hml/[A-Za-z0-9._/-]+/$'
       OR r2_public_media_prefix LIKE '%..%'
       OR r2_private_media_prefix LIKE '%..%'
       OR r2_document_prefix LIKE '%..%'
       OR r2_public_media_bucket = r2_preserved_public_bucket
       OR r2_preserved_public_base_url !~ '^https://[^/?#]+$'
       OR r2_preserved_public_prefix !~ '^[A-Za-z0-9._/-]+/$'
       OR r2_preserved_public_prefix LIKE '/%'
       OR r2_preserved_public_prefix LIKE '%..%'
       OR (
         r2_public_media_bucket = r2_private_media_bucket
         AND (
           r2_public_media_prefix LIKE r2_private_media_prefix || '%'
           OR r2_private_media_prefix LIKE r2_public_media_prefix || '%'
         )
       )
       OR (
         r2_public_media_bucket = r2_document_bucket
         AND (
           r2_public_media_prefix LIKE r2_document_prefix || '%'
           OR r2_document_prefix LIKE r2_public_media_prefix || '%'
         )
       )
       OR (
         r2_private_media_bucket = r2_document_bucket
         AND (
           r2_private_media_prefix LIKE r2_document_prefix || '%'
           OR r2_document_prefix LIKE r2_private_media_prefix || '%'
         )
       )
  ) THEN
    RAISE EXCEPTION 'configuracao R2 de destino invalida, sobreposta ou sem isolamento da origem';
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM dryrun_r2_public_media r
    CROSS JOIN dryrun_context c
    WHERE r.status IN ('MIGRADA', 'PRESERVADA')
      AND (
        r.object_key IS NULL
        OR r.sha256 !~ '^[0-9a-f]{64}$'
        OR r.object_key NOT LIKE c.r2_public_media_prefix || 'importacao/sha256/%'
        OR substring(r.object_key FROM length(c.r2_public_media_prefix) + 1)
            !~ '^importacao/sha256/[0-9a-f]{2}/[0-9a-f]{64}\.(jpg|jpeg|png|webp)$'
        OR r.object_key NOT LIKE c.r2_public_media_prefix || 'importacao/sha256/'
            || left(r.sha256, 2) || '/' || r.sha256 || '.%'
      )
  ) THEN
    RAISE EXCEPTION 'manifesto R2 publico nao corresponde ao prefixo e objetos do destino configurado';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_r2_kyc_documents r
    CROSS JOIN dryrun_context c
    WHERE r.status IN ('MIGRADA', 'PRESERVADA')
      AND (
        r.object_key IS NULL
        OR r.sha256 !~ '^[0-9a-f]{64}$'
        OR r.object_key NOT LIKE c.r2_document_prefix || 'importacao/%/sha256/%'
        OR substring(r.object_key FROM length(c.r2_document_prefix) + 1)
            !~ '^importacao/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*/sha256/[0-9a-f]{2}/[0-9a-f]{64}\.(pdf|jpg|png)$'
        OR r.object_key NOT LIKE c.r2_document_prefix || 'importacao/%/sha256/'
            || left(r.sha256, 2) || '/' || r.sha256 || '.%'
      )
  ) THEN
    RAISE EXCEPTION 'manifesto R2 documental nao corresponde ao prefixo e objetos do destino configurado';
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM importacao_execucao e
    CROSS JOIN dryrun_context c
    WHERE e.id = c.execucao_id
      AND (
        e.sistema_origem <> 'TOPSDOJOB_PRODUCAO_SNAPSHOT_READONLY'
        OR e.status NOT IN ('CONCLUIDA', 'CONCLUIDA_COM_PENDENCIAS')
        OR e.iniciado_em <> c.snapshot_at
        OR (e.resumo_json ->> 'snapshotId') IS DISTINCT FROM c.snapshot_id
        OR (e.resumo_json ->> 'snapshotFingerprint') IS DISTINCT FROM c.snapshot_fingerprint
        OR (e.resumo_json ->> 'storageDestinationFingerprint')
            IS DISTINCT FROM c.storage_destination_fingerprint
      )
  ) THEN
    RAISE EXCEPTION 'execucao existente nao corresponde integralmente ao snapshot solicitado';
  END IF;
END $$;

SELECT NOT EXISTS (
  SELECT 1
  FROM importacao_execucao e
  CROSS JOIN dryrun_context c
  WHERE e.id = c.execucao_id
) AS dryrun_snapshot_novo
\gset

\if :dryrun_snapshot_novo

INSERT INTO importacao_execucao (
  id, sistema_origem, status, iniciado_em, finalizado_em, resumo_json, criado_em
)
SELECT
  execucao_id,
  'TOPSDOJOB_PRODUCAO_SNAPSHOT_READONLY',
  'EM_EXECUCAO',
  snapshot_at,
  NULL,
  jsonb_build_object(
    'modo', 'DRY_RUN_SANEADO',
    'snapshotUtc', snapshot_at,
    'snapshotId', snapshot_id,
    'snapshotFingerprint', snapshot_fingerprint,
    'storageDestinationFingerprint', storage_destination_fingerprint
  ),
  snapshot_at
FROM dryrun_context;

-- Usuarios: todos os registros da origem sao preservados, mas credenciais,
-- sessoes e identificadores pessoais nao sao promovidos.
CREATE TEMP TABLE dryrun_usuario AS
WITH ranked AS (
  SELECT
    u.*,
    count(*) OVER (PARTITION BY lower(trim(u.username))) AS username_total,
    row_number() OVER (PARTITION BY lower(trim(u.username)) ORDER BY u.id) AS username_ordem
  FROM legacy.usuarios u
)
SELECT
  r.id AS origem_id,
  md5('legacy:usuario:' || r.id)::uuid AS id,
  CASE
    WHEN nullif(trim(r.username), '') IS NULL THEN 'usuario-' || r.id
    WHEN r.username_total > 1 THEN 'usuario-' || r.id
    ELSE left(trim(r.username), 180)
  END AS nome,
  'legacy-' || r.id || '@example.invalid' AS email_normalizado,
  CASE WHEN r.status = 'ATIVO' THEN 'ATIVO' ELSE 'DESATIVADO' END AS status,
  CASE WHEN r.role IN ('ADMIN', 'MODERADOR') THEN 'STAFF' ELSE 'ANUNCIANTE' END AS tipo_conta,
  r.data_nascimento,
  r.role,
  CASE
    WHEN r.advertiser_verification_status::text = 'APROVADO' THEN 'APROVADO'
    WHEN r.advertiser_verification_status::text IN ('PENDENTE', 'EM_REVISAO') THEN 'PENDENTE'
    WHEN r.advertiser_verification_status::text IN ('REPROVADO', 'SUSPENSO') THEN 'REPROVADO'
    WHEN EXISTS (
      SELECT 1
      FROM legacy.usuario_documentos d
      WHERE d.usuario_id = r.id
    ) THEN 'APROVADO'
    ELSE 'NAO_INICIADO'
  END AS kyc_status_origem,
  r.criado_em AT TIME ZONE 'America/Sao_Paulo' AS criado_em,
  r.username_total > 1 AS username_conflitante,
  md5(coalesce(lower(trim(r.username)), '')) AS username_hash
FROM ranked r;

INSERT INTO usuario (
  id, nome, email_normalizado, telefone_normalizado, status, tipo_conta,
  criado_em, atualizado_em, desativado_em, versao, data_nascimento,
  nome_civil, cpf_normalizado
)
SELECT
  id, nome, email_normalizado, NULL, status, tipo_conta,
  criado_em, criado_em, NULL, 0, data_nascimento, NULL, NULL
FROM dryrun_usuario;

-- Ator tecnico sem credencial registra apenas a proveniencia da preservacao
-- de decisoes KYC legadas quando a origem nao possui revisor identificavel.
INSERT INTO usuario (
  id, nome, email_normalizado, telefone_normalizado, status, tipo_conta,
  criado_em, atualizado_em, desativado_em, versao, data_nascimento,
  nome_civil, cpf_normalizado
)
SELECT
  c.kyc_migration_actor_id,
  'Importacao KYC',
  NULL,
  NULL,
  'ATIVO',
  'SISTEMA',
  c.snapshot_at,
  c.snapshot_at,
  NULL,
  0,
  NULL,
  NULL,
  NULL
FROM dryrun_context c
WHERE EXISTS (
  SELECT 1
  FROM dryrun_usuario u
  WHERE u.kyc_status_origem IN ('APROVADO', 'REPROVADO')
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO papel_usuario (usuario_id, papel, criado_por, criado_em)
SELECT
  id,
  CASE WHEN role = 'ADMIN' THEN 'ADMIN'
       WHEN role = 'MODERADOR' THEN 'MODERADOR'
       ELSE 'USUARIO' END,
  NULL,
  criado_em
FROM dryrun_usuario;

INSERT INTO stg_usuario (
  id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:usuario:' || u.origem_id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'usuarios',
  u.origem_id::text,
  u.username_hash,
  jsonb_build_object(
    'usernameConflitante', u.username_conflitante,
    'nascimentoPresente', u.data_nascimento IS NOT NULL,
    'kycStatusOrigem', u.kyc_status_origem,
    'credencialImportada', false
  ),
  'PROCESSADO',
  CASE WHEN u.username_conflitante THEN 'USUARIO_USERNAME_RECONCILIADO' END,
  u.id,
  c.snapshot_at,
  c.snapshot_at
FROM dryrun_usuario u CROSS JOIN dryrun_context c;

INSERT INTO importacao_mapeamento (
  id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
  entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
)
SELECT
  md5('map:usuario:' || u.origem_id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'usuarios',
  u.origem_id::text,
  u.username_hash,
  'USUARIO',
  u.id,
  'MAPEADO',
  c.snapshot_at,
  c.snapshot_at
FROM dryrun_usuario u CROSS JOIN dryrun_context c;

-- Localidades: slugs de bairro sao deterministas e recebem sufixo do ID
-- apenas quando o mesmo slug-base colide dentro da cidade.
CREATE TEMP TABLE dryrun_estado AS
SELECT
  e.id AS origem_id,
  md5('legacy:estado:' || e.id)::uuid AS id,
  upper(e.uf) AS uf,
  trim(e.nome) AS nome,
  lower(unaccent(trim(e.nome))) AS nome_normalizado
FROM legacy.estado e;

CREATE TEMP TABLE dryrun_cidade AS
WITH base AS (
  SELECT
    c.*,
    CASE
      WHEN c.slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' THEN c.slug
      ELSE trim(both '-' FROM regexp_replace(lower(unaccent(trim(c.nome))), '[^a-z0-9]+', '-', 'g'))
    END AS slug_base
  FROM legacy.cidade c
), ranked AS (
  SELECT
    b.*,
    row_number() OVER (PARTITION BY b.estado_id, b.slug_base ORDER BY b.id) AS slug_ordem
  FROM base b
)
SELECT
  r.id AS origem_id,
  md5('legacy:cidade:' || r.id)::uuid AS id,
  md5('legacy:estado:' || r.estado_id)::uuid AS estado_id,
  trim(r.nome) AS nome,
  lower(unaccent(trim(r.nome))) AS nome_normalizado,
  CASE WHEN r.slug_ordem = 1 THEN r.slug_base ELSE r.slug_base || '-' || r.id END AS slug
FROM ranked r;

CREATE TEMP TABLE dryrun_bairro AS
WITH base AS (
  SELECT
    b.*,
    trim(both '-' FROM regexp_replace(lower(unaccent(trim(b.nome))), '[^a-z0-9]+', '-', 'g')) AS slug_base
  FROM legacy.bairro b
), ranked AS (
  SELECT
    b.*,
    row_number() OVER (PARTITION BY b.cidade_id, b.slug_base ORDER BY b.id) AS slug_ordem
  FROM base b
)
SELECT
  r.id AS origem_id,
  md5('legacy:bairro:' || r.id)::uuid AS id,
  md5('legacy:cidade:' || r.cidade_id)::uuid AS cidade_id,
  trim(r.nome) AS nome,
  lower(unaccent(trim(r.nome))) AS nome_normalizado,
  CASE WHEN r.slug_ordem = 1 THEN r.slug_base ELSE r.slug_base || '-' || r.id END AS slug,
  r.slug_ordem > 1 AS slug_colisao
FROM ranked r;

INSERT INTO estado (id, uf, nome, nome_normalizado, criado_em)
SELECT e.id, e.uf, e.nome, e.nome_normalizado, c.snapshot_at
FROM dryrun_estado e CROSS JOIN dryrun_context c;

INSERT INTO cidade (id, estado_id, nome, nome_normalizado, slug, criado_em)
SELECT x.id, x.estado_id, x.nome, x.nome_normalizado, x.slug, c.snapshot_at
FROM dryrun_cidade x CROSS JOIN dryrun_context c;

INSERT INTO bairro (id, cidade_id, nome, nome_normalizado, slug, criado_em)
SELECT b.id, b.cidade_id, b.nome, b.nome_normalizado, b.slug, c.snapshot_at
FROM dryrun_bairro b CROSS JOIN dryrun_context c;

INSERT INTO stg_localidade (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:estado:' || e.origem_id)::uuid, c.execucao_id,
  'TOPSDOJOB_PRODUCAO', 'estado', e.origem_id::text,
  jsonb_build_object('tipo', 'ESTADO', 'uf', e.uf),
  'PROCESSADO', NULL, e.id, c.snapshot_at, c.snapshot_at
FROM dryrun_estado e CROSS JOIN dryrun_context c
UNION ALL
SELECT
  md5('stg:cidade:' || x.origem_id)::uuid, c.execucao_id,
  'TOPSDOJOB_PRODUCAO', 'cidade', x.origem_id::text,
  jsonb_build_object('tipo', 'CIDADE', 'slug', x.slug),
  'PROCESSADO', NULL, x.id, c.snapshot_at, c.snapshot_at
FROM dryrun_cidade x CROSS JOIN dryrun_context c
UNION ALL
SELECT
  md5('stg:bairro:' || b.origem_id)::uuid, c.execucao_id,
  'TOPSDOJOB_PRODUCAO', 'bairro', b.origem_id::text,
  jsonb_build_object('tipo', 'BAIRRO', 'slug', b.slug, 'colisaoResolvida', b.slug_colisao),
  'PROCESSADO', CASE WHEN b.slug_colisao THEN 'BAIRRO_SLUG_COLISAO_RESOLVIDA' END,
  b.id, c.snapshot_at, c.snapshot_at
FROM dryrun_bairro b CROSS JOIN dryrun_context c;

-- Primeira publicacao: revisao aprovada vinculada ao anuncio e a evidencia
-- preferida. Na ausencia, criado_em e usado e explicitamente marcado.
CREATE TEMP TABLE dryrun_publicacao AS
SELECT
  a.id AS anuncio_origem_id,
  min(r.reviewed_at) FILTER (
    WHERE r.status = 'APROVADA' AND r.reviewed_at IS NOT NULL
  ) AS recuperada_em,
  coalesce(
    min(r.reviewed_at) FILTER (
      WHERE r.status = 'APROVADA' AND r.reviewed_at IS NOT NULL
    ),
    a.criado_em
  ) AS primeira_publicacao_em,
  CASE
    WHEN min(r.reviewed_at) FILTER (
      WHERE r.status = 'APROVADA' AND r.reviewed_at IS NOT NULL
    ) IS NOT NULL THEN 'REVISAO_APROVADA'
    ELSE 'CRIADO_EM_INFERIDO'
  END AS origem
FROM legacy.anuncios a
LEFT JOIN legacy.anuncio_revisions r ON r.anuncio_id = a.id
GROUP BY a.id, a.criado_em;

CREATE TEMP TABLE dryrun_anuncio AS
SELECT
  a.id AS origem_id,
  md5('legacy:anuncio:' || a.id)::uuid AS id,
  md5('legacy:usuario:' || a.usuario_id)::uuid AS usuario_id,
  a.slug,
  a.titulo,
  a.descricao,
  CASE a.status
    WHEN 'ATIVO' THEN 'PUBLICADO'
    WHEN 'PAUSADO' THEN 'PAUSADO'
    WHEN 'REJEITADO' THEN 'REJEITADO'
    ELSE 'RASCUNHO'
  END AS status,
  CASE a.status
    WHEN 'ATIVO' THEN 'APROVADO'
    WHEN 'PAUSADO' THEN 'APROVADO'
    WHEN 'REJEITADO' THEN 'REJEITADO'
    ELSE 'NAO_ENVIADO'
  END AS status_moderacao,
  a.categoria,
  a.preco,
  p.primeira_publicacao_em AT TIME ZONE 'America/Sao_Paulo' AS publicado_em,
  p.origem AS publicacao_origem,
  a.criado_em AT TIME ZONE 'America/Sao_Paulo' AS criado_em,
  a.removido_logicamente_em AT TIME ZONE 'America/Sao_Paulo' AS removido_em,
  a.cidade_id AS cidade_origem_id,
  a.bairro_id AS bairro_origem_id
FROM legacy.anuncios a
JOIN dryrun_publicacao p ON p.anuncio_origem_id = a.id;

INSERT INTO anuncio (
  id, usuario_id, slug, titulo, descricao, status, status_moderacao,
  categoria, preco, whatsapp_normalizado, publicado_em,
  ultima_publicacao_em, criado_em, atualizado_em, removido_em,
  origem_importacao_id, versao
)
SELECT
  a.id, a.usuario_id, a.slug, a.titulo, a.descricao, a.status,
  a.status_moderacao, a.categoria, a.preco, NULL, a.publicado_em,
  a.publicado_em, a.criado_em, greatest(a.criado_em, a.publicado_em),
  a.removido_em, c.execucao_id, 0
FROM dryrun_anuncio a CROSS JOIN dryrun_context c;

INSERT INTO anuncio_status_historico (
  id, anuncio_id, status_anterior, status_novo, motivo,
  ator_usuario_id, criado_em
)
SELECT
  md5('legacy:anuncio-status:' || a.origem_id)::uuid,
  a.id,
  NULL,
  a.status,
  'IMPORTACAO_' || a.publicacao_origem,
  NULL,
  a.publicado_em
FROM dryrun_anuncio a;

INSERT INTO anuncio_localizacao (
  anuncio_id, estado_id, cidade_id, bairro_id, endereco_resumido,
  latitude, longitude, criado_em, atualizado_em
)
SELECT
  a.id,
  c.estado_id,
  c.id,
  b.id,
  NULL, NULL, NULL,
  a.criado_em,
  a.criado_em
FROM dryrun_anuncio a
JOIN dryrun_cidade c ON c.origem_id = a.cidade_origem_id
LEFT JOIN dryrun_bairro b ON b.origem_id = a.bairro_origem_id;

INSERT INTO anuncio_servicos (anuncio_id, servico, criado_em)
SELECT DISTINCT
  md5('legacy:anuncio:' || s.anuncio_id)::uuid,
  s.servico,
  c.snapshot_at
FROM legacy.anuncio_servicos s
CROSS JOIN dryrun_context c
WHERE s.servico IN (
  'ANAL', 'ATRIZ_PORNO', 'FETICHES', 'MASSAGEM_TANTRICA', 'ATIVO',
  'BDSM', 'JOGOS_DE_INTERPRETACAO', 'ORAL', 'ATOR_PORNO',
  'EJACULACAO_CORPORAL', 'MASSAGEM_EROTICA', 'PASSIVO', 'NAMORADAS',
  'TRIO', 'VIDEOCHAMADA'
);

INSERT INTO anuncio_local_atendimento (anuncio_id, local_atendimento, criado_em)
SELECT DISTINCT
  md5('legacy:anuncio:' || l.anuncio_id)::uuid,
  l.local_atendimento,
  c.snapshot_at
FROM legacy.anuncio_local_atendimento l
CROSS JOIN dryrun_context c
WHERE l.local_atendimento IN ('A_COMBINAR', 'HOTEL_MOTEL', 'MEU_LOCAL');

INSERT INTO stg_anuncio (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:anuncio:' || a.origem_id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'anuncios',
  a.origem_id::text,
  jsonb_build_object(
    'slug', a.slug,
    'status', a.status,
    'primeiraPublicacaoOrigem', a.publicacao_origem
  ),
  CASE WHEN a.publicacao_origem = 'CRIADO_EM_INFERIDO' THEN 'PENDENTE_REVISAO' ELSE 'PROCESSADO' END,
  CASE WHEN a.publicacao_origem = 'CRIADO_EM_INFERIDO' THEN 'PRIMEIRA_PUBLICACAO_INFERIDA' END,
  a.id,
  c.snapshot_at,
  c.snapshot_at
FROM dryrun_anuncio a CROSS JOIN dryrun_context c;

INSERT INTO importacao_mapeamento (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
)
SELECT
  md5('map:anuncio:' || a.origem_id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'anuncios',
  a.origem_id::text,
  'ANUNCIO',
  a.id,
  'MAPEADO',
  c.snapshot_at,
  c.snapshot_at
FROM dryrun_anuncio a CROSS JOIN dryrun_context c;

-- Midia: a classificacao do anuncio e apenas uma das evidencias. A promocao
-- exige foto real do anuncio retornada pela API publica anonima sem age gate,
-- binario valido e confirmacao no R2 V3. Assets institucionais e previews
-- protegidos permanecem em staging; nenhuma referencia restrita e copiada.
CREATE TEMP TABLE dryrun_classificacao_publica AS
WITH historico_ativo AS (
  SELECT
    anuncio_id,
    count(DISTINCT classification) AS classificacoes_distintas,
    min(classification) AS classificacao
  FROM legacy.content_classifications
  WHERE active
  GROUP BY anuncio_id
)
SELECT a.id AS anuncio_id
FROM legacy.anuncios a
LEFT JOIN historico_ativo h ON h.anuncio_id = a.id
WHERE a.status = 'ATIVO'
  AND a.removido_logicamente_em IS NULL
  AND a.content_classification IN ('SAFE_PUBLIC', 'ADULT_NON_EXPLICIT')
  AND coalesce(h.classificacoes_distintas, 1) = 1
  AND (h.classificacao IS NULL OR h.classificacao = a.content_classification);

CREATE TEMP TABLE dryrun_midia_publica AS
WITH referencias AS (
  SELECT
    f.anuncio_id,
    f.url_foto,
    md5(f.url_foto) AS reference_hash,
    a.criado_em AS created_at,
    r.object_key AS copied_object_key,
    r.sha256,
    r.tamanho_bytes,
    r.mime_type,
    r.largura,
    r.altura,
    r.status AS migracao_status,
    c.r2_public_media_prefix,
    c.r2_preserved_public_prefix,
    CASE
      WHEN left(f.url_foto, length(c.r2_preserved_public_base_url) + 1)
          = c.r2_preserved_public_base_url || '/'
      THEN substring(f.url_foto FROM length(c.r2_preserved_public_base_url) + 2)
    END AS source_object_key
  FROM legacy.anuncio_fotos f
  JOIN legacy.anuncios a ON a.id = f.anuncio_id
  JOIN dryrun_classificacao_publica cp ON cp.anuncio_id = f.anuncio_id
  JOIN dryrun_r2_public_media r
    ON r.anuncio_origem_id = f.anuncio_id
   AND r.reference_hash = md5(f.url_foto)
  CROSS JOIN dryrun_context c
), dedup AS (
  SELECT DISTINCT ON (f.anuncio_id, f.url_foto)
    f.anuncio_id,
    f.reference_hash,
    f.created_at,
    f.copied_object_key,
    f.source_object_key,
    f.r2_public_media_prefix,
    f.sha256,
    f.tamanho_bytes,
    f.mime_type,
    f.largura,
    f.altura,
    f.migracao_status
  FROM referencias f
  WHERE f.url_foto !~* '(logo|placeholder|sem[-_]?foto|default|favicon|2151117281)'
    AND f.migracao_status IN ('MIGRADA', 'PRESERVADA')
    AND f.tamanho_bytes > 0
    AND f.mime_type IN ('image/jpeg', 'image/png', 'image/webp')
    AND f.largura > 0
    AND f.altura > 0
    AND f.copied_object_key LIKE f.r2_public_media_prefix || 'importacao/sha256/%'
    AND substring(f.copied_object_key FROM length(f.r2_public_media_prefix) + 1)
        ~ '^importacao/sha256/[0-9a-f]{2}/[0-9a-f]{64}\.(jpg|jpeg|png|webp)$'
    AND f.sha256 ~ '^[0-9a-f]{64}$'
    AND f.copied_object_key LIKE f.r2_public_media_prefix || 'importacao/sha256/'
        || left(f.sha256, 2) || '/' || f.sha256 || '.%'
    AND f.source_object_key LIKE f.r2_preserved_public_prefix || '%'
    AND substring(f.source_object_key FROM length(f.r2_preserved_public_prefix) + 1)
        ~ '^[0-9a-f]{32}\.(jpg|jpeg|png|webp)$'
  ORDER BY f.anuncio_id, f.url_foto
), typed AS (
  SELECT
    d.*,
    'FOTO'::text AS tipo,
    row_number() OVER (
      PARTITION BY d.anuncio_id
      ORDER BY d.reference_hash
    ) AS tipo_ordem
  FROM dedup d
), finalized AS (
  SELECT
    t.*,
    CASE WHEN t.tipo = 'FOTO' AND t.tipo_ordem = 1 THEN 'CAPA' ELSE 'GALERIA' END AS finalidade
  FROM typed t
)
  SELECT
    f.*,
    row_number() OVER (
      PARTITION BY f.anuncio_id, f.finalidade
      ORDER BY f.reference_hash
    ) - 1 AS ordem_final
FROM finalized f;

INSERT INTO arquivo_midia (
  id, storage_provider, bucket, chave_objeto, nome_original, mime_type,
  tamanho_bytes, largura, altura, duracao_ms, sha256, etag,
  status_arquivo, criado_em
)
SELECT DISTINCT ON (m.copied_object_key)
  md5('r2:arquivo-publico-destino:' || c.r2_public_media_bucket || ':'
      || m.copied_object_key)::uuid,
  'R2',
  c.r2_public_media_bucket,
  m.copied_object_key,
  NULL,
  m.mime_type,
  m.tamanho_bytes,
  m.largura, m.altura, NULL, m.sha256, NULL,
  'VALIDADO',
  m.created_at AT TIME ZONE 'America/Sao_Paulo'
FROM dryrun_midia_publica m CROSS JOIN dryrun_context c
ORDER BY m.copied_object_key, m.reference_hash;

INSERT INTO anuncio_midia (
  id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
  criado_em, atualizado_em, visibilidade_midia
)
SELECT
  md5('legacy:anuncio-midia:foto:' || m.anuncio_id || ':' || m.reference_hash)::uuid,
  md5('legacy:anuncio:' || m.anuncio_id)::uuid,
  md5('r2:arquivo-publico-destino:' || c.r2_public_media_bucket || ':'
      || m.copied_object_key)::uuid,
  m.tipo,
  m.finalidade,
  m.ordem_final::integer,
  'PUBLICAVEL',
  m.created_at AT TIME ZONE 'America/Sao_Paulo',
  m.created_at AT TIME ZONE 'America/Sao_Paulo',
  'LIVRE'
FROM dryrun_midia_publica m CROSS JOIN dryrun_context c;

INSERT INTO stg_midia (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:asset:' || p.id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'protected_media_assets',
  p.id::text,
  jsonb_build_object(
    'storageMode', p.storage_mode,
    'ativo', p.active,
    'referenciaHash', md5(coalesce(p.original_storage_ref, p.preview_public_url, '')),
    'classificacaoPublicaComprovada', cp.anuncio_id IS NOT NULL
  ),
  'PENDENTE_REVISAO',
  CASE
    WHEN p.storage_mode = 'PRIVATE_R2' THEN 'MIDIA_PRIVADA_SEM_VERIFICACAO_R2'
    WHEN NOT p.active THEN 'MIDIA_INATIVA_PRESERVADA'
    WHEN p.media_type = 'VIDEO' THEN 'VIDEO_EXIGE_MIGRACAO_PRIVADA_R2'
    WHEN coalesce(p.preview_public_url, '') ~* '(logo|placeholder|sem[-_]?foto|default|favicon|2151117281)'
      THEN 'MIDIA_PLACEHOLDER_INSTITUCIONAL_QUARENTENA'
    WHEN cp.anuncio_id IS NULL THEN 'MIDIA_NAO_LIVRE_QUARENTENA'
    ELSE 'MIDIA_PROTEGIDA_SEM_EVIDENCIA_PUBLICA_ANONIMA'
  END,
  NULL,
  c.snapshot_at,
  NULL
FROM legacy.protected_media_assets p
CROSS JOIN dryrun_context c
LEFT JOIN dryrun_classificacao_publica cp ON cp.anuncio_id = p.anuncio_id
;

INSERT INTO stg_midia (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo,
  criado_em, processado_em
)
SELECT
  md5('stg:foto:' || f.anuncio_id || ':' || md5(f.url_foto) || ':' || f.ocorrencia)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'anuncio_fotos',
  f.anuncio_id || ':' || md5(f.url_foto) || ':' || f.ocorrencia,
  jsonb_build_object(
    'anuncioId', f.anuncio_id,
    'referenciaHash', md5(f.url_foto),
    'ocorrencia', f.ocorrencia
  ),
  CASE WHEN m.reference_hash IS NOT NULL
    THEN 'PROCESSADO' ELSE 'PENDENTE_REVISAO' END,
  CASE
    WHEN f.url_foto ~* '(logo|placeholder|sem[-_]?foto|default|favicon|2151117281)'
      THEN 'MIDIA_PLACEHOLDER_INSTITUCIONAL_QUARENTENA'
    WHEN r.status = 'BLOQUEADA' THEN 'MIDIA_PUBLICA_R2_CHECKSUM_DIVERGENTE'
    WHEN r.status = 'QUARENTENA' THEN 'MIDIA_PUBLICA_R2_QUARENTENA'
    WHEN r.reference_hash IS NULL THEN 'MIDIA_SEM_EVIDENCIA_PUBLICA_ANONIMA'
    WHEN m.reference_hash IS NULL THEN 'MIDIA_URL_PUBLICA_INCOMPATIVEL'
  END,
  c.snapshot_at,
  CASE WHEN m.reference_hash IS NOT NULL THEN c.snapshot_at END
FROM (
  SELECT
    x.*,
    row_number() OVER (
      PARTITION BY x.anuncio_id, x.url_foto
      ORDER BY x.anuncio_id, x.url_foto
    ) AS ocorrencia
  FROM legacy.anuncio_fotos x
) f
CROSS JOIN dryrun_context c
LEFT JOIN dryrun_r2_public_media r
  ON r.anuncio_origem_id = f.anuncio_id
 AND r.reference_hash = md5(f.url_foto)
LEFT JOIN dryrun_midia_publica m
  ON m.anuncio_id = f.anuncio_id
 AND m.reference_hash = md5(f.url_foto);

INSERT INTO stg_midia (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo,
  criado_em, processado_em
)
SELECT
  md5('stg:video:' || v.anuncio_id || ':' || md5(v.url_video))::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'anuncio_videos',
  v.anuncio_id || ':' || md5(v.url_video),
  jsonb_build_object('anuncioId', v.anuncio_id, 'referenciaHash', md5(v.url_video)),
  'PENDENTE_REVISAO',
  'VIDEO_EXIGE_MIGRACAO_PRIVADA_R2',
  c.snapshot_at,
  NULL
FROM (
  SELECT DISTINCT anuncio_id, url_video FROM legacy.anuncio_videos
) v CROSS JOIN dryrun_context c;

-- Busca e SEO recebem somente a evidencia fisicamente migrada para o R2 V3.
-- A indexacao runtime continua dependente da base publica HML configurada.
UPDATE documento_busca_anuncio d
SET tem_midia_valida = EXISTS (
      SELECT 1 FROM anuncio_midia am
      WHERE am.anuncio_id = d.anuncio_id
        AND am.status = 'PUBLICAVEL'
        AND am.tipo = 'FOTO'
        AND am.visibilidade_midia = 'LIVRE'
    ),
    atualizado_em = c.snapshot_at
FROM dryrun_context c;

INSERT INTO documento_busca_anuncio (
  anuncio_id, texto_busca, estado_id, cidade_id, bairro_id, categoria,
  preco, status_publicacao, tem_midia_valida, beneficios_ranking_json,
  ranking_base, atualizado_em
)
SELECT
  a.id,
  concat_ws(' ', a.titulo, a.descricao, ci.nome, ba.nome),
  e.id,
  ci.id,
  ba.id,
  a.categoria,
  a.preco,
  CASE WHEN a.status = 'PUBLICADO' THEN 'PUBLICAVEL' ELSE 'NAO_PUBLICAVEL' END,
  EXISTS (
    SELECT 1 FROM anuncio_midia am
    WHERE am.anuncio_id = a.id
      AND am.status = 'PUBLICAVEL'
      AND am.tipo = 'FOTO'
      AND am.visibilidade_midia = 'LIVRE'
  ),
  '{}'::jsonb,
  0,
  c.snapshot_at
FROM dryrun_anuncio a
JOIN anuncio_localizacao al ON al.anuncio_id = a.id
JOIN estado e ON e.id = al.estado_id
JOIN cidade ci ON ci.id = al.cidade_id
LEFT JOIN bairro ba ON ba.id = al.bairro_id
CROSS JOIN dryrun_context c;

CREATE TEMP TABLE dryrun_seo_anuncio AS
SELECT
  a.id,
  a.origem_id,
  a.slug,
  a.status = 'PUBLICADO'
    AND length(trim(a.titulo)) >= 8
    AND n.titulo_normalizado NOT IN (
      'acompanhante', 'acompanhante 1', 'acompanhante 2',
      'anuncio', 'anuncio 1', 'perfil', 'teste',
      'nova na cidade', 'novinha chegando na cidade'
    )
    AND replace(n.slug_normalizado, ' ', '-') NOT IN (
      'acompanhante-1', 'acompanhante-2', 'sem-acompanhante',
      'nova-na-cidade', 'novinha-chegando-na-cidade', 'teste'
    )
    AND n.titulo_normalizado !~ '^(acompanhante|anuncio|perfil|teste)\s*[0-9]*$'
    AND length(regexp_replace(coalesce(a.descricao, ''), '\s+', ' ', 'g')) >= 120
    AND (
      SELECT count(*) FROM anuncio_midia am
      WHERE am.anuncio_id = a.id
        AND am.tipo = 'FOTO'
        AND am.status = 'PUBLICAVEL'
        AND am.visibilidade_midia = 'LIVRE'
    ) >= 1 AS indexavel_por_evidencia
FROM dryrun_anuncio a
CROSS JOIN LATERAL (
  SELECT
    trim(regexp_replace(
      regexp_replace(
        translate(lower(coalesce(a.titulo, '')),
          'áàâãäéèêëíìîïóòôõöúùûüç',
          'aaaaaeeeeiiiiooooouuuuc'),
        '[^a-z0-9]+', ' ', 'g'),
      '\s+', ' ', 'g')) AS titulo_normalizado,
    trim(regexp_replace(
      regexp_replace(
        translate(lower(coalesce(a.slug, '')),
          'áàâãäéèêëíìîïóòôõöúùûüç',
          'aaaaaeeeeiiiiooooouuuuc'),
        '[^a-z0-9]+', ' ', 'g'),
      '\s+', ' ', 'g')) AS slug_normalizado
) n;

INSERT INTO seo_url (
  id, caminho_publico, canonical_path, tipo, entidade_tipo, entidade_id,
  status_esperado, indexavel, incluir_sitemap, qualidade_status,
  ultima_validacao_em, motivo_noindex, criado_em, atualizado_em, versao
)
SELECT
  md5('legacy:seo:anuncio:' || s.origem_id)::uuid,
  '/anuncios/' || s.slug,
  '/anuncios/' || s.slug,
  'ANUNCIO',
  'ANUNCIO',
  s.id,
  CASE WHEN s.indexavel_por_evidencia THEN 'OK_200' ELSE 'NOINDEX' END,
  s.indexavel_por_evidencia,
  s.indexavel_por_evidencia,
  CASE WHEN s.indexavel_por_evidencia THEN 'APROVADO' ELSE 'INSUFICIENTE' END,
  c.snapshot_at,
  CASE WHEN s.indexavel_por_evidencia THEN NULL ELSE 'EVIDENCIA_SEO_INSUFICIENTE' END,
  c.snapshot_at,
  c.snapshot_at,
  0
FROM dryrun_seo_anuncio s CROSS JOIN dryrun_context c;

INSERT INTO stg_url (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:url:anuncio:' || s.origem_id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'anuncios',
  s.origem_id::text,
  jsonb_build_object(
    'path', '/anuncios/' || s.slug,
    'indexavelPorEvidencia', s.indexavel_por_evidencia
  ),
  CASE WHEN s.indexavel_por_evidencia THEN 'PROCESSADO' ELSE 'PENDENTE_REVISAO' END,
  CASE WHEN NOT s.indexavel_por_evidencia THEN 'SEO_EVIDENCIA_INSUFICIENTE' END,
  md5('legacy:seo:anuncio:' || s.origem_id)::uuid,
  c.snapshot_at,
  c.snapshot_at
FROM dryrun_seo_anuncio s CROSS JOIN dryrun_context c;

-- Stories pagos sao preservados em staging. Nao ha correspondencia segura entre
-- suas URLs e os assets canonicos, portanto nenhuma linha paga falsa e criada.
INSERT INTO stg_story (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo,
  criado_em, processado_em
)
SELECT
  md5('stg:story:' || s.id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'stories',
  s.id::text,
  jsonb_build_object(
    'anuncioId', s.anuncio_id,
    'usuarioExiste', u.id IS NOT NULL,
    'anuncioExiste', a.id IS NOT NULL,
    'referenciaHash', md5(coalesce(s.midia_url, ''))
  ),
  'PENDENTE_REVISAO',
  'STORY_SEM_VINCULO_MIDIA_CANONICO',
  c.snapshot_at,
  NULL
FROM legacy.stories s
CROSS JOIN dryrun_context c
LEFT JOIN legacy.usuarios u ON u.id = s.usuario_id
LEFT JOIN legacy.anuncios a ON a.id = s.anuncio_id;

-- KYC: referencias do banco sao reconciliadas com objetos privados por usuario,
-- checksum e parte documental. PDF unico e imagens frente/verso comprovadas sao
-- promovidos; duplicatas do mesmo usuario apontam para um unico documento. URLs
-- HTTP so sao aceitas quando resolvidas para o mesmo objeto privado da origem.
CREATE TEMP TABLE dryrun_kyc_referencia AS
WITH origem AS (
  SELECT
    d.usuario_id,
    d.documento_url,
    md5(coalesce(d.documento_url, '')) AS reference_hash,
    count(*) AS ocorrencias
  FROM legacy.usuario_documentos d
  GROUP BY d.usuario_id, d.documento_url
), revisao AS (
  SELECT DISTINCT ON (r.usuario_id)
    r.usuario_id,
    r.reviewed_by_user_id,
    r.reviewed_at
  FROM legacy.advertiser_verification_requests r
  ORDER BY r.usuario_id, coalesce(r.reviewed_at, r.requested_at) DESC, r.id DESC
)
SELECT
  o.usuario_id AS usuario_origem_id,
  u.id AS usuario_v3_id,
  o.reference_hash,
  coalesce(r.canonical_reference_hash, o.reference_hash) AS canonical_reference_hash,
  o.ocorrencias,
  CASE
    WHEN nullif(trim(o.documento_url), '') IS NULL THEN 'AUSENTE'
    WHEN o.documento_url ~* '^https?://' THEN 'HTTP_PRIVACIDADE_DUVIDOSA'
    WHEN o.documento_url ~* '^r2://' THEN 'R2_PRIVADA'
    ELSE 'PRIVADA_NAO_VERIFICAVEL'
  END AS classe_referencia,
  coalesce(nullif(r.object_key, ''), nullif(canonico.object_key, '')) AS object_key,
  coalesce(nullif(r.sha256, ''), nullif(canonico.sha256, '')) AS sha256,
  coalesce(nullif(r.tamanho_bytes, 0), canonico.tamanho_bytes, 0) AS tamanho_bytes,
  coalesce(nullif(r.mime_type, ''), nullif(canonico.mime_type, '')) AS mime_type,
  coalesce(nullif(r.extensao, ''), nullif(canonico.extensao, '')) AS extensao,
  coalesce(nullif(r.parte, 'NAO_DETERMINADA'), canonico.parte, r.parte) AS parte,
  coalesce(nullif(r.envio_hash, ''), canonico.envio_hash) AS envio_hash,
  r.status AS migracao_status,
  r.motivo AS migracao_motivo,
  CASE WHEN r.status = 'CONSOLIDADA' THEN canonico.status ELSE r.status END AS objeto_status,
  CASE u.kyc_status_origem
    WHEN 'APROVADO' THEN 'VALIDADO'
    WHEN 'REPROVADO' THEN 'REJEITADO'
    ELSE 'PENDENTE'
  END AS kyc_status,
  CASE
    WHEN u.kyc_status_origem IN ('APROVADO', 'REPROVADO')
      THEN coalesce(revisor.id, c.kyc_migration_actor_id)
  END AS revisor_v3_id,
  CASE
    WHEN u.kyc_status_origem IN ('APROVADO', 'REPROVADO')
      THEN coalesce(revisao.reviewed_at AT TIME ZONE 'America/Sao_Paulo', c.snapshot_at)
  END AS revisado_em
FROM origem o
LEFT JOIN dryrun_usuario u ON u.origem_id = o.usuario_id
CROSS JOIN dryrun_context c
LEFT JOIN dryrun_r2_kyc_documents r
  ON r.usuario_v3_id = u.id
 AND r.reference_hash = o.reference_hash
LEFT JOIN dryrun_r2_kyc_documents canonico
  ON canonico.usuario_v3_id = u.id
 AND canonico.reference_hash = r.canonical_reference_hash
LEFT JOIN revisao ON revisao.usuario_id = o.usuario_id
LEFT JOIN dryrun_usuario revisor ON revisor.origem_id = revisao.reviewed_by_user_id;

CREATE TEMP TABLE dryrun_kyc_promovivel AS
SELECT r.*
FROM dryrun_kyc_referencia r
CROSS JOIN dryrun_context c
WHERE r.usuario_v3_id IS NOT NULL
  AND r.reference_hash = r.canonical_reference_hash
  AND r.classe_referencia IN ('R2_PRIVADA', 'HTTP_PRIVACIDADE_DUVIDOSA')
  AND r.objeto_status IN ('MIGRADA', 'PRESERVADA')
  AND r.object_key LIKE c.r2_document_prefix || 'importacao/%/sha256/%'
  AND substring(r.object_key FROM length(c.r2_document_prefix) + 1)
      ~ '^importacao/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*/sha256/[0-9a-f]{2}/[0-9a-f]{64}\.(pdf|jpg|png)$'
  AND r.sha256 ~ '^[0-9a-f]{64}$'
  AND r.object_key LIKE c.r2_document_prefix || 'importacao/%/sha256/'
      || left(r.sha256, 2) || '/' || r.sha256 || '.%'
  AND r.tamanho_bytes BETWEEN 1 AND 12582912
  AND (
    (r.mime_type = 'application/pdf' AND r.extensao = 'pdf' AND r.parte = 'UNICO')
    OR (r.mime_type = 'image/jpeg' AND r.extensao = 'jpg' AND r.parte IN ('FRENTE', 'VERSO'))
    OR (r.mime_type = 'image/png' AND r.extensao = 'png' AND r.parte IN ('FRENTE', 'VERSO'))
  )
  AND r.envio_hash ~ '^[0-9a-f]{64}$'
  AND r.kyc_status IN ('PENDENTE', 'VALIDADO', 'REJEITADO')
  AND (
    r.kyc_status <> 'VALIDADO'
    OR (r.revisor_v3_id IS NOT NULL AND r.revisado_em IS NOT NULL)
  )
  AND length(trim(c.r2_document_bucket)) > 0;

INSERT INTO arquivo_midia (
  id, storage_provider, bucket, chave_objeto, nome_original, mime_type,
  tamanho_bytes, largura, altura, duracao_ms, sha256, etag,
  status_arquivo, criado_em
)
SELECT DISTINCT ON (k.sha256)
  md5('r2:kyc-arquivo:' || k.sha256)::uuid,
  'R2',
  c.r2_document_bucket,
  k.object_key,
  NULL,
  k.mime_type,
  k.tamanho_bytes,
  NULL, NULL, NULL, k.sha256, NULL,
  CASE k.kyc_status
    WHEN 'VALIDADO' THEN 'VALIDADO'
    WHEN 'REJEITADO' THEN 'REJEITADO'
    ELSE 'PENDENTE'
  END,
  c.snapshot_at
FROM dryrun_kyc_promovivel k
CROSS JOIN dryrun_context c
ORDER BY k.sha256, k.reference_hash
ON CONFLICT (id) DO NOTHING;

INSERT INTO documento_usuario (
  id, usuario_id, arquivo_midia_id, tipo, status, politica_retencao,
  criado_em, atualizado_em, validado_por, validado_em,
  envio_id, parte, motivo_moderacao, revisado_por, revisado_em
)
SELECT
  md5('legacy:documento-kyc:' || k.usuario_origem_id || ':' || k.canonical_reference_hash)::uuid,
  k.usuario_v3_id,
  md5('r2:kyc-arquivo:' || k.sha256)::uuid,
  'IDENTIDADE',
  k.kyc_status,
  'ENQUANTO_HOUVER_ANUNCIO',
  c.snapshot_at,
  c.snapshot_at,
  CASE WHEN k.kyc_status = 'VALIDADO' THEN k.revisor_v3_id END,
  CASE WHEN k.kyc_status = 'VALIDADO' THEN k.revisado_em END,
  md5('legacy:documento-kyc-envio:' || k.usuario_origem_id || ':' || k.envio_hash)::uuid,
  k.parte,
  NULL,
  CASE WHEN k.kyc_status IN ('VALIDADO', 'REJEITADO') THEN k.revisor_v3_id END,
  CASE WHEN k.kyc_status IN ('VALIDADO', 'REJEITADO') THEN k.revisado_em END
FROM dryrun_kyc_promovivel k
CROSS JOIN dryrun_context c
ON CONFLICT (id) DO NOTHING;

INSERT INTO stg_midia (
  id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:documento-kyc:' || k.usuario_origem_id || ':' || k.reference_hash)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'usuario_documentos',
  k.usuario_origem_id || ':' || k.reference_hash,
  k.reference_hash,
  jsonb_build_object(
    'classeReferencia', k.classe_referencia,
    'ocorrencias', k.ocorrencias,
    'resultadoR2', coalesce(k.migracao_status, 'NAO_AUDITADA'),
    'tipoValidado', k.mime_type,
    'parteComprovada', k.parte,
    'conteudoDuplicadoConsolidado', k.reference_hash <> k.canonical_reference_hash,
    'estadoImportado', k.kyc_status,
    'urlPublicaGerada', false
  ),
  CASE
    WHEN k.usuario_v3_id IS NULL THEN 'REJEITADO'
    WHEN p.reference_hash IS NOT NULL THEN 'PROCESSADO'
    ELSE 'PENDENTE_REVISAO'
  END,
  CASE
    WHEN p.reference_hash IS NOT NULL THEN NULL
    WHEN k.usuario_v3_id IS NULL THEN 'KYC_VINCULO_USUARIO_INCONSISTENTE'
    WHEN k.classe_referencia = 'AUSENTE' THEN 'KYC_REFERENCIA_AUSENTE'
    WHEN k.classe_referencia = 'PRIVADA_NAO_VERIFICAVEL' THEN 'KYC_REFERENCIA_PRIVADA_NAO_VERIFICADA'
    WHEN k.migracao_status = 'BLOQUEADA' THEN 'KYC_CHECKSUM_DIVERGENTE'
    WHEN k.migracao_motivo = 'OBJETO_AUSENTE' THEN 'KYC_ARQUIVO_INEXISTENTE'
    WHEN k.migracao_motivo = 'REFERENCIA_HTTP_SEM_OBJETO' THEN 'KYC_REFERENCIA_HTTP_SEM_OBJETO'
    WHEN k.migracao_motivo = 'DOCUMENTO_CONFLITANTE' THEN 'KYC_DOCUMENTO_CONFLITANTE'
    WHEN k.migracao_motivo = 'VINCULO_ENTRE_USUARIOS' THEN 'KYC_VINCULO_ENTRE_USUARIOS'
    WHEN k.migracao_motivo = 'PARTE_DOCUMENTAL_NAO_COMPROVADA'
      THEN 'KYC_PARTE_DOCUMENTAL_NAO_COMPROVADA'
    WHEN k.migracao_motivo = 'TIPO_OU_CONTEUDO_INVALIDO'
      THEN 'KYC_TIPO_OU_CONTEUDO_INVALIDO'
    WHEN k.migracao_motivo = 'ARQUIVO_ACIMA_LIMITE'
      THEN 'KYC_ARQUIVO_ACIMA_LIMITE'
    WHEN k.migracao_motivo = 'ARQUIVO_INEXISTENTE'
      THEN 'KYC_ARQUIVO_INEXISTENTE'
    WHEN k.migracao_motivo = 'ORIGEM_PUBLICAMENTE_ACESSIVEL'
      THEN 'KYC_ORIGEM_PUBLICAMENTE_ACESSIVEL'
    WHEN k.classe_referencia = 'HTTP_PRIVACIDADE_DUVIDOSA' THEN 'KYC_REFERENCIA_HTTP_QUARENTENA'
    WHEN k.migracao_status = 'QUARENTENA' THEN 'KYC_FALHA_INDIVIDUAL_QUARENTENA'
    ELSE 'KYC_REFERENCIA_PRIVADA_NAO_VERIFICADA'
  END,
  CASE WHEN p.reference_hash IS NOT NULL
    THEN md5('legacy:documento-kyc:' || k.usuario_origem_id || ':' || k.canonical_reference_hash)::uuid
  END,
  c.snapshot_at,
  CASE WHEN p.reference_hash IS NOT NULL THEN c.snapshot_at END
FROM dryrun_kyc_referencia k
CROSS JOIN dryrun_context c
LEFT JOIN dryrun_kyc_promovivel p
  ON p.usuario_origem_id = k.usuario_origem_id
 AND p.reference_hash = k.canonical_reference_hash
ON CONFLICT (id) DO NOTHING;

INSERT INTO importacao_mapeamento (
  id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
  entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
)
SELECT
  md5('map:documento-kyc:' || k.usuario_origem_id || ':' || k.reference_hash)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'usuario_documentos',
  k.usuario_origem_id || ':' || k.reference_hash,
  k.reference_hash,
  'DOCUMENTO_KYC',
  CASE WHEN p.reference_hash IS NOT NULL
    THEN md5('legacy:documento-kyc:' || k.usuario_origem_id || ':' || k.canonical_reference_hash)::uuid
  END,
  CASE WHEN p.reference_hash IS NOT NULL THEN 'MAPEADO' ELSE 'PENDENTE' END,
  c.snapshot_at,
  c.snapshot_at
FROM dryrun_kyc_referencia k
CROSS JOIN dryrun_context c
LEFT JOIN dryrun_kyc_promovivel p
  ON p.usuario_origem_id = k.usuario_origem_id
 AND p.reference_hash = k.canonical_reference_hash
ON CONFLICT (id) DO NOTHING;

INSERT INTO importacao_pendencia (
  id, execucao_id, codigo, severidade, status, entidade_tipo,
  id_origem, detalhe_resumido, criado_em
)
SELECT
  md5('pendencia:kyc:' || k.usuario_origem_id || ':' || k.reference_hash)::uuid,
  c.execucao_id,
  s.pendencia_codigo,
  'ALTA',
  'ABERTA',
  'DOCUMENTO_KYC',
  k.usuario_origem_id || ':' || k.reference_hash,
  'Referencia documental mantida em quarentena; nenhuma URL publica foi criada.',
  c.snapshot_at
FROM dryrun_kyc_referencia k
CROSS JOIN dryrun_context c
JOIN stg_midia s
  ON s.id = md5('stg:documento-kyc:' || k.usuario_origem_id || ':' || k.reference_hash)::uuid
WHERE s.status <> 'PROCESSADO'
ON CONFLICT (id) DO NOTHING;

INSERT INTO importacao_pendencia (
  id, execucao_id, codigo, severidade, status, entidade_tipo,
  id_origem, detalhe_resumido, criado_em
)
SELECT
  md5('pendencia:kyc-duplicada:' || k.usuario_origem_id || ':' || k.reference_hash)::uuid,
  c.execucao_id,
  'KYC_REFERENCIA_DUPLICADA',
  'MEDIA',
  'ABERTA',
  'DOCUMENTO_KYC',
  k.usuario_origem_id || ':' || k.reference_hash,
  'Referencia repetida na origem: ' || k.ocorrencias || ' ocorrencias; um unico vinculo foi considerado.',
  c.snapshot_at
FROM dryrun_kyc_referencia k
CROSS JOIN dryrun_context c
WHERE k.ocorrencias > 1
ON CONFLICT (id) DO NOTHING;

-- Creditos: o saldo operacional do snapshot e a unica fonte do saldo inicial.
-- Historicos sem cronologia confiavel permanecem integralmente no staging.
CREATE TEMP TABLE dryrun_credito_saldo AS
WITH historico AS (
  SELECT
    usuario_id,
    count(*) AS historico_quantidade,
    coalesce(sum(quantidade), 0) AS historico_total,
    count(*) FILTER (WHERE criado_em IS NULL) AS historico_sem_data
  FROM legacy.historico_creditos
  GROUP BY usuario_id
), saldos AS (
  SELECT
    s.*,
    count(*) OVER (PARTITION BY s.usuario_id) AS saldos_usuario
  FROM legacy.creditos_usuario s
)
SELECT
  s.id AS saldo_origem_id,
  s.usuario_id,
  s.saldo,
  s.saldos_usuario,
  u.id IS NOT NULL AS usuario_existe,
  coalesce(h.historico_quantidade, 0) AS historico_quantidade,
  coalesce(h.historico_total, 0) AS historico_total,
  coalesce(h.historico_sem_data, 0) AS historico_sem_data,
  s.saldo <> coalesce(h.historico_total, 0) AS divergencia_historica
FROM saldos s
LEFT JOIN legacy.usuarios u ON u.id = s.usuario_id
LEFT JOIN historico h ON h.usuario_id = s.usuario_id;

INSERT INTO stg_credito (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:credito-historico:' || h.id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'historico_creditos',
  h.id::text,
  jsonb_build_object(
    'quantidade', h.quantidade,
    'tipo', h.tipo,
    'dataConfiavel', h.criado_em IS NOT NULL,
    'versaoImportador', 'ledger-saldo-inicial-v1'
  ),
  CASE WHEN u.id IS NULL THEN 'REJEITADO' ELSE 'PENDENTE_REVISAO' END,
  CASE
    WHEN u.id IS NULL THEN 'USUARIO_ORFAO_QUARENTENA'
    WHEN h.criado_em IS NULL THEN 'MOVIMENTO_SEM_DATA_CONFIAVEL'
    ELSE 'HISTORICO_LEGADO_FORA_LEDGER'
  END,
  NULL,
  c.snapshot_at,
  NULL
FROM legacy.historico_creditos h
CROSS JOIN dryrun_context c
LEFT JOIN legacy.usuarios u ON u.id = h.usuario_id;

INSERT INTO stg_credito (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo,
  entidade_v3_id, criado_em, processado_em
)
SELECT
  md5('stg:saldo-credito:' || s.saldo_origem_id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'creditos_usuario',
  s.saldo_origem_id::text,
  jsonb_build_object(
    'saldoOperacional', s.saldo,
    'usuarioExiste', s.usuario_existe,
    'usuarioLegadoHash', md5('legacy:usuario:' || s.usuario_id),
    'historicoQuantidade', s.historico_quantidade,
    'historicoTotal', s.historico_total,
    'historicoSemData', s.historico_sem_data,
    'divergenciaHistorica', s.divergencia_historica,
    'versaoImportador', 'ledger-saldo-inicial-v1'
  ),
  CASE
    WHEN NOT s.usuario_existe THEN 'REJEITADO'
    WHEN s.saldos_usuario <> 1 THEN 'PENDENTE_REVISAO'
    WHEN s.saldo < 0 OR s.saldo > 1000000 THEN 'PENDENTE_REVISAO'
    ELSE 'PROCESSADO'
  END,
  CASE
    WHEN NOT s.usuario_existe THEN 'USUARIO_ORFAO_QUARENTENA'
    WHEN s.saldos_usuario <> 1 THEN 'SALDO_OPERACIONAL_DUPLICADO'
    WHEN s.saldo < 0 OR s.saldo > 1000000 THEN 'SALDO_OPERACIONAL_INCOMPATIVEL'
    WHEN s.saldo = 0 THEN 'SALDO_ZERO_SEM_MOVIMENTO'
  END,
  CASE
    WHEN s.usuario_existe AND s.saldos_usuario = 1 AND s.saldo BETWEEN 0 AND 1000000
    THEN md5('legacy:usuario:' || s.usuario_id)::uuid
  END,
  c.snapshot_at,
  CASE
    WHEN s.usuario_existe AND s.saldos_usuario = 1 AND s.saldo BETWEEN 0 AND 1000000
    THEN c.snapshot_at
  END
FROM dryrun_credito_saldo s
CROSS JOIN dryrun_context c;

INSERT INTO importacao_pendencia (
  id, execucao_id, codigo, severidade, status, entidade_tipo,
  id_origem, detalhe_resumido, criado_em
)
WITH historico AS (
  SELECT usuario_id, sum(quantidade) AS saldo_historico
  FROM legacy.historico_creditos
  GROUP BY usuario_id
)
SELECT
  md5('pendencia:saldo-divergente:' || s.usuario_id)::uuid,
  c.execucao_id,
  'CREDITO_SALDO_DIVERGENTE',
  'CRITICA',
  'ABERTA',
  'USUARIO',
  md5('legacy:usuario:' || s.usuario_id),
  'Saldo mutavel diverge do total de movimentos; nenhum ajuste foi criado.',
  c.snapshot_at
FROM legacy.creditos_usuario s
JOIN legacy.usuarios u ON u.id = s.usuario_id
LEFT JOIN historico h ON h.usuario_id = s.usuario_id
CROSS JOIN dryrun_context c
WHERE s.saldo <> coalesce(h.saldo_historico, 0);

\ir reconciliar-ledger-saldo-inicial.sql

-- Pagamentos ficam integralmente em staging historico e nao geram credito.
INSERT INTO stg_pagamento (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo,
  criado_em, processado_em
)
SELECT
  md5('stg:pagamento:' || p.id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'pagamentos_mp',
  p.id::text,
  jsonb_build_object(
    'provedorDeclarado', p.provider,
    'status', p.status,
    'valor', p.valor,
    'creditos', p.creditos,
    'usuarioExiste', u.id IS NOT NULL
  ),
  'PENDENTE_REVISAO',
  CASE WHEN u.id IS NULL THEN 'USUARIO_ORFAO_QUARENTENA'
       ELSE 'PAGAMENTO_HISTORICO_NAO_CONCILIADO' END,
  c.snapshot_at,
  NULL
FROM legacy.pagamentos_mp p
CROSS JOIN dryrun_context c
LEFT JOIN legacy.usuarios u ON u.id = p.usuario_id;

-- Premium: a origem e determinada pelo comportamento comprovado. Na producao,
-- a ativacao pelo usuario debita creditos e grava creditos_cobrados; linhas de
-- custo zero nao distinguem administracao, cortesia ou migracao e permanecem
-- em quarentena. Stories continuam no fluxo proprio.
CREATE TEMP TABLE dryrun_premium_classificado AS
WITH base AS (
  SELECT
    f.id,
    f.usuario_id,
    f.anuncio_id,
    f.codigo AS codigo_legado,
    CASE f.codigo
      WHEN 'OCULTAR_IDADE' THEN 'OCULTAR_IDADE'
      WHEN 'FOTOS_EXTRA_5' THEN 'FOTOS_EXTRA_5'
      WHEN 'ANUNCIO_TOPO' THEN 'ANUNCIO_TOPO'
      WHEN 'WHATSAPP_CARD' THEN 'WHATSAPP_CARD'
      WHEN 'CARROSSEL_FOTOS' THEN 'CARROSSEL_FOTOS'
      WHEN 'VIDEO_1' THEN 'VIDEO_1'
      ELSE NULL
    END AS codigo_v3,
    f.creditos_cobrados,
    f.status AS status_legado,
    f.ativado_em AT TIME ZONE 'America/Sao_Paulo' AS inicio_em,
    f.expira_em AT TIME ZONE 'America/Sao_Paulo' AS fim_em,
    u.id IS NOT NULL AS usuario_origem_existe,
    a.id IS NOT NULL AS anuncio_origem_existe,
    a.usuario_id IS NOT DISTINCT FROM f.usuario_id AS propriedade_coerente,
    fc.id IS NOT NULL AS catalogo_origem_existe,
    coalesce(fc.ativo, false) AS catalogo_origem_ativo,
    b.id AS beneficio_id,
    coalesce(b.ativo, false) AS beneficio_v3_ativo,
    vu.id AS usuario_v3_id,
    va.id AS anuncio_v3_id,
    count(*) OVER (
      PARTITION BY f.anuncio_id, f.codigo, f.ativado_em, f.expira_em
    ) AS equivalentes_no_periodo,
    c.snapshot_at
  FROM legacy.feature_ativacao f
  CROSS JOIN dryrun_context c
  LEFT JOIN legacy.usuarios u ON u.id = f.usuario_id
  LEFT JOIN legacy.anuncios a ON a.id = f.anuncio_id
  LEFT JOIN legacy.feature_catalogo fc ON fc.codigo = f.codigo
  LEFT JOIN beneficio_premium b ON b.codigo = CASE f.codigo
    WHEN 'OCULTAR_IDADE' THEN 'OCULTAR_IDADE'
    WHEN 'FOTOS_EXTRA_5' THEN 'FOTOS_EXTRA_5'
    WHEN 'ANUNCIO_TOPO' THEN 'ANUNCIO_TOPO'
    WHEN 'WHATSAPP_CARD' THEN 'WHATSAPP_CARD'
    WHEN 'CARROSSEL_FOTOS' THEN 'CARROSSEL_FOTOS'
    WHEN 'VIDEO_1' THEN 'VIDEO_1'
    ELSE NULL
  END
  LEFT JOIN usuario vu ON vu.id = md5('legacy:usuario:' || f.usuario_id)::uuid
  LEFT JOIN anuncio va ON va.id = md5('legacy:anuncio:' || f.anuncio_id)::uuid
), classificado AS (
  SELECT
    base.*,
    CASE
      WHEN codigo_legado = 'STORIES' THEN 'STORIES_FLUXO_PROPRIO'
      WHEN codigo_v3 IS NULL THEN 'BENEFICIO_SEM_MAPEAMENTO_SEGURO'
      WHEN NOT usuario_origem_existe OR usuario_v3_id IS NULL THEN 'USUARIO_ORFAO_QUARENTENA'
      WHEN NOT anuncio_origem_existe OR anuncio_v3_id IS NULL THEN 'ANUNCIO_ORFAO_QUARENTENA'
      WHEN NOT propriedade_coerente THEN 'PROPRIETARIO_DIVERGENTE_QUARENTENA'
      WHEN NOT catalogo_origem_existe OR NOT catalogo_origem_ativo THEN 'CATALOGO_ORIGEM_INATIVO_QUARENTENA'
      WHEN beneficio_id IS NULL OR NOT beneficio_v3_ativo THEN 'BENEFICIO_V3_INATIVO_QUARENTENA'
      WHEN inicio_em IS NULL OR fim_em IS NULL OR fim_em <= inicio_em THEN 'DATAS_INVALIDAS_QUARENTENA'
      WHEN status_legado = 'CANCELADO' THEN 'ATIVACAO_CANCELADA_QUARENTENA'
      WHEN status_legado <> 'ATIVO' THEN 'STATUS_LEGADO_DESCONHECIDO_QUARENTENA'
      WHEN coalesce(creditos_cobrados, 0) <= 0 THEN 'ORIGEM_NAO_COMPROVADA_QUARENTENA'
      WHEN equivalentes_no_periodo > 1 THEN 'ATIVACAO_DUPLICADA_QUARENTENA'
      WHEN EXISTS (
        SELECT 1
        FROM legacy.feature_ativacao outra
        WHERE outra.id <> base.id
          AND outra.anuncio_id = base.anuncio_id
          AND outra.codigo = base.codigo_legado
          AND outra.status <> 'CANCELADO'
          AND outra.ativado_em < (base.fim_em AT TIME ZONE 'America/Sao_Paulo')
          AND outra.expira_em > (base.inicio_em AT TIME ZONE 'America/Sao_Paulo')
      ) THEN 'ATIVACAO_SOBREPOSTA_QUARENTENA'
      ELSE NULL
    END AS pendencia_codigo
  FROM base
)
SELECT * FROM classificado;

CREATE TEMP TABLE dryrun_premium AS
SELECT
  p.*,
  md5('legacy:grupo-premium:' || p.id)::uuid AS grupo_id,
  md5('legacy:ativacao-premium:' || p.id)::uuid AS ativacao_id,
  'CREDITO'::text AS origem_v3,
  CASE
    WHEN p.fim_em <= p.snapshot_at THEN 'EXPIRADO'
    WHEN p.inicio_em > p.snapshot_at THEN 'PLANEJADO'
    ELSE 'ATIVO'
  END AS grupo_status,
  CASE
    WHEN p.fim_em <= p.snapshot_at THEN 'EXPIRADA'
    WHEN p.inicio_em > p.snapshot_at THEN 'AGENDADA'
    ELSE 'ATIVA'
  END AS ativacao_status
FROM dryrun_premium_classificado p
WHERE p.pendencia_codigo IS NULL;

INSERT INTO stg_premium (
  id, execucao_id, sistema_origem, tabela_origem, id_origem,
  payload_normalizado_json, status, pendencia_codigo, entidade_v3_id,
  criado_em, processado_em
)
SELECT
  md5('stg:premium:' || p.id)::uuid,
  c.execucao_id,
  'TOPSDOJOB_PRODUCAO',
  'feature_ativacao',
  p.id::text,
  jsonb_build_object(
    'codigoLegado', p.codigo_legado,
    'codigoV3', p.codigo_v3,
    'statusLegado', p.status_legado,
    'inicio', p.inicio_em,
    'fim', p.fim_em,
    'origemV3', CASE WHEN p.pendencia_codigo IS NULL THEN 'CREDITO' END,
    'creditosCobrados', p.creditos_cobrados,
    'usuarioV3Id', p.usuario_v3_id,
    'anuncioV3Id', p.anuncio_v3_id,
    'beneficioId', p.beneficio_id,
    'grupoId', CASE WHEN p.pendencia_codigo IS NULL THEN md5('legacy:grupo-premium:' || p.id)::uuid END,
    'ativacaoId', CASE WHEN p.pendencia_codigo IS NULL THEN md5('legacy:ativacao-premium:' || p.id)::uuid END,
    'grupoStatus', CASE
      WHEN p.pendencia_codigo IS NOT NULL THEN NULL
      WHEN p.fim_em <= p.snapshot_at THEN 'EXPIRADO'
      WHEN p.inicio_em > p.snapshot_at THEN 'PLANEJADO'
      ELSE 'ATIVO'
    END,
    'ativacaoStatus', CASE
      WHEN p.pendencia_codigo IS NOT NULL THEN NULL
      WHEN p.fim_em <= p.snapshot_at THEN 'EXPIRADA'
      WHEN p.inicio_em > p.snapshot_at THEN 'AGENDADA'
      ELSE 'ATIVA'
    END,
    'snapshotFingerprint', c.snapshot_fingerprint,
    'versaoImportador', 'premium-historico-v1'
  ),
  CASE WHEN p.pendencia_codigo IS NULL THEN 'PROCESSADO' ELSE 'PENDENTE_REVISAO' END,
  p.pendencia_codigo,
  CASE WHEN p.pendencia_codigo IS NULL THEN md5('legacy:ativacao-premium:' || p.id)::uuid END,
  c.snapshot_at,
  CASE WHEN p.pendencia_codigo IS NULL THEN c.snapshot_at END
FROM dryrun_premium_classificado p
CROSS JOIN dryrun_context c;

-- Favoritos preservados sem duplicidade.
INSERT INTO favorito_anuncio (id, usuario_id, anuncio_id, criado_em)
SELECT
  md5('legacy:favorito:' || f.usuario_id || ':' || f.anuncio_id)::uuid,
  md5('legacy:usuario:' || f.usuario_id)::uuid,
  md5('legacy:anuncio:' || f.anuncio_id)::uuid,
  c.snapshot_at
FROM (
  SELECT DISTINCT usuario_id, anuncio_id FROM legacy.usuario_favoritos
) f CROSS JOIN dryrun_context c;

-- Quarentena agregavel das referencias orfas conhecidas.
INSERT INTO importacao_pendencia (
  id, execucao_id, codigo, severidade, status, entidade_tipo,
  id_origem, detalhe_resumido, criado_em
)
SELECT
  md5('pendencia:orfao:' || origem || ':' || id_origem)::uuid,
  c.execucao_id,
  'USUARIO_ORFAO_QUARENTENA',
  'CRITICA',
  'ABERTA',
  origem,
  id_origem,
  'Referencia financeira ou de suporte excluida do operacional e preservada em staging/agregado.',
  c.snapshot_at
FROM (
  SELECT 'CREDITO_SALDO' origem, x.id::text id_origem
  FROM legacy.creditos_usuario x LEFT JOIN legacy.usuarios u ON u.id = x.usuario_id
  WHERE u.id IS NULL
  UNION ALL
  SELECT 'CREDITO_HISTORICO', x.id::text
  FROM legacy.historico_creditos x LEFT JOIN legacy.usuarios u ON u.id = x.usuario_id
  WHERE u.id IS NULL
  UNION ALL
  SELECT 'PAGAMENTO', x.id::text
  FROM legacy.pagamentos_mp x LEFT JOIN legacy.usuarios u ON u.id = x.usuario_id
  WHERE u.id IS NULL
  UNION ALL
  SELECT 'SUPORTE', x.id::text
  FROM legacy.suporte_mensagens x LEFT JOIN legacy.usuarios u ON u.id = x.enviado_por_id
  WHERE x.enviado_por_id IS NOT NULL AND u.id IS NULL
) q CROSS JOIN dryrun_context c;

UPDATE importacao_execucao e
SET status = 'CONCLUIDA_COM_PENDENCIAS',
    finalizado_em = c.snapshot_at,
    resumo_json = jsonb_build_object(
      'usuariosOrigem', (SELECT count(*) FROM legacy.usuarios),
      'anunciosOrigem', (SELECT count(*) FROM legacy.anuncios),
      'midiasOrigem', (SELECT count(*) FROM legacy.protected_media_assets),
      'favoritosOrigem', (SELECT count(*) FROM legacy.usuario_favoritos),
      'premiumOrigem', (SELECT count(*) FROM legacy.feature_ativacao),
      'pagamentosOrigem', (SELECT count(*) FROM legacy.pagamentos_mp),
      'snapshotId', c.snapshot_id,
      'snapshotFingerprint', c.snapshot_fingerprint,
      'storageDestinationFingerprint', c.storage_destination_fingerprint,
      'anunciosPublicados', (SELECT count(*) FROM anuncio WHERE status = 'PUBLICADO'),
      'primeiraPublicacaoRecuperada', (SELECT count(*) FROM dryrun_anuncio WHERE publicacao_origem = 'REVISAO_APROVADA'),
      'primeiraPublicacaoInferida', (SELECT count(*) FROM dryrun_anuncio WHERE publicacao_origem = 'CRIADO_EM_INFERIDO'),
      'midiasLivres', (SELECT count(*) FROM anuncio_midia WHERE visibilidade_midia = 'LIVRE'),
      'midiasRestritas', (SELECT count(*) FROM anuncio_midia WHERE visibilidade_midia = 'RESTRITA_18'),
      'midiasR2Candidatas', (SELECT count(*) FROM dryrun_r2_public_media),
      'midiasR2ObjetosValidos', (
        SELECT count(DISTINCT sha256)
        FROM dryrun_r2_public_media
        WHERE status IN ('MIGRADA', 'PRESERVADA')
      ),
      'midiasR2Migradas', (SELECT count(*) FROM dryrun_r2_public_media WHERE status = 'MIGRADA'),
      'midiasR2Preservadas', (SELECT count(*) FROM dryrun_r2_public_media WHERE status = 'PRESERVADA'),
      'midiasR2Bloqueadas', (SELECT count(*) FROM dryrun_r2_public_media WHERE status = 'BLOQUEADA'),
      'midiasR2Quarentena', (SELECT count(*) FROM dryrun_r2_public_media WHERE status = 'QUARENTENA'),
      'seoIndexavelPorEvidencia', (SELECT count(*) FROM dryrun_seo_anuncio WHERE indexavel_por_evidencia),
      'saldosOperacionaisValidos', (
        SELECT count(*) FROM stg_credito
        WHERE tabela_origem = 'creditos_usuario' AND status = 'PROCESSADO'
      ),
      'saldosIniciaisPromovidos', (
        SELECT count(*) FROM movimento_credito
        WHERE tipo = 'MIGRACAO_SALDO_INICIAL'
      ),
      'saldoInicialTotal', (
        SELECT coalesce(sum(quantidade), 0) FROM movimento_credito
        WHERE tipo = 'MIGRACAO_SALDO_INICIAL'
      ),
      'historicosCreditoQuarentena', (
        SELECT count(*) FROM stg_credito
        WHERE tabela_origem = 'historico_creditos' AND status <> 'PROCESSADO'
      ),
      'usuariosSaldoDivergente', (
        SELECT count(*) FROM importacao_pendencia
        WHERE codigo = 'CREDITO_SALDO_DIVERGENTE'
      ),
      'premiumMapeavel', (SELECT count(*) FROM stg_premium WHERE status = 'PROCESSADO'),
      'premiumVigente', (SELECT count(*) FROM dryrun_premium WHERE ativacao_status = 'ATIVA'),
      'premiumExpirado', (SELECT count(*) FROM dryrun_premium WHERE ativacao_status = 'EXPIRADA'),
      'premiumQuarentena', (SELECT count(*) FROM stg_premium WHERE status <> 'PROCESSADO'),
      'premiumPromovido', (
        SELECT count(*) FROM ativacao_beneficio
        WHERE idempotency_key LIKE 'import:ativacao-premium:%'
      ),
      'kycReferenciasOrigem', (SELECT coalesce(sum(ocorrencias), 0) FROM dryrun_kyc_referencia),
      'kycReferenciasUnicas', (SELECT count(*) FROM dryrun_kyc_referencia),
      'kycReferenciasHttpQuarentena', (
        SELECT coalesce(sum(ocorrencias), 0) FROM dryrun_kyc_referencia
        WHERE classe_referencia = 'HTTP_PRIVACIDADE_DUVIDOSA'
      ),
      'kycReferenciasDuplicadas', (
        SELECT coalesce(sum(ocorrencias - 1), 0) FROM dryrun_kyc_referencia
      ),
      'kycR2Migradas', (SELECT count(*) FROM dryrun_r2_kyc_documents WHERE status = 'MIGRADA'),
      'kycR2Preservadas', (SELECT count(*) FROM dryrun_r2_kyc_documents WHERE status = 'PRESERVADA'),
      'kycR2Bloqueadas', (SELECT count(*) FROM dryrun_r2_kyc_documents WHERE status = 'BLOQUEADA'),
      'kycR2Quarentena', (SELECT count(*) FROM dryrun_r2_kyc_documents WHERE status = 'QUARENTENA'),
      'kycPromovidoPendente', (SELECT count(*) FROM documento_usuario WHERE status = 'PENDENTE'),
      'kycAprovadoPreservado', (SELECT count(*) FROM documento_usuario WHERE status = 'VALIDADO'),
      'kycReprovadoPreservado', (SELECT count(*) FROM documento_usuario WHERE status = 'REJEITADO'),
      'kycAprovadoAutomaticamente', 0,
      'pagamentosPromovidos', 0
    )
FROM dryrun_context c
WHERE e.id = c.execucao_id;

\endif

COMMIT;

DROP SCHEMA legacy CASCADE;
DROP SERVER legacy_source CASCADE;
DROP EXTENSION postgres_fdw;
