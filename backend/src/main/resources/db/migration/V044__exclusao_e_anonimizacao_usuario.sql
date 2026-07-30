-- Encerramento definitivo de contas comuns com preservacao de historicos por FK.

ALTER TABLE usuario
  DROP CONSTRAINT usuario_status_chk,
  ADD CONSTRAINT usuario_status_chk CHECK (
    status IN ('ATIVO', 'PENDENTE', 'SUSPENSO', 'DESATIVADO', 'IMPORTADO', 'EXCLUIDO')
  ),
  ADD COLUMN exclusao_tipo varchar(40),
  ADD COLUMN excluido_em timestamptz,
  ADD COLUMN excluido_por uuid REFERENCES usuario (id);

ALTER TABLE usuario
  ADD CONSTRAINT usuario_exclusao_tipo_chk CHECK (
    exclusao_tipo IS NULL OR exclusao_tipo = 'EXCLUSAO_COM_ANONIMIZACAO'
  ),
  ADD CONSTRAINT usuario_exclusao_estado_chk CHECK (
    (
      status = 'EXCLUIDO'
      AND exclusao_tipo = 'EXCLUSAO_COM_ANONIMIZACAO'
      AND excluido_em IS NOT NULL
      AND excluido_por IS NOT NULL
      AND nome = 'Conta excluida'
      AND nome_civil IS NULL
      AND cpf_normalizado IS NULL
      AND telefone_normalizado IS NULL
      AND data_nascimento IS NULL
      AND email_normalizado ~
        '^conta-excluida\+[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}@topsdojob\.invalid$'
    )
    OR (
      status <> 'EXCLUIDO'
      AND exclusao_tipo IS NULL
      AND excluido_em IS NULL
      AND excluido_por IS NULL
    )
  );

CREATE INDEX usuario_excluido_em_idx
  ON usuario (excluido_em DESC, id)
  WHERE status = 'EXCLUIDO';

COMMENT ON COLUMN usuario.exclusao_tipo IS
  'Estrategia definitiva aplicada quando a FK historica exige preservar a identidade tecnica.';
COMMENT ON COLUMN usuario.excluido_em IS
  'Instante UTC da anonimização irreversivel da conta.';
COMMENT ON COLUMN usuario.excluido_por IS
  'ADMIN que confirmou a exclusao; nunca recebe dados pessoais do alvo.';
