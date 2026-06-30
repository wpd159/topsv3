-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- SEO, rotas publicas preservadas e conteudo institucional administravel.

CREATE TABLE seo_url (
  id uuid PRIMARY KEY,
  caminho_publico text NOT NULL,
  canonical_path text NOT NULL,
  tipo text NOT NULL,
  entidade_tipo text,
  entidade_id uuid,
  status_esperado text NOT NULL,
  indexavel boolean NOT NULL DEFAULT false,
  incluir_sitemap boolean NOT NULL DEFAULT false,
  qualidade_status text NOT NULL,
  ultima_validacao_em timestamptz,
  motivo_noindex text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT seo_url_caminho_chk CHECK (caminho_publico ~ '^/[a-z0-9/_\[\]-]+$' OR caminho_publico IN ('/sitemap.xml', '/robots.txt')),
  CONSTRAINT seo_url_canonical_path_chk CHECK (canonical_path ~ '^/[a-z0-9/_\[\]-]+$' OR canonical_path IN ('/sitemap.xml', '/robots.txt')),
  CONSTRAINT seo_url_rotas_alternativas_chk CHECK (caminho_publico !~ '^/(anuncio|perfil|acompanhante|ads)(/|$)'),
  CONSTRAINT seo_url_tipo_chk CHECK (tipo IN ('ANUNCIO', 'CIDADE', 'BAIRRO', 'SITEMAP', 'ROBOTS', 'INSTITUCIONAL', 'BLOG', 'OUTRO')),
  CONSTRAINT seo_url_status_esperado_chk CHECK (status_esperado IN ('OK_200', 'REDIRECT_301', 'NOINDEX', 'REMOVIDO', 'PENDENTE')),
  CONSTRAINT seo_url_qualidade_status_chk CHECK (qualidade_status IN ('PENDENTE', 'APROVADO', 'INSUFICIENTE', 'BLOQUEADO')),
  CONSTRAINT seo_url_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX seo_url_caminho_uk ON seo_url (caminho_publico);
CREATE UNIQUE INDEX seo_url_canonical_path_uk ON seo_url (canonical_path);
CREATE INDEX seo_url_sitemap_idx ON seo_url (incluir_sitemap, indexavel, qualidade_status);
CREATE INDEX seo_url_entidade_idx ON seo_url (entidade_tipo, entidade_id);

COMMENT ON TABLE seo_url IS 'Registro de URLs publicas, canonical path, sitemap e indexabilidade.';
COMMENT ON COLUMN seo_url.caminho_publico IS 'Preserva /anuncios/[slug] e paginas locais; bloqueia rotas alternativas.';

CREATE TABLE seo_metadado (
  id uuid PRIMARY KEY,
  seo_url_id uuid NOT NULL REFERENCES seo_url (id),
  titulo text NOT NULL,
  descricao text,
  robots text NOT NULL,
  og_titulo text,
  og_descricao text,
  schema_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  origem text NOT NULL,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT seo_metadado_robots_chk CHECK (robots IN ('INDEX_FOLLOW', 'NOINDEX_FOLLOW', 'NOINDEX_NOFOLLOW')),
  CONSTRAINT seo_metadado_origem_chk CHECK (origem IN ('SISTEMA', 'ADMIN', 'IMPORTACAO', 'GERADO'))
);

CREATE INDEX seo_metadado_url_idx ON seo_metadado (seo_url_id, atualizado_em);

COMMENT ON TABLE seo_metadado IS 'Metadados SEO versionaveis por URL, sem publicar conteudo final nesta fase.';

CREATE TABLE seo_redirect (
  id uuid PRIMARY KEY,
  origem_caminho text NOT NULL,
  destino_caminho text NOT NULL,
  status_code integer NOT NULL,
  ativo boolean NOT NULL DEFAULT true,
  motivo text,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT seo_redirect_origem_chk CHECK (origem_caminho ~ '^/[a-z0-9/_\[\]-]+$' OR origem_caminho ~ '^/[a-z0-9/_\[\]-]+\.[a-z0-9]+$'),
  CONSTRAINT seo_redirect_destino_chk CHECK (destino_caminho ~ '^/[a-z0-9/_\[\]-]+$' OR destino_caminho ~ '^/[a-z0-9/_\[\]-]+\.[a-z0-9]+$'),
  CONSTRAINT seo_redirect_status_chk CHECK (status_code IN (301, 302, 308)),
  CONSTRAINT seo_redirect_sem_loop_chk CHECK (origem_caminho <> destino_caminho)
);

CREATE UNIQUE INDEX seo_redirect_origem_ativo_uk ON seo_redirect (origem_caminho) WHERE ativo;
CREATE INDEX seo_redirect_destino_idx ON seo_redirect (destino_caminho);

COMMENT ON TABLE seo_redirect IS 'Mapa de redirects futuros; sem rotas alternativas de anuncio como destino canonico.';

CREATE TABLE seo_conteudo_pagina (
  id uuid PRIMARY KEY,
  seo_url_id uuid NOT NULL REFERENCES seo_url (id),
  chave text NOT NULL,
  titulo text,
  corpo_markdown text,
  status text NOT NULL,
  origem text NOT NULL,
  criado_por uuid REFERENCES usuario (id),
  aprovado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  aprovado_em timestamptz,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT seo_conteudo_pagina_chave_chk CHECK (chave ~ '^[a-z0-9_]+$'),
  CONSTRAINT seo_conteudo_pagina_status_chk CHECK (status IN ('RASCUNHO', 'REVISAO', 'APROVADO', 'PUBLICADO', 'ARQUIVADO')),
  CONSTRAINT seo_conteudo_pagina_origem_chk CHECK (origem IN ('ADMIN', 'IMPORTACAO', 'GERADO')),
  CONSTRAINT seo_conteudo_pagina_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX seo_conteudo_pagina_chave_uk ON seo_conteudo_pagina (seo_url_id, chave, versao);
CREATE UNIQUE INDEX seo_conteudo_pagina_publicado_uk ON seo_conteudo_pagina (seo_url_id, chave) WHERE status IN ('APROVADO', 'PUBLICADO');
CREATE INDEX seo_conteudo_pagina_status_idx ON seo_conteudo_pagina (status, atualizado_em);

COMMENT ON TABLE seo_conteudo_pagina IS 'Conteudo SEO/institucional administravel; apenas uma versao aprovada/publicada por URL e chave.';
