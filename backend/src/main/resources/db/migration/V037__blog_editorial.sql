-- Blog editorial canônico da V3.

CREATE TABLE blog_categoria (
  id uuid PRIMARY KEY,
  nome varchar(120) NOT NULL,
  slug varchar(120) NOT NULL,
  ordem integer NOT NULL DEFAULT 0,
  ativa boolean NOT NULL DEFAULT true,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT blog_categoria_nome_chk CHECK (nome = btrim(nome) AND char_length(nome) BETWEEN 2 AND 120),
  CONSTRAINT blog_categoria_slug_chk CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  CONSTRAINT blog_categoria_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT blog_categoria_nome_uk UNIQUE (nome),
  CONSTRAINT blog_categoria_slug_uk UNIQUE (slug)
);

CREATE INDEX blog_categoria_publica_idx
  ON blog_categoria (ativa, ordem, nome);

CREATE TABLE blog_imagem (
  id uuid PRIMARY KEY,
  criado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  tipo varchar(16) NOT NULL,
  estado varchar(16) NOT NULL,
  private_object_key text NOT NULL,
  public_object_key text,
  mime_type varchar(64) NOT NULL,
  extensao varchar(8) NOT NULL,
  sha256 varchar(64) NOT NULL,
  tamanho_bytes bigint NOT NULL,
  largura integer NOT NULL,
  altura integer NOT NULL,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT blog_imagem_tipo_chk CHECK (tipo IN ('CAPA', 'OG')),
  CONSTRAINT blog_imagem_estado_chk CHECK (estado IN ('PRIVADA', 'PUBLICA', 'REMOVIDA')),
  CONSTRAINT blog_imagem_sha_chk CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT blog_imagem_tamanho_chk CHECK (tamanho_bytes > 0),
  CONSTRAINT blog_imagem_dimensoes_chk CHECK (largura BETWEEN 1 AND 12000 AND altura BETWEEN 1 AND 12000),
  CONSTRAINT blog_imagem_publica_chk CHECK (
    (estado = 'PUBLICA' AND public_object_key IS NOT NULL)
    OR (estado <> 'PUBLICA' AND public_object_key IS NULL)
  ),
  CONSTRAINT blog_imagem_private_key_uk UNIQUE (private_object_key),
  CONSTRAINT blog_imagem_public_key_uk UNIQUE (public_object_key)
);

CREATE INDEX blog_imagem_criador_idx
  ON blog_imagem (criado_por_usuario_id, criado_em DESC);

CREATE TABLE blog_post (
  id uuid PRIMARY KEY,
  categoria_id uuid NOT NULL REFERENCES blog_categoria (id),
  titulo varchar(180) NOT NULL,
  slug varchar(180) NOT NULL,
  resumo varchar(320) NOT NULL,
  conteudo text NOT NULL,
  autor_nome varchar(120) NOT NULL,
  status varchar(16) NOT NULL,
  seo_title varchar(180) NOT NULL,
  seo_description varchar(320) NOT NULL,
  sitemap_priority numeric(2,1) NOT NULL DEFAULT 0.7,
  change_frequency varchar(16) NOT NULL DEFAULT 'weekly',
  imagem_capa_id uuid REFERENCES blog_imagem (id),
  imagem_og_id uuid REFERENCES blog_imagem (id),
  criado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  atualizado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  criado_request_id varchar(128) NOT NULL,
  publicado_em timestamptz,
  arquivado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT blog_post_titulo_chk CHECK (titulo = btrim(titulo) AND char_length(titulo) BETWEEN 3 AND 180),
  CONSTRAINT blog_post_slug_chk CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  CONSTRAINT blog_post_resumo_chk CHECK (resumo = btrim(resumo) AND char_length(resumo) <= 320),
  CONSTRAINT blog_post_conteudo_chk CHECK (conteudo = btrim(conteudo) AND char_length(conteudo) <= 200000),
  CONSTRAINT blog_post_autor_chk CHECK (autor_nome = btrim(autor_nome) AND char_length(autor_nome) BETWEEN 2 AND 120),
  CONSTRAINT blog_post_status_chk CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO')),
  CONSTRAINT blog_post_seo_title_chk CHECK (seo_title = btrim(seo_title) AND char_length(seo_title) <= 180),
  CONSTRAINT blog_post_seo_description_chk CHECK (seo_description = btrim(seo_description) AND char_length(seo_description) <= 320),
  CONSTRAINT blog_post_priority_chk CHECK (sitemap_priority BETWEEN 0.1 AND 1.0),
  CONSTRAINT blog_post_frequency_chk CHECK (change_frequency IN ('daily', 'weekly', 'monthly')),
  CONSTRAINT blog_post_publicacao_chk CHECK (
    (status = 'PUBLICADO' AND publicado_em IS NOT NULL AND arquivado_em IS NULL)
    OR (status = 'RASCUNHO' AND arquivado_em IS NULL)
    OR (status = 'ARQUIVADO' AND arquivado_em IS NOT NULL)
  ),
  CONSTRAINT blog_post_slug_uk UNIQUE (slug),
  CONSTRAINT blog_post_request_uk UNIQUE (criado_por_usuario_id, criado_request_id)
);

CREATE INDEX blog_post_admin_idx
  ON blog_post (status, atualizado_em DESC);

CREATE INDEX blog_post_publico_idx
  ON blog_post (publicado_em DESC, id)
  WHERE status = 'PUBLICADO';

CREATE INDEX blog_post_categoria_publico_idx
  ON blog_post (categoria_id, publicado_em DESC)
  WHERE status = 'PUBLICADO';

CREATE UNIQUE INDEX blog_post_capa_uk
  ON blog_post (imagem_capa_id)
  WHERE imagem_capa_id IS NOT NULL;

CREATE UNIQUE INDEX blog_post_og_uk
  ON blog_post (imagem_og_id)
  WHERE imagem_og_id IS NOT NULL;

COMMENT ON TABLE blog_post IS
  'Fonte editorial canônica do Blog V3; rascunhos e arquivados nunca integram contratos públicos.';

COMMENT ON TABLE blog_imagem IS
  'Imagem editorial sanitizada; a cópia pública somente existe enquanto vinculada a artigo publicado.';
