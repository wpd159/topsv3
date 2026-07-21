-- Historico consolidado de visualizacoes anterior ao inicio dos eventos V3.

CREATE TABLE agregado_visualizacao_inicial (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  execucao_id uuid NOT NULL REFERENCES importacao_execucao (id),
  total_visualizacoes bigint NOT NULL,
  snapshot_fingerprint text NOT NULL,
  origem_hash text NOT NULL,
  snapshot_corte_em timestamptz NOT NULL,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT agregado_visualizacao_inicial_anuncio_uk UNIQUE (anuncio_id),
  CONSTRAINT agregado_visualizacao_inicial_total_chk CHECK (total_visualizacoes >= 0),
  CONSTRAINT agregado_visualizacao_inicial_fingerprint_chk CHECK (length(trim(snapshot_fingerprint)) > 0),
  CONSTRAINT agregado_visualizacao_inicial_origem_hash_chk CHECK (origem_hash ~ '^[0-9a-f]{32}$'),
  CONSTRAINT agregado_visualizacao_inicial_datas_chk CHECK (atualizado_em >= criado_em)
);

CREATE INDEX agregado_visualizacao_inicial_execucao_idx
  ON agregado_visualizacao_inicial (execucao_id);
CREATE INDEX agregado_visualizacao_inicial_corte_idx
  ON agregado_visualizacao_inicial (snapshot_corte_em);

COMMENT ON TABLE agregado_visualizacao_inicial IS
  'Total historico imutavel por anuncio no corte do snapshot; eventos V3 posteriores permanecem em evento_visualizacao.';
COMMENT ON COLUMN agregado_visualizacao_inicial.origem_hash IS
  'Hash sanitizado dos campos canonicos da linha de origem; nao contem titulo, slug ou dado pessoal.';
