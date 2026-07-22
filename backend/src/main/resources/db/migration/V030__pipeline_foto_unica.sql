-- Processamento aditivo apenas para novas fotos. Midias existentes permanecem nulas e nao sao reprocessadas.

ALTER TABLE arquivo_midia
  ADD COLUMN pipeline_versao integer,
  ADD COLUMN marca_dagua_versao text,
  ADD COLUMN processado_em timestamptz,
  ADD COLUMN sha256_origem text;

ALTER TABLE arquivo_midia
  ADD CONSTRAINT arquivo_midia_pipeline_versao_chk
    CHECK (pipeline_versao IS NULL OR pipeline_versao > 0),
  ADD CONSTRAINT arquivo_midia_sha256_origem_chk
    CHECK (sha256_origem IS NULL OR sha256_origem ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT arquivo_midia_pipeline_consistencia_chk
    CHECK (
      (pipeline_versao IS NULL
        AND marca_dagua_versao IS NULL
        AND processado_em IS NULL
        AND sha256_origem IS NULL)
      OR
      (pipeline_versao IS NOT NULL
        AND marca_dagua_versao IS NOT NULL
        AND length(trim(marca_dagua_versao)) > 0
        AND processado_em IS NOT NULL
        AND sha256_origem IS NOT NULL)
    );

COMMENT ON COLUMN arquivo_midia.pipeline_versao IS
  'Versao do pipeline aplicado uma unica vez a nova foto; nulo para legado e outros tipos.';
COMMENT ON COLUMN arquivo_midia.marca_dagua_versao IS
  'Identificador imutavel do asset oficial aplicado ao derivado final.';
COMMENT ON COLUMN arquivo_midia.processado_em IS
  'Instante UTC em que o unico arquivo permanente foi gerado e validado.';
COMMENT ON COLUMN arquivo_midia.sha256_origem IS
  'Hash do upload temporario usado para idempotencia; o original nao e persistido.';
