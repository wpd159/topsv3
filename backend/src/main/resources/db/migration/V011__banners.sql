-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Banners administraveis com dimensoes fixas e historico.

CREATE TABLE banner_espaco (
  id uuid PRIMARY KEY,
  codigo text NOT NULL,
  nome text NOT NULL,
  largura_desktop integer NOT NULL,
  altura_desktop integer NOT NULL,
  largura_mobile integer NOT NULL,
  altura_mobile integer NOT NULL,
  ativo boolean NOT NULL DEFAULT true,
  criado_em timestamptz NOT NULL,
  CONSTRAINT banner_espaco_codigo_chk CHECK (codigo ~ '^[A-Z0-9_]+$'),
  CONSTRAINT banner_espaco_dimensoes_chk CHECK (largura_desktop > 0 AND altura_desktop > 0 AND largura_mobile > 0 AND altura_mobile > 0)
);

CREATE UNIQUE INDEX banner_espaco_codigo_uk ON banner_espaco (codigo);

COMMENT ON TABLE banner_espaco IS 'Espacos de banner; dimensoes esperadas incluem desktop 1452x500 e mobile 1080x900.';

CREATE TABLE banner (
  id uuid PRIMARY KEY,
  banner_espaco_id uuid NOT NULL REFERENCES banner_espaco (id),
  titulo text,
  subtitulo text,
  texto_botao text,
  url_destino text,
  alt_text_desktop text,
  alt_text_mobile text,
  arquivo_desktop_id uuid REFERENCES arquivo_midia (id),
  arquivo_mobile_id uuid REFERENCES arquivo_midia (id),
  status text NOT NULL,
  inicio_em timestamptz,
  fim_em timestamptz,
  ordem integer NOT NULL DEFAULT 0,
  versao integer NOT NULL DEFAULT 0,
  criado_por uuid REFERENCES usuario (id),
  atualizado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT banner_status_chk CHECK (status IN ('RASCUNHO', 'AGENDADO', 'PUBLICADO', 'INATIVO', 'ENCERRADO')),
  CONSTRAINT banner_midias_publicacao_chk CHECK (
    status NOT IN ('AGENDADO', 'PUBLICADO')
    OR (
      arquivo_desktop_id IS NOT NULL
      AND arquivo_mobile_id IS NOT NULL
      AND alt_text_desktop IS NOT NULL
      AND length(trim(alt_text_desktop)) > 0
      AND alt_text_mobile IS NOT NULL
      AND length(trim(alt_text_mobile)) > 0
    )
  ),
  CONSTRAINT banner_janela_chk CHECK (fim_em IS NULL OR inicio_em IS NULL OR fim_em > inicio_em),
  CONSTRAINT banner_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT banner_versao_chk CHECK (versao >= 0)
);

CREATE INDEX banner_espaco_status_idx ON banner (banner_espaco_id, status, inicio_em, fim_em, ordem);
CREATE INDEX banner_arquivos_idx ON banner (arquivo_desktop_id, arquivo_mobile_id);

COMMENT ON TABLE banner IS 'Banner futuro editavel pelo admin; rascunho pode existir sem midia, agendado/publicado exige midia e texto alternativo.';

CREATE TABLE banner_versao (
  id uuid PRIMARY KEY,
  banner_id uuid NOT NULL REFERENCES banner (id),
  snapshot_json jsonb NOT NULL,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL
);

CREATE INDEX banner_versao_banner_idx ON banner_versao (banner_id, criado_em);

COMMENT ON TABLE banner_versao IS 'Snapshot para historico e rollback de banner.';
