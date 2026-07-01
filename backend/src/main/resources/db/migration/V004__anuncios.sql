-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Anuncios, historico de status e documento de busca reconstruivel.

CREATE TABLE anuncio (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  slug text NOT NULL,
  titulo text NOT NULL,
  descricao text,
  status text NOT NULL,
  status_moderacao text NOT NULL,
  categoria text NOT NULL,
  classificacao_conteudo text NOT NULL DEFAULT 'LIVRE',
  preco numeric(12,2),
  whatsapp_normalizado text,
  publicado_em timestamptz,
  ultima_publicacao_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  removido_em timestamptz,
  origem_importacao_id uuid,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT anuncio_slug_formato_chk CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  CONSTRAINT anuncio_status_chk CHECK (status IN ('RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO', 'PAUSADO', 'REJEITADO', 'REMOVIDO')),
  CONSTRAINT anuncio_status_moderacao_chk CHECK (status_moderacao IN ('NAO_ENVIADO', 'PENDENTE', 'APROVADO', 'REJEITADO', 'BLOQUEADO')),
  CONSTRAINT anuncio_classificacao_conteudo_chk CHECK (classificacao_conteudo IN ('LIVRE', 'BLOQUEADO')),
  CONSTRAINT anuncio_preco_chk CHECK (preco IS NULL OR preco >= 0),
  CONSTRAINT anuncio_whatsapp_e164_chk CHECK (whatsapp_normalizado IS NULL OR whatsapp_normalizado ~ '^\+[1-9][0-9]{7,14}$'),
  CONSTRAINT anuncio_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX anuncio_slug_uk ON anuncio (slug);
CREATE INDEX anuncio_usuario_idx ON anuncio (usuario_id);
CREATE INDEX anuncio_status_categoria_idx ON anuncio (status, categoria, publicado_em);
CREATE INDEX anuncio_removido_em_idx ON anuncio (removido_em) WHERE removido_em IS NOT NULL;

COMMENT ON TABLE anuncio IS 'Fonte transacional de anuncios da V3; nao contem dados de anuncio nesta migration.';
COMMENT ON COLUMN anuncio.slug IS 'Contrato publico de /anuncios/[slug]; slug nao deve ser reutilizado mesmo apos remocao logica.';
COMMENT ON COLUMN anuncio.preco IS 'Valor monetario em numeric(12,2), nunca ponto flutuante.';

CREATE TABLE anuncio_status_historico (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  status_anterior text,
  status_novo text NOT NULL,
  motivo text,
  ator_usuario_id uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  CONSTRAINT anuncio_status_historico_status_novo_chk CHECK (status_novo IN ('RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO', 'PAUSADO', 'REJEITADO', 'REMOVIDO')),
  CONSTRAINT anuncio_status_historico_status_anterior_chk CHECK (status_anterior IS NULL OR status_anterior IN ('RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO', 'PAUSADO', 'REJEITADO', 'REMOVIDO'))
);

CREATE INDEX anuncio_status_historico_anuncio_idx ON anuncio_status_historico (anuncio_id, criado_em);

COMMENT ON TABLE anuncio_status_historico IS 'Historico append-only de mudancas de status de anuncio.';

CREATE TABLE documento_busca_anuncio (
  anuncio_id uuid PRIMARY KEY REFERENCES anuncio (id),
  texto_busca text NOT NULL,
  estado_id uuid NOT NULL REFERENCES estado (id),
  cidade_id uuid NOT NULL REFERENCES cidade (id),
  bairro_id uuid REFERENCES bairro (id),
  categoria text NOT NULL,
  preco numeric(12,2),
  status_publicacao text NOT NULL,
  tem_midia_valida boolean NOT NULL DEFAULT false,
  beneficios_ranking_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  ranking_base numeric(10,4) NOT NULL DEFAULT 0,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT documento_busca_anuncio_preco_chk CHECK (preco IS NULL OR preco >= 0),
  CONSTRAINT documento_busca_anuncio_status_chk CHECK (status_publicacao IN ('NAO_PUBLICAVEL', 'PUBLICAVEL', 'NOINDEX', 'REMOVIDO')),
  CONSTRAINT documento_busca_anuncio_ranking_base_chk CHECK (ranking_base >= 0)
);

CREATE INDEX documento_busca_anuncio_local_idx ON documento_busca_anuncio (estado_id, cidade_id, bairro_id);
CREATE INDEX documento_busca_anuncio_categoria_status_idx ON documento_busca_anuncio (categoria, status_publicacao, tem_midia_valida);
CREATE INDEX documento_busca_anuncio_ranking_idx ON documento_busca_anuncio (ranking_base DESC, atualizado_em DESC);

COMMENT ON TABLE documento_busca_anuncio IS 'Projecao reconstruivel para busca publica; substitui fallback de busca geral.';
