-- V054/V055 mantidas imutaveis: os estados de aplicacao fora das fixtures
-- descartaveis nao foram comprovados. Esta evolucao so acrescenta estruturas.

-- O mesmo Story pode voltar a ficar exibivel antes do vencimento original.
-- Cada retomada abre um periodo distinto; o writer serializa pelo registro
-- operacional do Story e nao sobrepoe janelas.
ALTER TABLE arquivo_publicidade_story_veiculacao
  DROP CONSTRAINT arquivo_publicidade_story_veiculacao_story_uk;
CREATE UNIQUE INDEX arquivo_publicidade_story_periodo_inicio_uk
  ON arquivo_publicidade_story_veiculacao (story_id, inicio_em);
CREATE INDEX arquivo_publicidade_story_periodos_idx
  ON arquivo_publicidade_story_veiculacao (story_id, inicio_em DESC, id);

-- Uma versao posterior pode apontar para bytes ja copiados e verificados.
-- A chave privada continua pertencendo somente a linha de origem; a FK
-- impede excluir essa origem enquanto houver versoes que a referenciem.
CREATE TABLE arquivo_publicidade_midia_referencia (
  id uuid PRIMARY KEY,
  versao_id uuid NOT NULL REFERENCES arquivo_publicidade_versao (id),
  origem_midia_id uuid NOT NULL REFERENCES arquivo_publicidade_midia (id),
  anuncio_midia_id uuid NOT NULL REFERENCES anuncio_midia (id),
  variante text NOT NULL,
  ordem integer NOT NULL,
  CONSTRAINT arquivo_publicidade_midia_referencia_variante_chk
    CHECK (variante IN ('ORIGINAL', 'PREVIEW_RESTRITO')),
  CONSTRAINT arquivo_publicidade_midia_referencia_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT arquivo_publicidade_midia_referencia_unica_uk
    UNIQUE (versao_id, anuncio_midia_id, variante)
);
CREATE INDEX arquivo_publicidade_midia_referencia_origem_idx
  ON arquivo_publicidade_midia_referencia (origem_midia_id);

CREATE TABLE arquivo_publicidade_story_midia_referencia (
  id uuid PRIMARY KEY,
  versao_id uuid NOT NULL REFERENCES arquivo_publicidade_story_versao (id),
  origem_midia_id uuid NOT NULL REFERENCES arquivo_publicidade_story_midia (id),
  arquivo_midia_id uuid NOT NULL REFERENCES arquivo_midia (id),
  variante text NOT NULL,
  ordem integer NOT NULL,
  CONSTRAINT arquivo_publicidade_story_midia_referencia_variante_chk
    CHECK (variante IN ('ORIGINAL', 'PREVIEW_RESTRITO')),
  CONSTRAINT arquivo_publicidade_story_midia_referencia_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT arquivo_publicidade_story_midia_referencia_unica_uk
    UNIQUE (versao_id, arquivo_midia_id, variante, ordem)
);
CREATE INDEX arquivo_publicidade_story_midia_referencia_origem_idx
  ON arquivo_publicidade_story_midia_referencia (origem_midia_id);

COMMENT ON TABLE arquivo_publicidade_midia_referencia IS
  'Uso de copia privada ja verificada por outra versao, sem nova leitura/escrita no storage.';
COMMENT ON TABLE arquivo_publicidade_story_midia_referencia IS
  'Uso de copia privada de Story ja verificada por outra versao; a origem permanece retida.';
