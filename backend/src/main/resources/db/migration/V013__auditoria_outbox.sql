-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Auditoria administrativa e outbox transacional.

CREATE TABLE auditoria_evento (
  id uuid PRIMARY KEY,
  ator_usuario_id uuid REFERENCES usuario (id),
  acao text NOT NULL,
  recurso_tipo text NOT NULL,
  recurso_id uuid,
  antes_json jsonb,
  depois_json jsonb,
  antes_hash text,
  depois_hash text,
  request_id text,
  ip_hash text,
  user_agent_hash text,
  origem text NOT NULL,
  resultado text NOT NULL,
  criado_em timestamptz NOT NULL,
  CONSTRAINT auditoria_evento_origem_chk CHECK (origem IN ('ADMIN', 'SISTEMA', 'IMPORTACAO', 'WEBHOOK', 'SUPORTE')),
  CONSTRAINT auditoria_evento_resultado_chk CHECK (resultado IN ('SUCESSO', 'NEGADO', 'ERRO', 'PENDENTE'))
);

CREATE INDEX auditoria_evento_ator_idx ON auditoria_evento (ator_usuario_id, criado_em);
CREATE INDEX auditoria_evento_recurso_idx ON auditoria_evento (recurso_tipo, recurso_id, criado_em);
CREATE INDEX auditoria_evento_acao_idx ON auditoria_evento (acao, criado_em);

COMMENT ON TABLE auditoria_evento IS 'Trilha de auditoria com snapshots sanitizados ou hashes; nunca deve armazenar segredo, documento privado ou payload financeiro integral.';
COMMENT ON COLUMN auditoria_evento.antes_json IS 'Snapshot anterior sanitizado; preferir hash quando houver risco de dado sensivel.';
COMMENT ON COLUMN auditoria_evento.depois_json IS 'Snapshot posterior sanitizado; preferir hash quando houver risco de dado sensivel.';

CREATE TABLE outbox_evento (
  id uuid PRIMARY KEY,
  aggregate_tipo text NOT NULL,
  aggregate_id uuid NOT NULL,
  tipo_evento text NOT NULL,
  payload_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  idempotency_key text,
  tentativas integer NOT NULL DEFAULT 0,
  proxima_tentativa_em timestamptz,
  processado_em timestamptz,
  erro_resumido text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT outbox_evento_status_chk CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'PROCESSADO', 'ERRO', 'CANCELADO')),
  CONSTRAINT outbox_evento_tentativas_chk CHECK (tentativas >= 0)
);

CREATE UNIQUE INDEX outbox_evento_idempotency_uk ON outbox_evento (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX outbox_evento_status_tentativa_idx ON outbox_evento (status, proxima_tentativa_em, criado_em);
CREATE INDEX outbox_evento_aggregate_idx ON outbox_evento (aggregate_tipo, aggregate_id);

COMMENT ON TABLE outbox_evento IS 'Eventos transacionais internos para processamento assincrono futuro; nao substitui fonte canonica.';
