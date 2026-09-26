-- Planos prospectivos sao evidencia de uma regra temporal conhecida, nao versoes
-- ja ocorridas. O processamento posterior grava a fronteira efetiva separada
-- do instante em que a derivacao foi executada.

ALTER TABLE arquivo_publicidade_versao
  ADD COLUMN evidencia_origem_versao_id uuid REFERENCES arquivo_publicidade_versao (id);
ALTER TABLE arquivo_publicidade_story_versao
  ADD COLUMN evidencia_origem_versao_id uuid REFERENCES arquivo_publicidade_story_versao (id);
-- NULL identifies a still-current archived version that has not yet been
-- prospectively reconciled by this release (notably pre-V057 rows).
ALTER TABLE arquivo_publicidade_versao
  ADD COLUMN transicoes_reconciliadas_em timestamptz;
ALTER TABLE arquivo_publicidade_story_versao
  ADD COLUMN transicoes_reconciliadas_em timestamptz;
CREATE INDEX arquivo_publicidade_versao_bootstrap_idx
  ON arquivo_publicidade_versao (id)
  WHERE transicoes_reconciliadas_em IS NULL;
CREATE INDEX arquivo_publicidade_story_versao_bootstrap_idx
  ON arquivo_publicidade_story_versao (id)
  WHERE transicoes_reconciliadas_em IS NULL;

CREATE TABLE arquivo_publicidade_transicao_plano (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  veiculacao_id uuid NOT NULL REFERENCES arquivo_publicidade_veiculacao (id),
  evidencia_versao_id uuid NOT NULL REFERENCES arquivo_publicidade_versao (id),
  evidencia_sha256 text NOT NULL,
  fronteira_em timestamptz NOT NULL,
  planejado_em timestamptz NOT NULL,
  processado_em timestamptz,
  cancelado_em timestamptz,
  estado text NOT NULL,
  resultado_versao_id uuid REFERENCES arquivo_publicidade_versao (id),
  conteudo_projetado_json jsonb NOT NULL,
  dependencias_json jsonb NOT NULL,
  plano_sha256 text NOT NULL,
  CONSTRAINT arquivo_publicidade_transicao_estado_chk CHECK (
    (estado = 'PENDENTE' AND processado_em IS NULL AND cancelado_em IS NULL
      AND resultado_versao_id IS NULL)
    OR (estado = 'PROCESSADA' AND processado_em IS NOT NULL AND cancelado_em IS NULL)
    OR (estado = 'CANCELADA' AND cancelado_em IS NOT NULL AND processado_em IS NULL
      AND resultado_versao_id IS NULL)
  ),
  CONSTRAINT arquivo_publicidade_transicao_evidencia_hash_chk
    CHECK (evidencia_sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT arquivo_publicidade_transicao_plano_hash_chk
    CHECK (plano_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX arquivo_publicidade_transicao_pendente_uk
  ON arquivo_publicidade_transicao_plano (veiculacao_id, fronteira_em)
  WHERE estado = 'PENDENTE';
CREATE INDEX arquivo_publicidade_transicao_vencimento_idx
  ON arquivo_publicidade_transicao_plano (fronteira_em, anuncio_id)
  WHERE estado = 'PENDENTE';

CREATE TABLE arquivo_publicidade_transicao_plano_midia (
  plano_id uuid NOT NULL REFERENCES arquivo_publicidade_transicao_plano (id),
  origem_midia_id uuid NOT NULL REFERENCES arquivo_publicidade_midia (id),
  anuncio_midia_id uuid NOT NULL REFERENCES anuncio_midia (id),
  variante text NOT NULL,
  ordem integer NOT NULL,
  PRIMARY KEY (plano_id, anuncio_midia_id, variante),
  CONSTRAINT arquivo_publicidade_transicao_midia_variante_chk
    CHECK (variante IN ('ORIGINAL', 'PREVIEW_RESTRITO')),
  CONSTRAINT arquivo_publicidade_transicao_midia_ordem_chk CHECK (ordem >= 0)
);
CREATE INDEX arquivo_publicidade_transicao_midia_origem_idx
  ON arquivo_publicidade_transicao_plano_midia (origem_midia_id);

CREATE TABLE arquivo_publicidade_story_transicao_plano (
  id uuid PRIMARY KEY,
  story_id uuid NOT NULL REFERENCES story_anuncio (id),
  veiculacao_id uuid NOT NULL REFERENCES arquivo_publicidade_story_veiculacao (id),
  evidencia_versao_id uuid NOT NULL REFERENCES arquivo_publicidade_story_versao (id),
  evidencia_sha256 text NOT NULL,
  fronteira_em timestamptz NOT NULL,
  planejado_em timestamptz NOT NULL,
  processado_em timestamptz,
  cancelado_em timestamptz,
  estado text NOT NULL,
  resultado_versao_id uuid REFERENCES arquivo_publicidade_story_versao (id),
  conteudo_projetado_json jsonb NOT NULL,
  dependencias_json jsonb NOT NULL,
  plano_sha256 text NOT NULL,
  CONSTRAINT arquivo_publicidade_story_transicao_estado_chk CHECK (
    (estado = 'PENDENTE' AND processado_em IS NULL AND cancelado_em IS NULL
      AND resultado_versao_id IS NULL)
    OR (estado = 'PROCESSADA' AND processado_em IS NOT NULL AND cancelado_em IS NULL)
    OR (estado = 'CANCELADA' AND cancelado_em IS NOT NULL AND processado_em IS NULL
      AND resultado_versao_id IS NULL)
  ),
  CONSTRAINT arquivo_publicidade_story_transicao_evidencia_hash_chk
    CHECK (evidencia_sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT arquivo_publicidade_story_transicao_plano_hash_chk
    CHECK (plano_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX arquivo_publicidade_story_transicao_pendente_uk
  ON arquivo_publicidade_story_transicao_plano (veiculacao_id, fronteira_em)
  WHERE estado = 'PENDENTE';
CREATE INDEX arquivo_publicidade_story_transicao_vencimento_idx
  ON arquivo_publicidade_story_transicao_plano (fronteira_em, story_id)
  WHERE estado = 'PENDENTE';

CREATE TABLE arquivo_publicidade_story_transicao_plano_midia (
  plano_id uuid NOT NULL REFERENCES arquivo_publicidade_story_transicao_plano (id),
  origem_midia_id uuid NOT NULL REFERENCES arquivo_publicidade_story_midia (id),
  arquivo_midia_id uuid NOT NULL REFERENCES arquivo_midia (id),
  variante text NOT NULL,
  ordem integer NOT NULL,
  PRIMARY KEY (plano_id, arquivo_midia_id, variante, ordem),
  CONSTRAINT arquivo_publicidade_story_transicao_midia_variante_chk
    CHECK (variante IN ('ORIGINAL', 'PREVIEW_RESTRITO')),
  CONSTRAINT arquivo_publicidade_story_transicao_midia_ordem_chk CHECK (ordem >= 0)
);
CREATE INDEX arquivo_publicidade_story_transicao_midia_origem_idx
  ON arquivo_publicidade_story_transicao_plano_midia (origem_midia_id);

COMMENT ON TABLE arquivo_publicidade_transicao_plano IS
  'Projecao ainda nao ocorrida de mudanca temporal do anuncio; conserva fonte e midias privadas verificadas.';
COMMENT ON TABLE arquivo_publicidade_story_transicao_plano IS
  'Projecao ainda nao ocorrida de Story ANUNCIO; MIDIA_UPLOAD nao depende dos beneficios de galeria.';
COMMENT ON COLUMN arquivo_publicidade_versao.evidencia_origem_versao_id IS
  'Fonte prospectiva ja capturada usada para derivar versao de expiracao; capturado_em e o processamento real.';
COMMENT ON COLUMN arquivo_publicidade_story_versao.evidencia_origem_versao_id IS
  'Fonte prospectiva ja capturada usada para derivar versao de expiracao; capturado_em e o processamento real.';
COMMENT ON COLUMN arquivo_publicidade_versao.transicoes_reconciliadas_em IS
  'Marker prospectivo de planejamento bem-sucedido; NULL exige verificacao no bootstrap, sem reconstruir passado.';
COMMENT ON COLUMN arquivo_publicidade_story_versao.transicoes_reconciliadas_em IS
  'Marker prospectivo de planejamento bem-sucedido; NULL exige verificacao no bootstrap, sem reconstruir passado.';
