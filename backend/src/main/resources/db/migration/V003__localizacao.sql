-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Catalogo de localizacao e vinculo publico minimizado.

CREATE TABLE estado (
  id uuid PRIMARY KEY,
  uf char(2) NOT NULL,
  nome text NOT NULL,
  nome_normalizado text NOT NULL,
  criado_em timestamptz NOT NULL,
  CONSTRAINT estado_uf_formato_chk CHECK (uf ~ '^[A-Z]{2}$')
);

CREATE UNIQUE INDEX estado_uf_uk ON estado (uf);
CREATE UNIQUE INDEX estado_nome_normalizado_uk ON estado (nome_normalizado);

COMMENT ON TABLE estado IS 'Catalogo de unidades federativas para rotas publicas e filtros.';

CREATE TABLE cidade (
  id uuid PRIMARY KEY,
  estado_id uuid NOT NULL REFERENCES estado (id),
  nome text NOT NULL,
  nome_normalizado text NOT NULL,
  slug text NOT NULL,
  criado_em timestamptz NOT NULL,
  CONSTRAINT cidade_nome_normalizado_lower_chk CHECK (nome_normalizado = lower(nome_normalizado)),
  CONSTRAINT cidade_slug_formato_chk CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE UNIQUE INDEX cidade_estado_nome_uk ON cidade (estado_id, nome_normalizado);
CREATE UNIQUE INDEX cidade_estado_slug_uk ON cidade (estado_id, slug);
CREATE UNIQUE INDEX cidade_id_estado_uk ON cidade (id, estado_id);
CREATE INDEX cidade_estado_idx ON cidade (estado_id);

COMMENT ON TABLE cidade IS 'Catalogo de cidades para /acompanhantes/[uf]/[cidade].';

CREATE TABLE bairro (
  id uuid PRIMARY KEY,
  cidade_id uuid NOT NULL REFERENCES cidade (id),
  nome text NOT NULL,
  nome_normalizado text NOT NULL,
  slug text NOT NULL,
  criado_em timestamptz NOT NULL,
  CONSTRAINT bairro_nome_normalizado_lower_chk CHECK (nome_normalizado = lower(nome_normalizado)),
  CONSTRAINT bairro_slug_formato_chk CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE UNIQUE INDEX bairro_cidade_nome_uk ON bairro (cidade_id, nome_normalizado);
CREATE UNIQUE INDEX bairro_cidade_slug_uk ON bairro (cidade_id, slug);
CREATE UNIQUE INDEX bairro_id_cidade_uk ON bairro (id, cidade_id);
CREATE INDEX bairro_cidade_idx ON bairro (cidade_id);

COMMENT ON TABLE bairro IS 'Catalogo de bairros para /acompanhantes/[uf]/[cidade]/[bairro].';

CREATE TABLE anuncio_localizacao (
  anuncio_id uuid PRIMARY KEY,
  estado_id uuid NOT NULL REFERENCES estado (id),
  cidade_id uuid NOT NULL REFERENCES cidade (id),
  bairro_id uuid REFERENCES bairro (id),
  endereco_resumido text,
  latitude numeric(9,6),
  longitude numeric(9,6),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT anuncio_localizacao_latitude_chk CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
  CONSTRAINT anuncio_localizacao_longitude_chk CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

CREATE INDEX anuncio_localizacao_estado_cidade_bairro_idx ON anuncio_localizacao (estado_id, cidade_id, bairro_id);
CREATE INDEX anuncio_localizacao_cidade_idx ON anuncio_localizacao (cidade_id);

COMMENT ON TABLE anuncio_localizacao IS 'Localizacao publica minimizada de anuncio; FK para anuncio entra em V017.';
