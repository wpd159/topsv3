-- Fonte canonica das categorias publicas da Home e nascimento confiavel do anunciante.

ALTER TABLE usuario
  ADD COLUMN data_nascimento date;

COMMENT ON COLUMN usuario.data_nascimento IS
  'Data privada usada pelo backend para calcular idade publica; nunca exposta em DTO publico.';

CREATE TABLE categoria_home (
  id uuid PRIMARY KEY,
  categoria_enum text NOT NULL,
  nome text NOT NULL,
  descricao text NOT NULL,
  destino text NOT NULL,
  imagem_publica_url text NOT NULL,
  ordem integer NOT NULL,
  ativo boolean NOT NULL DEFAULT true,
  CONSTRAINT categoria_home_categoria_enum_chk CHECK (categoria_enum ~ '^[A-Z0-9_]+$'),
  CONSTRAINT categoria_home_nome_chk CHECK (length(trim(nome)) BETWEEN 1 AND 120),
  CONSTRAINT categoria_home_descricao_chk CHECK (length(trim(descricao)) BETWEEN 1 AND 280),
  CONSTRAINT categoria_home_destino_chk CHECK (destino ~ '^/[A-Za-z0-9/?=&_-]+$'),
  CONSTRAINT categoria_home_imagem_publica_chk CHECK (
    imagem_publica_url ~ '^/[a-zA-Z0-9/._-]+$'
    AND imagem_publica_url NOT LIKE '//%'
  ),
  CONSTRAINT categoria_home_ordem_chk CHECK (ordem >= 0)
);

CREATE UNIQUE INDEX categoria_home_categoria_enum_uk ON categoria_home (categoria_enum);
CREATE UNIQUE INDEX categoria_home_ordem_uk ON categoria_home (ordem);
CREATE INDEX categoria_home_ativas_ordem_idx ON categoria_home (ativo, ordem, id);

COMMENT ON TABLE categoria_home IS
  'Catalogo canonico das categorias exibidas publicamente na Home.';
COMMENT ON COLUMN categoria_home.imagem_publica_url IS
  'Somente caminho publico relativo; nunca bucket, storage key ou URL privada.';
