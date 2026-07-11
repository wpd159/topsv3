-- Selecao singleton para a sequencia administrativa do feed de Stories.
-- Nao cria Story pago, nao referencia credito e nao duplica midia.

CREATE TABLE story_selecao_administrativa (
  singleton_id smallint PRIMARY KEY DEFAULT 1,
  anuncio_id uuid REFERENCES anuncio (id),
  ativa boolean NOT NULL DEFAULT false,
  ativado_por uuid REFERENCES usuario (id),
  ativado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT story_selecao_administrativa_singleton_chk CHECK (singleton_id = 1),
  CONSTRAINT story_selecao_administrativa_estado_chk CHECK (
    (ativa AND anuncio_id IS NOT NULL AND ativado_por IS NOT NULL AND ativado_em IS NOT NULL)
    OR
    (NOT ativa AND anuncio_id IS NULL AND ativado_por IS NULL AND ativado_em IS NULL)
  ),
  CONSTRAINT story_selecao_administrativa_versao_chk CHECK (versao >= 0)
);

COMMENT ON TABLE story_selecao_administrativa IS
  'Fonte singleton da selecao administrativa de Stories; as midias sao resolvidas dinamicamente do anuncio.';
COMMENT ON COLUMN story_selecao_administrativa.anuncio_id IS
  'Anuncio publico selecionado; nulo quando a sequencia administrativa esta desativada.';
