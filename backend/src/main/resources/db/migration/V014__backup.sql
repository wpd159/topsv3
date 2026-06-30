-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Controle de backup e testes de restauracao, sem executar backup.

CREATE TABLE backup_politica (
  id uuid PRIMARY KEY,
  codigo text NOT NULL,
  nome text NOT NULL,
  descricao text,
  retencao_dias integer NOT NULL,
  frequencia text NOT NULL,
  criptografia_obrigatoria boolean NOT NULL DEFAULT true,
  ativo boolean NOT NULL DEFAULT true,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT backup_politica_codigo_chk CHECK (codigo ~ '^[A-Z0-9_]+$'),
  CONSTRAINT backup_politica_retencao_chk CHECK (retencao_dias > 0),
  CONSTRAINT backup_politica_frequencia_chk CHECK (frequencia IN ('DIARIA', 'SEMANAL', 'MENSAL', 'MANUAL')),
  CONSTRAINT backup_politica_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX backup_politica_codigo_uk ON backup_politica (codigo);

COMMENT ON TABLE backup_politica IS 'Politicas de backup futuro; sem segredo e sem execucao nesta migration.';

CREATE TABLE backup_execucao (
  id uuid PRIMARY KEY,
  politica_id uuid NOT NULL REFERENCES backup_politica (id),
  status text NOT NULL,
  iniciado_em timestamptz NOT NULL,
  finalizado_em timestamptz,
  solicitado_por uuid REFERENCES usuario (id),
  erro_resumido text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT backup_execucao_status_chk CHECK (status IN ('AGENDADO', 'EM_EXECUCAO', 'SUCESSO', 'FALHA', 'CANCELADO')),
  CONSTRAINT backup_execucao_janela_chk CHECK (finalizado_em IS NULL OR finalizado_em >= iniciado_em)
);

CREATE INDEX backup_execucao_status_idx ON backup_execucao (status, iniciado_em);
CREATE INDEX backup_execucao_politica_idx ON backup_execucao (politica_id, iniciado_em);

COMMENT ON TABLE backup_execucao IS 'Registro de execucoes futuras de backup; nao contem arquivo fisico.';

CREATE TABLE backup_artefato (
  id uuid PRIMARY KEY,
  execucao_id uuid NOT NULL REFERENCES backup_execucao (id),
  tipo text NOT NULL,
  storage_provider text,
  bucket text,
  chave_objeto text,
  tamanho_bytes bigint,
  sha256 text,
  criptografado boolean NOT NULL DEFAULT true,
  criado_em timestamptz NOT NULL,
  CONSTRAINT backup_artefato_tipo_chk CHECK (tipo IN ('BANCO', 'MIDIA', 'CONFIGURACAO', 'SEO', 'RELEASE', 'LOG', 'MANIFESTO')),
  CONSTRAINT backup_artefato_tamanho_chk CHECK (tamanho_bytes IS NULL OR tamanho_bytes > 0)
);

CREATE INDEX backup_artefato_execucao_idx ON backup_artefato (execucao_id, tipo);

COMMENT ON TABLE backup_artefato IS 'Metadados de artefato de backup; sem armazenar credenciais ou dumps no Git.';

CREATE TABLE backup_teste_restauracao (
  id uuid PRIMARY KEY,
  artefato_id uuid NOT NULL REFERENCES backup_artefato (id),
  status text NOT NULL,
  ambiente text NOT NULL,
  iniciado_em timestamptz NOT NULL,
  finalizado_em timestamptz,
  responsavel_usuario_id uuid REFERENCES usuario (id),
  resultado_resumido text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT backup_teste_restauracao_status_chk CHECK (status IN ('PLANEJADO', 'EM_EXECUCAO', 'APROVADO', 'REPROVADO', 'CANCELADO')),
  CONSTRAINT backup_teste_restauracao_ambiente_chk CHECK (ambiente IN ('LOCAL', 'STAGING', 'HOMOLOGACAO')),
  CONSTRAINT backup_teste_restauracao_janela_chk CHECK (finalizado_em IS NULL OR finalizado_em >= iniciado_em)
);

CREATE INDEX backup_teste_restauracao_artefato_idx ON backup_teste_restauracao (artefato_id, iniciado_em);

COMMENT ON TABLE backup_teste_restauracao IS 'Registro futuro de teste de restore, sem executar restore nesta fase.';
