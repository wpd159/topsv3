-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Controle de importacao e staging; nao cria importador e nao importa dados.

CREATE TABLE importacao_execucao (
  id uuid PRIMARY KEY,
  sistema_origem text NOT NULL,
  status text NOT NULL,
  iniciado_em timestamptz NOT NULL,
  finalizado_em timestamptz,
  solicitado_por uuid REFERENCES usuario (id),
  resumo_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  criado_em timestamptz NOT NULL,
  CONSTRAINT importacao_execucao_status_chk CHECK (status IN ('PLANEJADA', 'EM_EXECUCAO', 'CONCLUIDA', 'CONCLUIDA_COM_PENDENCIAS', 'FALHA', 'CANCELADA')),
  CONSTRAINT importacao_execucao_janela_chk CHECK (finalizado_em IS NULL OR finalizado_em >= iniciado_em)
);

CREATE INDEX importacao_execucao_status_idx ON importacao_execucao (status, iniciado_em);

COMMENT ON TABLE importacao_execucao IS 'Execucao de importacao futura; nao acessa origem nesta migration.';

CREATE TABLE importacao_mapeamento (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  entidade_tipo text NOT NULL,
  entidade_v3_id uuid,
  status text NOT NULL,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT importacao_mapeamento_status_chk CHECK (status IN ('PENDENTE', 'MAPEADO', 'DIVERGENTE', 'REJEITADO'))
);

CREATE UNIQUE INDEX importacao_mapeamento_origem_uk ON importacao_mapeamento (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE INDEX importacao_mapeamento_hash_idx ON importacao_mapeamento (execucao_id, hash_origem);
CREATE INDEX importacao_mapeamento_entidade_idx ON importacao_mapeamento (entidade_tipo, entidade_v3_id);

COMMENT ON TABLE importacao_mapeamento IS 'Mapa origem -> V3 para idempotencia e auditoria.';

CREATE TABLE importacao_pendencia (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  codigo text NOT NULL,
  severidade text NOT NULL,
  status text NOT NULL,
  entidade_tipo text,
  id_origem text,
  detalhe_resumido text,
  criado_em timestamptz NOT NULL,
  resolvido_em timestamptz,
  CONSTRAINT importacao_pendencia_codigo_chk CHECK (codigo ~ '^[A-Z0-9_]+$'),
  CONSTRAINT importacao_pendencia_severidade_chk CHECK (severidade IN ('INFO', 'BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
  CONSTRAINT importacao_pendencia_status_chk CHECK (status IN ('ABERTA', 'EM_REVISAO', 'RESOLVIDA', 'ACEITA', 'REJEITADA'))
);

CREATE INDEX importacao_pendencia_execucao_idx ON importacao_pendencia (execucao_id, codigo, severidade, status);

COMMENT ON TABLE importacao_pendencia IS 'Pendencias de importacao que impedem promocao automatica.';

CREATE TABLE stg_usuario (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_usuario_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_anuncio (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_anuncio_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_localidade (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_localidade_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_midia (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_midia_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_story (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_story_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_pagamento (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_pagamento_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_credito (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_credito_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_premium (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_premium_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE TABLE stg_url (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  sistema_origem text NOT NULL,
  tabela_origem text NOT NULL,
  id_origem text NOT NULL,
  hash_origem text,
  payload_normalizado_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL,
  pendencia_codigo text,
  entidade_v3_id uuid,
  criado_em timestamptz NOT NULL,
  processado_em timestamptz,
  CONSTRAINT stg_url_status_chk CHECK (status IN ('PENDENTE', 'PROCESSADO', 'PENDENTE_REVISAO', 'REJEITADO'))
);

CREATE UNIQUE INDEX stg_usuario_origem_uk ON stg_usuario (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_anuncio_origem_uk ON stg_anuncio (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_localidade_origem_uk ON stg_localidade (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_midia_origem_uk ON stg_midia (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_story_origem_uk ON stg_story (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_pagamento_origem_uk ON stg_pagamento (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_credito_origem_uk ON stg_credito (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_premium_origem_uk ON stg_premium (execucao_id, sistema_origem, tabela_origem, id_origem);
CREATE UNIQUE INDEX stg_url_origem_uk ON stg_url (execucao_id, sistema_origem, tabela_origem, id_origem);

CREATE INDEX stg_usuario_status_idx ON stg_usuario (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_anuncio_status_idx ON stg_anuncio (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_localidade_status_idx ON stg_localidade (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_midia_status_idx ON stg_midia (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_story_status_idx ON stg_story (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_pagamento_status_idx ON stg_pagamento (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_credito_status_idx ON stg_credito (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_premium_status_idx ON stg_premium (execucao_id, status, pendencia_codigo);
CREATE INDEX stg_url_status_idx ON stg_url (execucao_id, status, pendencia_codigo);

COMMENT ON TABLE stg_usuario IS 'Staging de usuario para importacao futura, sem promover automaticamente.';
COMMENT ON TABLE stg_anuncio IS 'Staging de anuncio para importacao futura, sem dados de anuncio nesta migration.';
COMMENT ON TABLE stg_localidade IS 'Staging de localidade para saneamento futuro.';
COMMENT ON TABLE stg_midia IS 'Staging de midia com manifesto futuro, sem arquivo fisico.';
COMMENT ON TABLE stg_story IS 'Staging de stories para reconciliar anuncio_midia canonico.';
COMMENT ON TABLE stg_pagamento IS 'Staging financeiro para reconciliacao futura com tolerancia zero.';
COMMENT ON TABLE stg_credito IS 'Staging de creditos para razao e saldo projetado.';
COMMENT ON TABLE stg_premium IS 'Staging de premium para preservar beneficios existentes.';
COMMENT ON TABLE stg_url IS 'Staging de URLs para preservar SEO e redirects.';
