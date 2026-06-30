-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Base financeira e Pix modelado, sem integracao externa.

CREATE TABLE plano_credito (
  id uuid PRIMARY KEY,
  codigo text NOT NULL,
  nome text NOT NULL,
  quantidade_creditos integer NOT NULL,
  valor numeric(12,2) NOT NULL,
  moeda char(3) NOT NULL DEFAULT 'BRL',
  ativo boolean NOT NULL DEFAULT true,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT plano_credito_codigo_chk CHECK (codigo ~ '^[A-Z0-9_]+$'),
  CONSTRAINT plano_credito_quantidade_chk CHECK (quantidade_creditos > 0),
  CONSTRAINT plano_credito_valor_chk CHECK (valor >= 0),
  CONSTRAINT plano_credito_moeda_chk CHECK (moeda ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX plano_credito_codigo_uk ON plano_credito (codigo);

COMMENT ON TABLE plano_credito IS 'Planos de creditos sem seed nesta migration.';

CREATE TABLE pagamento (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  plano_credito_id uuid REFERENCES plano_credito (id),
  provedor text NOT NULL,
  metodo text NOT NULL,
  txid text,
  identificador_provedor text,
  valor numeric(12,2) NOT NULL,
  moeda char(3) NOT NULL DEFAULT 'BRL',
  quantidade_creditos integer NOT NULL,
  status_interno text NOT NULL,
  status_provedor text,
  expiracao_em timestamptz,
  aprovado_em timestamptz,
  cancelado_em timestamptz,
  creditado_em timestamptz,
  idempotency_key text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT pagamento_provedor_chk CHECK (provedor IN ('EFI', 'MERCADO_PAGO_LEGADO', 'OUTRO_LEGADO', 'DESCONHECIDO')),
  CONSTRAINT pagamento_metodo_chk CHECK (metodo IN ('PIX', 'LEGADO', 'DESCONHECIDO')),
  CONSTRAINT pagamento_valor_chk CHECK (valor >= 0),
  CONSTRAINT pagamento_creditos_chk CHECK (quantidade_creditos >= 0),
  CONSTRAINT pagamento_moeda_chk CHECK (moeda ~ '^[A-Z]{3}$'),
  CONSTRAINT pagamento_status_interno_chk CHECK (status_interno IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'APROVADO', 'CANCELADO', 'EXPIRADO', 'ESTORNADO', 'ERRO', 'LEGADO')),
  CONSTRAINT pagamento_aprovado_creditado_chk CHECK (creditado_em IS NULL OR aprovado_em IS NOT NULL),
  CONSTRAINT pagamento_efi_pix_chk CHECK (provedor <> 'EFI' OR metodo = 'PIX'),
  CONSTRAINT pagamento_legado_sem_fluxo_ativo_chk CHECK (provedor <> 'MERCADO_PAGO_LEGADO' OR (metodo = 'LEGADO' AND status_interno NOT IN ('CRIADO', 'AGUARDANDO_PAGAMENTO'))),
  CONSTRAINT pagamento_aprovado_em_chk CHECK (status_interno <> 'APROVADO' OR aprovado_em IS NOT NULL),
  CONSTRAINT pagamento_compra_valida_chk CHECK (status_interno <> 'APROVADO' OR (valor > 0 AND quantidade_creditos > 0)),
  CONSTRAINT pagamento_status_legado_chk CHECK (status_interno <> 'LEGADO' OR provedor IN ('MERCADO_PAGO_LEGADO', 'OUTRO_LEGADO', 'DESCONHECIDO')),
  CONSTRAINT pagamento_id_provedor_uk UNIQUE (id, provedor)
);

CREATE UNIQUE INDEX pagamento_txid_uk ON pagamento (txid) WHERE txid IS NOT NULL;
CREATE UNIQUE INDEX pagamento_idempotency_uk ON pagamento (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX pagamento_identificador_provedor_uk ON pagamento (provedor, identificador_provedor) WHERE identificador_provedor IS NOT NULL;
CREATE INDEX pagamento_usuario_idx ON pagamento (usuario_id, criado_em);
CREATE INDEX pagamento_status_idx ON pagamento (status_interno, provedor, criado_em);

COMMENT ON TABLE pagamento IS 'Cobrancas e historico financeiro; EFI e modelado sem credenciais ou chamadas externas.';

CREATE TABLE pagamento_evento (
  id uuid PRIMARY KEY,
  pagamento_id uuid NOT NULL,
  provedor text NOT NULL,
  provedor_evento_id text,
  tipo_evento text NOT NULL,
  payload_hash text,
  status_provedor text,
  recebido_em timestamptz NOT NULL,
  processado_em timestamptz,
  resultado text,
  CONSTRAINT pagamento_evento_provedor_chk CHECK (provedor IN ('EFI', 'MERCADO_PAGO_LEGADO', 'OUTRO_LEGADO', 'DESCONHECIDO')),
  CONSTRAINT pagamento_evento_tipo_chk CHECK (length(tipo_evento) > 0),
  CONSTRAINT pagamento_evento_janela_chk CHECK (processado_em IS NULL OR processado_em >= recebido_em),
  CONSTRAINT pagamento_evento_pagamento_provedor_fk FOREIGN KEY (pagamento_id, provedor) REFERENCES pagamento (id, provedor)
);

CREATE INDEX pagamento_evento_pagamento_idx ON pagamento_evento (pagamento_id, recebido_em);
CREATE UNIQUE INDEX pagamento_evento_provedor_evento_uk ON pagamento_evento (provedor, provedor_evento_id) WHERE provedor_evento_id IS NOT NULL;

COMMENT ON TABLE pagamento_evento IS 'Eventos financeiros sanitizados; idempotencia de provedor_evento_id e escopada por provedor.';

CREATE TABLE pagamento_webhook (
  id uuid PRIMARY KEY,
  provedor text NOT NULL,
  evento_id text,
  txid text,
  payload_hash text NOT NULL,
  origem_ip_hash text,
  validacao_resultado text NOT NULL,
  recebido_em timestamptz NOT NULL,
  processado_em timestamptz,
  resultado text,
  erro_resumido text,
  tentativas integer NOT NULL DEFAULT 0,
  CONSTRAINT pagamento_webhook_provedor_chk CHECK (provedor IN ('EFI', 'MERCADO_PAGO_LEGADO', 'OUTRO_LEGADO', 'DESCONHECIDO')),
  CONSTRAINT pagamento_webhook_validacao_chk CHECK (validacao_resultado IN ('PENDENTE', 'VALIDO', 'INVALIDO', 'IGNORADO')),
  CONSTRAINT pagamento_webhook_tentativas_chk CHECK (tentativas >= 0),
  CONSTRAINT pagamento_webhook_janela_chk CHECK (processado_em IS NULL OR processado_em >= recebido_em)
);

CREATE UNIQUE INDEX pagamento_webhook_evento_uk ON pagamento_webhook (provedor, evento_id) WHERE evento_id IS NOT NULL;
CREATE INDEX pagamento_webhook_txid_idx ON pagamento_webhook (provedor, txid);
CREATE INDEX pagamento_webhook_payload_hash_idx ON pagamento_webhook (payload_hash);

COMMENT ON TABLE pagamento_webhook IS 'Webhooks financeiros persistem identificadores, hashes e resultado; sem payload bruto sensivel.';

CREATE TABLE pagamento_conciliacao (
  id uuid PRIMARY KEY,
  pagamento_id uuid NOT NULL REFERENCES pagamento (id),
  movimento_credito_id uuid REFERENCES movimento_credito (id),
  origem text NOT NULL,
  status text NOT NULL,
  valor_confirmado numeric(12,2) NOT NULL,
  creditos_confirmados integer NOT NULL,
  aprovado_em timestamptz,
  creditado_em timestamptz,
  criado_em timestamptz NOT NULL,
  CONSTRAINT pagamento_conciliacao_origem_chk CHECK (origem IN ('WEBHOOK', 'CONSULTA_PROVEDOR', 'IMPORTACAO', 'AJUSTE_ADMIN')),
  CONSTRAINT pagamento_conciliacao_status_chk CHECK (status IN ('PENDENTE', 'CONCILIADO', 'DIVERGENTE', 'CANCELADO')),
  CONSTRAINT pagamento_conciliacao_valor_chk CHECK (valor_confirmado >= 0),
  CONSTRAINT pagamento_conciliacao_creditos_chk CHECK (creditos_confirmados >= 0),
  CONSTRAINT pagamento_conciliacao_creditado_chk CHECK (creditado_em IS NULL OR aprovado_em IS NOT NULL)
);

CREATE UNIQUE INDEX pagamento_conciliacao_pagamento_uk ON pagamento_conciliacao (pagamento_id);
CREATE UNIQUE INDEX pagamento_conciliacao_movimento_uk ON pagamento_conciliacao (movimento_credito_id) WHERE movimento_credito_id IS NOT NULL;

COMMENT ON TABLE pagamento_conciliacao IS 'Conciliacao entre pagamento aprovado e lancamento de credito, sem executar regra financeira.';
