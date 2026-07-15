-- Saldo de abertura da importacao: um unico movimento imutavel por usuario.

ALTER TABLE movimento_credito
  ADD COLUMN metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE movimento_credito
  DROP CONSTRAINT movimento_credito_tipo_chk,
  DROP CONSTRAINT movimento_credito_tipo_direcao_chk;

ALTER TABLE movimento_credito
  ADD CONSTRAINT movimento_credito_tipo_chk CHECK (
    tipo IN ('ENTRADA', 'SAIDA', 'AJUSTE', 'ESTORNO', 'MIGRACAO_SALDO_INICIAL')
  ),
  ADD CONSTRAINT movimento_credito_tipo_direcao_chk CHECK (
    (tipo = 'ENTRADA' AND direcao = 'CREDITO')
    OR (tipo = 'SAIDA' AND direcao = 'DEBITO')
    OR (tipo IN ('AJUSTE', 'ESTORNO') AND direcao IN ('CREDITO', 'DEBITO'))
    OR (tipo = 'MIGRACAO_SALDO_INICIAL' AND direcao = 'CREDITO')
  ),
  ADD CONSTRAINT movimento_credito_metadata_objeto_chk CHECK (
    jsonb_typeof(metadata_json) = 'object'
  ),
  ADD CONSTRAINT movimento_credito_migracao_saldo_inicial_chk CHECK (
    tipo <> 'MIGRACAO_SALDO_INICIAL'
    OR (
      origem = 'IMPORTACAO'
      AND direcao = 'CREDITO'
      AND saldo_antes = 0
      AND referencia_tipo = 'SNAPSHOT_IMPORTACAO'
      AND referencia_id IS NOT NULL
      AND idempotency_key IS NOT NULL
      AND metadata_json ?& ARRAY[
        'snapshotId',
        'snapshotFingerprint',
        'usuarioLegadoHash',
        'saldoImportado',
        'divergenciaHistorica',
        'versaoImportador'
      ]
    )
  );

CREATE UNIQUE INDEX movimento_credito_migracao_saldo_usuario_uk
  ON movimento_credito (usuario_id)
  WHERE tipo = 'MIGRACAO_SALDO_INICIAL';

COMMENT ON COLUMN movimento_credito.metadata_json IS
  'Metadata operacional sanitizada; nunca contem credenciais ou dados pessoais brutos.';

COMMENT ON INDEX movimento_credito_migracao_saldo_usuario_uk IS
  'Impede mais de um saldo inicial de importacao por usuario.';
