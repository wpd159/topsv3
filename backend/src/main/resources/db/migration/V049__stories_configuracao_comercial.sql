CREATE TABLE story_configuracao_comercial (
  id smallint PRIMARY KEY,
  ativo boolean NOT NULL,
  custo_creditos integer NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  atualizado_em timestamptz NOT NULL,
  atualizado_por uuid NOT NULL REFERENCES usuario (id),
  CONSTRAINT story_configuracao_comercial_singleton_chk CHECK (id = 1),
  CONSTRAINT story_configuracao_comercial_custo_chk CHECK (custo_creditos >= 0),
  CONSTRAINT story_configuracao_comercial_versao_chk CHECK (versao >= 0)
);

COMMENT ON TABLE story_configuracao_comercial IS
  'Configuracao comercial exclusiva de Stories; a duracao e regra fixa de dominio e nao e persistida.';
