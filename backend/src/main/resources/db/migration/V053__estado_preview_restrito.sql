-- Persiste o ciclo do unico preview restrito corrente por arquivo de midia.
-- Colunas nullable mantem compatibilidade de leitura e escrita com a release 052.
ALTER TABLE arquivo_midia
  ADD COLUMN preview_restrito_tipo varchar(32),
  ADD COLUMN preview_restrito_chave varchar(1024),
  ADD COLUMN preview_restrito_pipeline_versao varchar(32),
  ADD COLUMN preview_restrito_status varchar(32) DEFAULT 'DESCONHECIDO',
  ADD COLUMN preview_restrito_confirmado_em timestamptz;

ALTER TABLE arquivo_midia
  ADD CONSTRAINT arquivo_midia_preview_restrito_status_ck
    CHECK (preview_restrito_status IS NULL OR preview_restrito_status IN (
      'DESCONHECIDO', 'PENDENTE', 'DISPONIVEL', 'FALHA', 'REMOVIDO'
    )),
  ADD CONSTRAINT arquivo_midia_preview_restrito_disponivel_ck
    CHECK (
      preview_restrito_status <> 'DISPONIVEL'
      OR (
        preview_restrito_tipo = 'PREVIEW_RESTRITO'
        AND preview_restrito_chave IS NOT NULL
        AND preview_restrito_pipeline_versao IS NOT NULL
        AND preview_restrito_confirmado_em IS NOT NULL
      )
    );

CREATE INDEX arquivo_midia_preview_restrito_status_idx
  ON arquivo_midia (preview_restrito_status)
  WHERE preview_restrito_status IS NOT NULL;

COMMENT ON COLUMN arquivo_midia.preview_restrito_status IS
  'Estado fail-closed do preview publico restrito; registros legados iniciam DESCONHECIDO.';
