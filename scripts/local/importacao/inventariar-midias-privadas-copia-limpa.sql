\set ON_ERROR_STOP on

\if :{?r2_source_public_base_url}
\else
  \echo 'parametro obrigatorio ausente: r2_source_public_base_url'
  \quit 3
\endif
\if :{?r2_source_public_bucket}
\else
  \echo 'parametro obrigatorio ausente: r2_source_public_bucket'
  \quit 3
\endif
\if :{?r2_source_private_bucket}
\else
  \echo 'parametro obrigatorio ausente: r2_source_private_bucket'
  \quit 3
\endif

-- Executar exclusivamente contra source_snapshot local. Somente tabelas
-- temporarias sao criadas e a transacao termina em ROLLBACK. O arquivo
-- produzido contem referencias privadas de trabalho e nunca deve ser versionado.
BEGIN;

CREATE TEMP TABLE inventario_midia_referencia ON COMMIT DROP AS
WITH contexto AS (
  SELECT
    regexp_replace(:'r2_source_public_base_url'::text, '/+$', '')
      AS public_base_url,
    :'r2_source_public_bucket'::text AS public_bucket,
    :'r2_source_private_bucket'::text AS private_bucket
), fotos AS (
  SELECT DISTINCT anuncio_id, url_foto
  FROM anuncio_fotos
  WHERE nullif(trim(url_foto), '') IS NOT NULL
), videos AS (
  SELECT DISTINCT anuncio_id, url_video
  FROM anuncio_videos
  WHERE nullif(trim(url_video), '') IS NOT NULL
), referencias AS (
  SELECT
    f.anuncio_id AS source_ad_id,
    'anuncio_fotos'::text AS source_table,
    f.anuncio_id || ':' || encode(sha256(convert_to(f.url_foto, 'UTF8')), 'hex')
      AS source_id,
    'FOTO'::text AS media_type,
    'ORIGINAL'::text AS variant,
    f.url_foto::text AS raw_reference,
    f.url_foto::text AS canonical_locator,
    30 AS primary_priority,
    row_number() OVER (
      PARTITION BY f.anuncio_id
      ORDER BY f.url_foto
    )::integer - 1 AS source_order
  FROM fotos f

  UNION ALL

  SELECT
    v.anuncio_id,
    'anuncio_videos',
    v.anuncio_id || ':' || encode(sha256(convert_to(v.url_video, 'UTF8')), 'hex'),
    'VIDEO',
    'ORIGINAL',
    v.url_video,
    v.url_video,
    30,
    row_number() OVER (
      PARTITION BY v.anuncio_id
      ORDER BY v.url_video
    )::integer - 1
  FROM videos v

  UNION ALL

  SELECT
    p.anuncio_id,
    'protected_media_assets',
    p.id::text,
    CASE WHEN p.media_type = 'VIDEO' THEN 'VIDEO' ELSE 'FOTO' END,
    'ORIGINAL',
    p.original_storage_ref,
    p.original_storage_ref,
    10,
    greatest(coalesce(p.sort_order, 0), 0)
  FROM protected_media_assets p
  WHERE p.original_storage_ref LIKE 'r2://%'

  UNION ALL

  SELECT
    p.anuncio_id,
    'protected_media_assets',
    p.id::text,
    CASE WHEN p.media_type = 'VIDEO' THEN 'VIDEO' ELSE 'FOTO' END,
    'LEGADO',
    p.legacy_original_url,
    p.legacy_original_url,
    20,
    greatest(coalesce(p.sort_order, 0), 0)
  FROM protected_media_assets p
  CROSS JOIN contexto c
  WHERE left(p.legacy_original_url, length(c.public_base_url) + 1)
      = c.public_base_url || '/'

  UNION ALL

  SELECT
    p.anuncio_id,
    'protected_media_assets',
    p.id::text,
    CASE WHEN p.media_type = 'VIDEO' THEN 'VIDEO' ELSE 'FOTO' END,
    'PREVIEW',
    p.preview_public_url,
    CASE
      WHEN p.original_storage_ref LIKE 'r2://%'
        THEN p.original_storage_ref
      WHEN left(p.legacy_original_url, length(c.public_base_url) + 1)
          = c.public_base_url || '/'
        THEN p.legacy_original_url
      ELSE p.preview_public_url
    END,
    40,
    greatest(coalesce(p.sort_order, 0), 0)
  FROM protected_media_assets p
  CROSS JOIN contexto c
  WHERE left(p.preview_public_url, length(c.public_base_url) + 1)
      = c.public_base_url || '/'
), resolvidas AS (
  SELECT
    r.*,
    encode(sha256(convert_to(
      r.source_ad_id || '|' || r.canonical_locator,
      'UTF8'
    )), 'hex') AS logical_media_hash,
    encode(sha256(convert_to(r.raw_reference, 'UTF8')), 'hex') AS reference_hash,
    CASE
      WHEN r.raw_reference LIKE 'r2://' || c.public_bucket || '/%'
        THEN 'PUBLIC_MEDIA'
      WHEN r.raw_reference LIKE 'r2://' || c.private_bucket || '/%'
        THEN 'PRIVATE_MEDIA'
      WHEN left(r.raw_reference, length(c.public_base_url) + 1)
          = c.public_base_url || '/'
        THEN 'PUBLIC_MEDIA'
    END AS source_area,
    CASE
      WHEN r.raw_reference LIKE 'r2://' || c.public_bucket || '/%'
        THEN substring(r.raw_reference FROM length('r2://' || c.public_bucket || '/') + 1)
      WHEN r.raw_reference LIKE 'r2://' || c.private_bucket || '/%'
        THEN substring(r.raw_reference FROM length('r2://' || c.private_bucket || '/') + 1)
      WHEN left(r.raw_reference, length(c.public_base_url) + 1)
          = c.public_base_url || '/'
        THEN split_part(
          substring(r.raw_reference FROM length(c.public_base_url) + 2),
          '?',
          1
        )
    END AS source_key
  FROM referencias r
  CROSS JOIN contexto c
), ordenadas AS (
  SELECT
    r.*,
    row_number() OVER (
      PARTITION BY r.source_ad_id, r.logical_media_hash
      ORDER BY r.primary_priority, r.source_table, r.source_id, r.variant
    ) = 1 AS primary_media
  FROM resolvidas r
)
SELECT *
FROM ordenadas;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM inventario_midia_referencia
    WHERE source_area IS NULL
       OR source_key IS NULL
       OR source_key = ''
       OR source_key LIKE '/%'
       OR source_key LIKE '%..%'
       OR source_key LIKE '%\%'
       OR source_key LIKE '%?%'
       OR source_key LIKE '%#%'
  ) THEN
    RAISE EXCEPTION 'referencia de midia nao corresponde aos storages de origem configurados';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM inventario_midia_referencia
    GROUP BY source_table, source_id, variant
    HAVING count(*) <> 1
  ) THEN
    RAISE EXCEPTION 'inventario possui referencia de origem duplicada';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM inventario_midia_referencia
    GROUP BY source_ad_id, logical_media_hash
    HAVING count(*) FILTER (WHERE primary_media) <> 1
       OR count(DISTINCT media_type) <> 1
  ) THEN
    RAISE EXCEPTION 'inventario nao possui uma unica midia principal por vinculo';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM inventario_midia_referencia r
    LEFT JOIN anuncios a ON a.id = r.source_ad_id
    WHERE a.id IS NULL
  ) THEN
    RAISE EXCEPTION 'inventario possui vinculo de anuncio orfao';
  END IF;
END $$;

COPY (
  SELECT
    source_ad_id,
    logical_media_hash,
    source_table,
    source_id,
    media_type,
    variant,
    primary_media,
    reference_hash,
    source_area,
    source_key,
    source_order
  FROM inventario_midia_referencia
  ORDER BY source_ad_id, logical_media_hash, variant, source_table, source_id
) TO '/tmp/dryrun-r2-private-media-input.tsv' WITH (
  FORMAT text,
  DELIMITER E'\t',
  NULL ''
);

SELECT
  count(DISTINCT source_ad_id) AS anuncios_com_midia,
  count(DISTINCT (source_ad_id, logical_media_hash)) AS midias_logicas,
  count(*) AS referencias_transportaveis,
  count(*) FILTER (WHERE media_type = 'FOTO') AS referencias_foto,
  count(*) FILTER (WHERE media_type = 'VIDEO') AS referencias_video,
  count(*) FILTER (WHERE variant = 'PREVIEW') AS previews,
  count(*) FILTER (WHERE variant = 'LEGADO') AS objetos_legados,
  count(*) FILTER (WHERE source_area = 'PRIVATE_MEDIA') AS objetos_privados_origem,
  count(*) FILTER (WHERE source_area = 'PUBLIC_MEDIA') AS objetos_publicos_origem
FROM inventario_midia_referencia;

ROLLBACK;
