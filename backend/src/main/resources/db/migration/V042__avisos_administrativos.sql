-- Avisos administrativos exibidos no site, apos login e no detalhe de anuncios.

CREATE TABLE aviso_administrativo (
  id uuid PRIMARY KEY,
  titulo varchar(160) NOT NULL,
  descricao text NOT NULL,
  local_exibicao varchar(24) NOT NULL,
  frequencia_exibicao varchar(16) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'RASCUNHO',
  permite_dispensar boolean NOT NULL DEFAULT true,
  ativo_de timestamptz,
  ativo_ate timestamptz,
  criado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  criado_por_nome varchar(160) NOT NULL,
  atualizado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  criado_request_id varchar(128) NOT NULL,
  publicado_em timestamptz,
  retirado_em timestamptz,
  arquivado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT aviso_administrativo_titulo_chk CHECK (
    titulo = btrim(titulo) AND char_length(titulo) BETWEEN 3 AND 160
  ),
  CONSTRAINT aviso_administrativo_descricao_chk CHECK (
    descricao = btrim(descricao) AND char_length(descricao) BETWEEN 5 AND 4000
  ),
  CONSTRAINT aviso_administrativo_local_chk CHECK (
    local_exibicao IN ('SITE', 'LOGIN_POPUP', 'ANUNCIO_RODAPE')
  ),
  CONSTRAINT aviso_administrativo_frequencia_chk CHECK (
    frequencia_exibicao IN ('SEMPRE', 'UMA_VEZ', 'DIARIO')
  ),
  CONSTRAINT aviso_administrativo_status_chk CHECK (
    status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO')
  ),
  CONSTRAINT aviso_administrativo_janela_chk CHECK (
    ativo_de IS NULL OR ativo_ate IS NULL OR ativo_ate > ativo_de
  ),
  CONSTRAINT aviso_administrativo_estado_chk CHECK (
    (status = 'RASCUNHO' AND arquivado_em IS NULL)
    OR (status = 'PUBLICADO' AND publicado_em IS NOT NULL AND arquivado_em IS NULL)
    OR (status = 'ARQUIVADO' AND arquivado_em IS NOT NULL)
  ),
  CONSTRAINT aviso_administrativo_criacao_request_uk UNIQUE (
    criado_por_usuario_id, criado_request_id
  )
);

CREATE INDEX aviso_administrativo_admin_idx
  ON aviso_administrativo (status, local_exibicao, atualizado_em DESC, id);

CREATE INDEX aviso_administrativo_publico_idx
  ON aviso_administrativo (local_exibicao, ativo_de, ativo_ate, publicado_em DESC, id)
  WHERE status = 'PUBLICADO';

COMMENT ON TABLE aviso_administrativo IS
  'Fonte unica dos avisos V3; a dispensa segue a frequencia no navegador e nao gera comunicacao externa.';
