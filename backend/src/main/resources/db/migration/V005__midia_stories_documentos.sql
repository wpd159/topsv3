-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Midia canonica, documentos privados e stories vinculados a anuncio_midia.

CREATE TABLE arquivo_midia (
  id uuid PRIMARY KEY,
  storage_provider text NOT NULL,
  bucket text NOT NULL,
  chave_objeto text NOT NULL,
  nome_original text,
  mime_type text NOT NULL,
  tamanho_bytes bigint NOT NULL,
  largura integer,
  altura integer,
  duracao_ms integer,
  sha256 text,
  etag text,
  status_arquivo text NOT NULL,
  classificacao_conteudo text NOT NULL DEFAULT 'SAFE_PUBLIC',
  criado_em timestamptz NOT NULL,
  CONSTRAINT arquivo_midia_tamanho_chk CHECK (tamanho_bytes > 0),
  CONSTRAINT arquivo_midia_dimensoes_chk CHECK ((largura IS NULL OR largura > 0) AND (altura IS NULL OR altura > 0)),
  CONSTRAINT arquivo_midia_duracao_chk CHECK (duracao_ms IS NULL OR duracao_ms > 0),
  CONSTRAINT arquivo_midia_status_chk CHECK (status_arquivo IN ('PENDENTE', 'VALIDADO', 'REJEITADO', 'REMOVIDO')),
  CONSTRAINT arquivo_midia_classificacao_chk CHECK (classificacao_conteudo IN ('SAFE_PUBLIC', 'SEMIEXPLICIT', 'ADULT_RESTRICTED', 'ADULT_EXPLICIT_BLOCKED')),
  CONSTRAINT arquivo_midia_sem_placeholder_chk CHECK (lower(chave_objeto) NOT LIKE '%placeholder%')
);

CREATE UNIQUE INDEX arquivo_midia_storage_chave_uk ON arquivo_midia (storage_provider, bucket, chave_objeto);
CREATE INDEX arquivo_midia_sha256_idx ON arquivo_midia (sha256) WHERE sha256 IS NOT NULL;
CREATE INDEX arquivo_midia_status_idx ON arquivo_midia (status_arquivo);

COMMENT ON TABLE arquivo_midia IS 'Fonte canonica de midia; URL publica e derivada, nao armazenada como fonte de verdade.';

CREATE TABLE anuncio_midia (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  arquivo_midia_id uuid NOT NULL REFERENCES arquivo_midia (id),
  tipo text NOT NULL,
  finalidade text NOT NULL,
  ordem integer NOT NULL,
  status text NOT NULL,
  classificacao_conteudo text NOT NULL DEFAULT 'SAFE_PUBLIC',
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT anuncio_midia_tipo_chk CHECK (tipo IN ('FOTO', 'VIDEO', 'STORY')),
  CONSTRAINT anuncio_midia_finalidade_chk CHECK (finalidade IN ('CAPA', 'GALERIA', 'STORY')),
  CONSTRAINT anuncio_midia_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT anuncio_midia_status_chk CHECK (status IN ('PENDENTE', 'PUBLICAVEL', 'REJEITADA', 'REMOVIDA')),
  CONSTRAINT anuncio_midia_classificacao_chk CHECK (classificacao_conteudo IN ('SAFE_PUBLIC', 'SEMIEXPLICIT', 'ADULT_RESTRICTED', 'ADULT_EXPLICIT_BLOCKED')),
  CONSTRAINT anuncio_midia_story_consistencia_chk CHECK (
    (tipo = 'STORY' AND finalidade = 'STORY')
    OR (tipo <> 'STORY' AND finalidade <> 'STORY')
  )
);

CREATE INDEX anuncio_midia_anuncio_idx ON anuncio_midia (anuncio_id, tipo, finalidade, ordem);
CREATE INDEX anuncio_midia_arquivo_idx ON anuncio_midia (arquivo_midia_id);
CREATE UNIQUE INDEX anuncio_midia_ordem_ativa_uk ON anuncio_midia (anuncio_id, finalidade, ordem) WHERE status <> 'REMOVIDA';

COMMENT ON TABLE anuncio_midia IS 'Vinculo canonico entre anuncio e arquivo de midia.';

CREATE TABLE documento_usuario (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  arquivo_midia_id uuid NOT NULL REFERENCES arquivo_midia (id),
  tipo text NOT NULL,
  status text NOT NULL,
  politica_retencao text NOT NULL DEFAULT 'ENQUANTO_HOUVER_ANUNCIO',
  retencao_ate timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  validado_por uuid REFERENCES usuario (id),
  validado_em timestamptz,
  removido_em timestamptz,
  expurgado_em timestamptz,
  CONSTRAINT documento_usuario_tipo_chk CHECK (tipo IN ('IDENTIDADE', 'VERIFICACAO_IDADE', 'COMPROVANTE', 'OUTRO')),
  CONSTRAINT documento_usuario_status_chk CHECK (status IN ('PENDENTE', 'EM_ANALISE', 'VALIDADO', 'REJEITADO', 'REMOVIDO', 'EXPURGADO')),
  CONSTRAINT documento_usuario_politica_retencao_chk CHECK (politica_retencao IN ('ENQUANTO_HOUVER_ANUNCIO', 'DATA_DEFINIDA', 'RETENCAO_JURIDICA', 'MANUAL')),
  CONSTRAINT documento_usuario_validacao_chk CHECK (status <> 'VALIDADO' OR (validado_por IS NOT NULL AND validado_em IS NOT NULL)),
  CONSTRAINT documento_usuario_remocao_chk CHECK (
    (status <> 'REMOVIDO' OR removido_em IS NOT NULL)
    AND (status <> 'EXPURGADO' OR expurgado_em IS NOT NULL)
  ),
  CONSTRAINT documento_usuario_retencao_manual_chk CHECK (retencao_ate IS NULL OR politica_retencao IN ('DATA_DEFINIDA', 'RETENCAO_JURIDICA', 'MANUAL'))
);

CREATE INDEX documento_usuario_usuario_idx ON documento_usuario (usuario_id, status);
CREATE INDEX documento_usuario_arquivo_idx ON documento_usuario (arquivo_midia_id);
CREATE INDEX documento_usuario_retencao_idx ON documento_usuario (politica_retencao, retencao_ate) WHERE retencao_ate IS NOT NULL;

COMMENT ON TABLE documento_usuario IS 'Documento privado de usuario; nao deve ser publicavel nem exposto em API publica.';
COMMENT ON COLUMN documento_usuario.retencao_ate IS 'Data opcional de retencao; nula quando a politica permitir manter por vinculo operacional ou juridico.';

CREATE TABLE documento_usuario_acesso (
  id uuid PRIMARY KEY,
  documento_usuario_id uuid NOT NULL REFERENCES documento_usuario (id),
  ator_usuario_id uuid REFERENCES usuario (id),
  finalidade text NOT NULL,
  resultado text NOT NULL,
  request_id text,
  ip_hash text,
  user_agent_hash text,
  acessado_em timestamptz NOT NULL,
  criado_em timestamptz NOT NULL,
  CONSTRAINT documento_usuario_acesso_finalidade_chk CHECK (finalidade IN ('VALIDACAO', 'AUDITORIA', 'SUPORTE', 'JURIDICO', 'SEGURANCA')),
  CONSTRAINT documento_usuario_acesso_resultado_chk CHECK (resultado IN ('PERMITIDO', 'NEGADO'))
);

CREATE INDEX documento_usuario_acesso_documento_idx ON documento_usuario_acesso (documento_usuario_id, acessado_em);
CREATE INDEX documento_usuario_acesso_ator_idx ON documento_usuario_acesso (ator_usuario_id, acessado_em);

COMMENT ON TABLE documento_usuario_acesso IS 'Auditoria de acesso a documento privado; nao armazena conteudo do documento.';

CREATE TABLE story_anuncio (
  id uuid PRIMARY KEY,
  anuncio_midia_id uuid NOT NULL REFERENCES anuncio_midia (id),
  status text NOT NULL,
  inicio_em timestamptz NOT NULL,
  fim_em timestamptz,
  ordem integer NOT NULL DEFAULT 0,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT story_anuncio_status_chk CHECK (status IN ('RASCUNHO', 'PENDENTE', 'PUBLICADO', 'EXPIRADO', 'REMOVIDO')),
  CONSTRAINT story_anuncio_janela_chk CHECK (fim_em IS NULL OR fim_em > inicio_em),
  CONSTRAINT story_anuncio_ordem_chk CHECK (ordem >= 0)
);

CREATE UNIQUE INDEX story_anuncio_midia_uk ON story_anuncio (anuncio_midia_id);
CREATE INDEX story_anuncio_status_janela_idx ON story_anuncio (status, inicio_em, fim_em, ordem);

COMMENT ON TABLE story_anuncio IS 'Story vinculado exclusivamente a anuncio_midia; nao possui anuncio_id nem arquivo_midia_id.';
