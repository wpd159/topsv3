-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Premium preservado, beneficios auditaveis e razao de creditos.

CREATE TABLE beneficio_premium (
  id uuid PRIMARY KEY,
  codigo text NOT NULL,
  nome text NOT NULL,
  descricao text NOT NULL,
  escopo text NOT NULL,
  afeta_ranking boolean NOT NULL DEFAULT false,
  ativo boolean NOT NULL DEFAULT true,
  criado_em timestamptz NOT NULL,
  CONSTRAINT beneficio_premium_codigo_chk CHECK (codigo ~ '^[A-Z0-9_]+$'),
  CONSTRAINT beneficio_premium_escopo_chk CHECK (escopo IN ('ANUNCIO', 'USUARIO', 'MIDIA', 'RELATORIO'))
);

CREATE UNIQUE INDEX beneficio_premium_codigo_uk ON beneficio_premium (codigo);

COMMENT ON TABLE beneficio_premium IS 'Catalogo de beneficios premium preservaveis e aditivos; sem seed nesta migration.';

CREATE TABLE beneficio_premium_opcao (
  id uuid PRIMARY KEY,
  beneficio_id uuid NOT NULL REFERENCES beneficio_premium (id),
  duracao_dias integer NOT NULL,
  custo_creditos integer NOT NULL,
  preco_referencia numeric(12,2),
  versao_regra integer NOT NULL,
  ativo boolean NOT NULL DEFAULT true,
  vigencia_inicio_em timestamptz,
  vigencia_fim_em timestamptz,
  criado_em timestamptz NOT NULL,
  CONSTRAINT beneficio_premium_opcao_duracao_chk CHECK (duracao_dias > 0),
  CONSTRAINT beneficio_premium_opcao_creditos_chk CHECK (custo_creditos >= 0),
  CONSTRAINT beneficio_premium_opcao_preco_chk CHECK (preco_referencia IS NULL OR preco_referencia >= 0),
  CONSTRAINT beneficio_premium_opcao_versao_chk CHECK (versao_regra > 0),
  CONSTRAINT beneficio_premium_opcao_vigencia_chk CHECK (vigencia_fim_em IS NULL OR vigencia_inicio_em IS NULL OR vigencia_fim_em > vigencia_inicio_em)
);

CREATE INDEX beneficio_premium_opcao_beneficio_idx ON beneficio_premium_opcao (beneficio_id, ativo);

COMMENT ON TABLE beneficio_premium_opcao IS 'Opcoes versionadas de duracao, creditos e preco de referencia.';

CREATE TABLE grupo_ativacao_beneficio (
  id uuid PRIMARY KEY,
  tipo text NOT NULL,
  origem text NOT NULL,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  anuncio_id uuid REFERENCES anuncio (id),
  ator_usuario_id uuid REFERENCES usuario (id),
  campanha_codigo text,
  validade_inicio_em timestamptz NOT NULL,
  validade_fim_em timestamptz NOT NULL,
  status text NOT NULL,
  idempotency_key text,
  observacao text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT grupo_ativacao_beneficio_tipo_chk CHECK (tipo IN ('PACOTE', 'CAMPANHA', 'CORTESIA', 'ADMIN', 'IMPORTACAO')),
  CONSTRAINT grupo_ativacao_beneficio_origem_chk CHECK (origem IN ('COMPRA', 'CREDITO', 'CORTESIA', 'CAMPANHA', 'ADMIN', 'IMPORTACAO')),
  CONSTRAINT grupo_ativacao_beneficio_status_chk CHECK (status IN ('PLANEJADO', 'ATIVO', 'EXPIRADO', 'REVOGADO', 'CANCELADO')),
  CONSTRAINT grupo_ativacao_beneficio_janela_chk CHECK (validade_fim_em > validade_inicio_em)
);

CREATE UNIQUE INDEX grupo_ativacao_beneficio_idempotency_uk ON grupo_ativacao_beneficio (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX grupo_ativacao_beneficio_usuario_idx ON grupo_ativacao_beneficio (usuario_id, status, validade_inicio_em, validade_fim_em);
CREATE INDEX grupo_ativacao_beneficio_anuncio_idx ON grupo_ativacao_beneficio (anuncio_id, status) WHERE anuncio_id IS NOT NULL;

COMMENT ON TABLE grupo_ativacao_beneficio IS 'Pacote/campanha/cortesia que agrupa multiplas ativacoes premium sem regra funcional nesta migration.';

CREATE TABLE ativacao_beneficio (
  id uuid PRIMARY KEY,
  beneficio_id uuid NOT NULL REFERENCES beneficio_premium (id),
  opcao_id uuid REFERENCES beneficio_premium_opcao (id),
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  anuncio_id uuid REFERENCES anuncio (id),
  grupo_ativacao_id uuid REFERENCES grupo_ativacao_beneficio (id),
  origem text NOT NULL,
  ator_usuario_id uuid REFERENCES usuario (id),
  campanha_codigo text,
  inicio_em timestamptz NOT NULL,
  fim_em timestamptz NOT NULL,
  status text NOT NULL,
  custo_creditos_snapshot integer NOT NULL,
  preco_snapshot numeric(12,2),
  idempotency_key text,
  revogada_em timestamptz,
  motivo_revogacao text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT ativacao_beneficio_origem_chk CHECK (origem IN ('COMPRA', 'CREDITO', 'CORTESIA', 'CAMPANHA', 'ADMIN', 'IMPORTACAO')),
  CONSTRAINT ativacao_beneficio_status_chk CHECK (status IN ('AGENDADA', 'ATIVA', 'EXPIRADA', 'REVOGADA', 'CANCELADA')),
  CONSTRAINT ativacao_beneficio_janela_chk CHECK (fim_em > inicio_em),
  CONSTRAINT ativacao_beneficio_custo_chk CHECK (custo_creditos_snapshot >= 0),
  CONSTRAINT ativacao_beneficio_preco_chk CHECK (preco_snapshot IS NULL OR preco_snapshot >= 0)
);

CREATE UNIQUE INDEX ativacao_beneficio_idempotency_uk ON ativacao_beneficio (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ativacao_beneficio_anuncio_status_idx ON ativacao_beneficio (anuncio_id, status, inicio_em, fim_em);
CREATE INDEX ativacao_beneficio_usuario_idx ON ativacao_beneficio (usuario_id, criado_em);
CREATE INDEX ativacao_beneficio_grupo_idx ON ativacao_beneficio (grupo_ativacao_id) WHERE grupo_ativacao_id IS NOT NULL;

COMMENT ON TABLE ativacao_beneficio IS 'Ativacao premium auditavel, com origem, snapshot e grupo para pacotes/campanhas.';

CREATE TABLE movimento_credito (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  tipo text NOT NULL,
  direcao text NOT NULL,
  quantidade integer NOT NULL,
  saldo_antes integer NOT NULL,
  saldo_depois integer NOT NULL,
  origem text NOT NULL,
  referencia_tipo text,
  referencia_id uuid,
  idempotency_key text,
  ator_usuario_id uuid REFERENCES usuario (id),
  observacao text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT movimento_credito_tipo_chk CHECK (tipo IN ('ENTRADA', 'SAIDA', 'AJUSTE', 'ESTORNO')),
  CONSTRAINT movimento_credito_direcao_chk CHECK (direcao IN ('CREDITO', 'DEBITO')),
  CONSTRAINT movimento_credito_quantidade_chk CHECK (quantidade > 0),
  CONSTRAINT movimento_credito_saldo_antes_chk CHECK (saldo_antes >= 0),
  CONSTRAINT movimento_credito_saldo_depois_chk CHECK (saldo_depois >= 0),
  CONSTRAINT movimento_credito_origem_chk CHECK (origem IN ('PAGAMENTO', 'BENEFICIO', 'AJUSTE_ADMIN', 'ESTORNO', 'IMPORTACAO', 'CAMPANHA')),
  CONSTRAINT movimento_credito_saldo_coerente_chk CHECK (
    (direcao = 'CREDITO' AND saldo_depois = saldo_antes + quantidade)
    OR (direcao = 'DEBITO' AND saldo_depois = saldo_antes - quantidade)
  ),
  CONSTRAINT movimento_credito_tipo_direcao_chk CHECK (
    (tipo = 'ENTRADA' AND direcao = 'CREDITO')
    OR (tipo = 'SAIDA' AND direcao = 'DEBITO')
    OR (tipo IN ('AJUSTE', 'ESTORNO') AND direcao IN ('CREDITO', 'DEBITO'))
  ),
  CONSTRAINT movimento_credito_estorno_referencia_chk CHECK (tipo <> 'ESTORNO' OR (referencia_tipo IS NOT NULL AND referencia_id IS NOT NULL)),
  CONSTRAINT movimento_credito_ajuste_auditoria_chk CHECK (tipo <> 'AJUSTE' OR (origem = 'AJUSTE_ADMIN' AND ator_usuario_id IS NOT NULL AND observacao IS NOT NULL))
);

CREATE UNIQUE INDEX movimento_credito_idempotency_uk ON movimento_credito (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX movimento_credito_usuario_data_idx ON movimento_credito (usuario_id, criado_em);
CREATE INDEX movimento_credito_referencia_idx ON movimento_credito (referencia_tipo, referencia_id);

COMMENT ON TABLE movimento_credito IS 'Razao imutavel de creditos; direcao define soma/subtracao e ajuste manual exige auditoria.';

CREATE TABLE saldo_credito_usuario (
  usuario_id uuid PRIMARY KEY REFERENCES usuario (id),
  saldo_atual integer NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT saldo_credito_usuario_saldo_chk CHECK (saldo_atual >= 0),
  CONSTRAINT saldo_credito_usuario_versao_chk CHECK (versao >= 0)
);

COMMENT ON TABLE saldo_credito_usuario IS 'Projecao de saldo de creditos com controle otimista.';
