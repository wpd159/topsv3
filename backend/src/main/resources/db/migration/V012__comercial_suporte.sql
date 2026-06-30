-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Pipeline comercial e suporte sem regra funcional.

CREATE TABLE comercial_status (
  id uuid PRIMARY KEY,
  codigo text NOT NULL,
  nome text NOT NULL,
  ordem integer NOT NULL,
  ativo boolean NOT NULL DEFAULT true,
  criado_em timestamptz NOT NULL,
  CONSTRAINT comercial_status_codigo_chk CHECK (codigo ~ '^[A-Z0-9_]+$'),
  CONSTRAINT comercial_status_ordem_chk CHECK (ordem >= 0)
);

CREATE UNIQUE INDEX comercial_status_codigo_uk ON comercial_status (codigo);

COMMENT ON TABLE comercial_status IS 'Catalogo administravel de status comercial; sem seed nesta migration.';

CREATE TABLE comercial_contato (
  id uuid PRIMARY KEY,
  usuario_id uuid REFERENCES usuario (id),
  anuncio_id uuid REFERENCES anuncio (id),
  status_id uuid REFERENCES comercial_status (id),
  nome_contato text,
  email_normalizado text,
  telefone_normalizado text,
  origem text NOT NULL,
  campanha_codigo text,
  responsavel_usuario_id uuid REFERENCES usuario (id),
  proxima_acao_em timestamptz,
  cortesia_concedida boolean NOT NULL DEFAULT false,
  observacao_resumida text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT comercial_contato_email_lower_chk CHECK (email_normalizado IS NULL OR email_normalizado = lower(email_normalizado)),
  CONSTRAINT comercial_contato_telefone_e164_chk CHECK (telefone_normalizado IS NULL OR telefone_normalizado ~ '^\+[1-9][0-9]{7,14}$'),
  CONSTRAINT comercial_contato_origem_chk CHECK (origem IN ('ORGANICO', 'INDICACAO', 'CAMPANHA', 'ADMIN', 'IMPORTACAO', 'OUTRO')),
  CONSTRAINT comercial_contato_versao_chk CHECK (versao >= 0)
);

CREATE INDEX comercial_contato_status_responsavel_idx ON comercial_contato (status_id, responsavel_usuario_id, atualizado_em);
CREATE INDEX comercial_contato_usuario_idx ON comercial_contato (usuario_id);
CREATE INDEX comercial_contato_anuncio_idx ON comercial_contato (anuncio_id);

COMMENT ON TABLE comercial_contato IS 'Contato comercial para aquisicao e liquidez de marketplace, sem acao funcional nesta fase.';

CREATE TABLE comercial_interacao (
  id uuid PRIMARY KEY,
  contato_id uuid NOT NULL REFERENCES comercial_contato (id),
  tipo text NOT NULL,
  resultado text,
  responsavel_usuario_id uuid REFERENCES usuario (id),
  resumo text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT comercial_interacao_tipo_chk CHECK (tipo IN ('NOTA', 'LIGACAO', 'MENSAGEM', 'EMAIL', 'REUNIAO', 'CORTESIA', 'OUTRO'))
);

CREATE INDEX comercial_interacao_contato_idx ON comercial_interacao (contato_id, criado_em);
CREATE INDEX comercial_interacao_responsavel_idx ON comercial_interacao (responsavel_usuario_id, criado_em);

COMMENT ON TABLE comercial_interacao IS 'Historico de interacoes comerciais com dados minimizados.';

CREATE TABLE ticket_suporte (
  id uuid PRIMARY KEY,
  usuario_id uuid REFERENCES usuario (id),
  anuncio_id uuid REFERENCES anuncio (id),
  pagamento_id uuid REFERENCES pagamento (id),
  revisao_anuncio_id uuid REFERENCES revisao_anuncio (id),
  assunto text NOT NULL,
  status text NOT NULL,
  prioridade text NOT NULL,
  responsavel_usuario_id uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  encerrado_em timestamptz,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT ticket_suporte_status_chk CHECK (status IN ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'ENCERRADO')),
  CONSTRAINT ticket_suporte_prioridade_chk CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
  CONSTRAINT ticket_suporte_janela_chk CHECK (encerrado_em IS NULL OR encerrado_em >= criado_em),
  CONSTRAINT ticket_suporte_versao_chk CHECK (versao >= 0)
);

CREATE INDEX ticket_suporte_status_idx ON ticket_suporte (status, prioridade, responsavel_usuario_id, atualizado_em);
CREATE INDEX ticket_suporte_usuario_idx ON ticket_suporte (usuario_id, criado_em);
CREATE INDEX ticket_suporte_anuncio_idx ON ticket_suporte (anuncio_id);

COMMENT ON TABLE ticket_suporte IS 'Ticket de suporte com vinculos opcionais para auditoria.';

CREATE TABLE mensagem_suporte (
  id uuid PRIMARY KEY,
  ticket_id uuid NOT NULL REFERENCES ticket_suporte (id),
  autor_usuario_id uuid REFERENCES usuario (id),
  origem text NOT NULL,
  corpo_resumido text NOT NULL,
  privado_staff boolean NOT NULL DEFAULT false,
  criado_em timestamptz NOT NULL,
  CONSTRAINT mensagem_suporte_origem_chk CHECK (origem IN ('USUARIO', 'STAFF', 'SISTEMA', 'IMPORTACAO'))
);

CREATE INDEX mensagem_suporte_ticket_idx ON mensagem_suporte (ticket_id, criado_em);

COMMENT ON TABLE mensagem_suporte IS 'Mensagens de suporte com conteudo minimizado para revisao futura.';
