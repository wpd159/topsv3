-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Indices de busca planejados; revisar custo antes de aplicar.

CREATE INDEX documento_busca_anuncio_fts_idx
  ON documento_busca_anuncio
  USING gin (to_tsvector('portuguese', coalesce(texto_busca, '')));

CREATE INDEX documento_busca_anuncio_texto_trgm_idx
  ON documento_busca_anuncio
  USING gin (texto_busca gin_trgm_ops);

CREATE INDEX anuncio_titulo_trgm_idx
  ON anuncio
  USING gin (titulo gin_trgm_ops);

CREATE INDEX anuncio_slug_trgm_idx
  ON anuncio
  USING gin (slug gin_trgm_ops);

CREATE INDEX cidade_nome_trgm_idx
  ON cidade
  USING gin (nome_normalizado gin_trgm_ops);

CREATE INDEX bairro_nome_trgm_idx
  ON bairro
  USING gin (nome_normalizado gin_trgm_ops);

CREATE INDEX comercial_contato_nome_trgm_idx
  ON comercial_contato
  USING gin (nome_contato gin_trgm_ops);

CREATE INDEX importacao_mapeamento_id_origem_trgm_idx
  ON importacao_mapeamento
  USING gin (id_origem gin_trgm_ops);

COMMENT ON INDEX documento_busca_anuncio_fts_idx IS 'Indice FTS inicial para busca publica; custo deve ser revisado em banco descartavel.';
COMMENT ON INDEX documento_busca_anuncio_texto_trgm_idx IS 'Indice trigram inicial para busca aproximada; depende de pg_trgm.';
