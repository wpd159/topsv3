-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Base de usuarios, papeis e sessoes opacas. Nao cria login funcional.

CREATE TABLE usuario (
  id uuid PRIMARY KEY,
  nome text,
  email_normalizado text,
  telefone_normalizado text,
  status text NOT NULL,
  tipo_conta text NOT NULL,
  email_verificado_em timestamptz,
  telefone_verificado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  desativado_em timestamptz,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT usuario_status_chk CHECK (status IN ('ATIVO', 'PENDENTE', 'SUSPENSO', 'DESATIVADO', 'IMPORTADO')),
  CONSTRAINT usuario_tipo_conta_chk CHECK (tipo_conta IN ('ANUNCIANTE', 'STAFF', 'SISTEMA')),
  CONSTRAINT usuario_email_lower_chk CHECK (email_normalizado IS NULL OR email_normalizado = lower(email_normalizado)),
  CONSTRAINT usuario_telefone_e164_chk CHECK (telefone_normalizado IS NULL OR telefone_normalizado ~ '^\+[1-9][0-9]{7,14}$'),
  CONSTRAINT usuario_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX usuario_email_normalizado_uk ON usuario (email_normalizado) WHERE email_normalizado IS NOT NULL;
CREATE UNIQUE INDEX usuario_telefone_normalizado_uk ON usuario (telefone_normalizado) WHERE telefone_normalizado IS NOT NULL;
CREATE INDEX usuario_status_idx ON usuario (status);
CREATE INDEX usuario_criado_em_idx ON usuario (criado_em);

COMMENT ON TABLE usuario IS 'Fonte canonica de usuarios e anunciantes da V3.';
COMMENT ON COLUMN usuario.id IS 'UUID v7 gerado pela aplicacao; sem default aleatorio no banco.';
COMMENT ON COLUMN usuario.email_normalizado IS 'E-mail em lowercase para unicidade e busca segura.';
COMMENT ON COLUMN usuario.telefone_normalizado IS 'Telefone em formato E.164 quando armazenado.';

CREATE TABLE credencial_usuario (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  senha_hash text NOT NULL,
  algoritmo text NOT NULL,
  alterada_em timestamptz NOT NULL,
  precisa_redefinir boolean NOT NULL DEFAULT false,
  criado_em timestamptz NOT NULL,
  CONSTRAINT credencial_usuario_algoritmo_chk CHECK (length(algoritmo) > 0)
);

CREATE UNIQUE INDEX credencial_usuario_usuario_uk ON credencial_usuario (usuario_id);

COMMENT ON TABLE credencial_usuario IS 'Credenciais persistem apenas hash de credencial, nunca valor bruto.';

CREATE TABLE papel_usuario (
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  papel text NOT NULL,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  PRIMARY KEY (usuario_id, papel),
  CONSTRAINT papel_usuario_papel_chk CHECK (papel IN ('ADMIN', 'MODERADOR', 'COMERCIAL', 'USUARIO'))
);

COMMENT ON TABLE papel_usuario IS 'Vinculo entre usuario e papel futuro de autorizacao.';

CREATE TABLE permissao (
  id uuid PRIMARY KEY,
  codigo text NOT NULL,
  descricao text NOT NULL,
  criado_em timestamptz NOT NULL,
  CONSTRAINT permissao_codigo_formato_chk CHECK (codigo ~ '^[A-Z0-9_]+$')
);

CREATE UNIQUE INDEX permissao_codigo_uk ON permissao (codigo);

COMMENT ON TABLE permissao IS 'Catalogo futuro de permissoes granulares, sem seed nesta migration.';

CREATE TABLE papel_permissao (
  papel text NOT NULL,
  permissao_id uuid NOT NULL REFERENCES permissao (id),
  criado_em timestamptz NOT NULL,
  PRIMARY KEY (papel, permissao_id),
  CONSTRAINT papel_permissao_papel_chk CHECK (papel IN ('ADMIN', 'MODERADOR', 'COMERCIAL', 'USUARIO'))
);

COMMENT ON TABLE papel_permissao IS 'Permissoes por papel para RBAC futuro; sem autenticacao funcional nesta fase.';

CREATE TABLE sessao_usuario (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  token_sessao_hash text NOT NULL,
  dispositivo_hash text,
  ip_criacao_hash text,
  user_agent_hash text,
  criada_em timestamptz NOT NULL,
  ultimo_uso_em timestamptz,
  expira_inatividade_em timestamptz NOT NULL,
  expira_absoluta_em timestamptz NOT NULL,
  revogada_em timestamptz,
  motivo_revogacao text,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT sessao_usuario_expira_inatividade_chk CHECK (expira_inatividade_em > criada_em),
  CONSTRAINT sessao_usuario_expira_absoluta_chk CHECK (expira_absoluta_em > criada_em),
  CONSTRAINT sessao_usuario_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX sessao_usuario_token_hash_uk ON sessao_usuario (token_sessao_hash);
CREATE INDEX sessao_usuario_usuario_idx ON sessao_usuario (usuario_id);
CREATE INDEX sessao_usuario_expira_idx ON sessao_usuario (expira_absoluta_em, expira_inatividade_em);

COMMENT ON TABLE sessao_usuario IS 'Sessao server-side opaca; valor bruto nao e persistido.';

CREATE TABLE token_seguranca (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  tipo text NOT NULL,
  token_hash text NOT NULL,
  expira_em timestamptz NOT NULL,
  tentativas integer NOT NULL DEFAULT 0,
  consumido_em timestamptz,
  criado_em timestamptz NOT NULL,
  CONSTRAINT token_seguranca_tipo_chk CHECK (tipo IN ('CONFIRMACAO_EMAIL', 'RECUPERACAO_SENHA', 'CONVITE', 'REAUTENTICACAO')),
  CONSTRAINT token_seguranca_tentativas_chk CHECK (tentativas >= 0)
);

CREATE UNIQUE INDEX token_seguranca_hash_uk ON token_seguranca (token_hash);
CREATE INDEX token_seguranca_usuario_tipo_idx ON token_seguranca (usuario_id, tipo, expira_em);

COMMENT ON TABLE token_seguranca IS 'Credenciais temporarias de seguranca persistem apenas hash, expiracao e consumo unico.';
