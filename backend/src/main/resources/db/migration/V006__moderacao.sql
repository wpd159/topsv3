-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Revisoes e decisoes de moderacao sem fluxo funcional.

CREATE TABLE revisao_anuncio (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  tipo text NOT NULL,
  status text NOT NULL,
  payload_solicitado jsonb NOT NULL DEFAULT '{}'::jsonb,
  criado_por uuid REFERENCES usuario (id),
  criado_em timestamptz NOT NULL,
  finalizado_em timestamptz,
  CONSTRAINT revisao_anuncio_tipo_chk CHECK (tipo IN ('CRIACAO', 'EDICAO', 'MIDIA', 'DOCUMENTO', 'DENUNCIA')),
  CONSTRAINT revisao_anuncio_status_chk CHECK (status IN ('ABERTA', 'EM_ANALISE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
  CONSTRAINT revisao_anuncio_janela_chk CHECK (finalizado_em IS NULL OR finalizado_em >= criado_em)
);

CREATE INDEX revisao_anuncio_anuncio_status_idx ON revisao_anuncio (anuncio_id, status, criado_em);
CREATE INDEX revisao_anuncio_criado_por_idx ON revisao_anuncio (criado_por, criado_em);

COMMENT ON TABLE revisao_anuncio IS 'Fila auditavel de revisao de anuncio, midia ou documento.';

CREATE TABLE anuncio_midia_revisao (
  id uuid PRIMARY KEY,
  revisao_anuncio_id uuid NOT NULL REFERENCES revisao_anuncio (id),
  anuncio_midia_id uuid REFERENCES anuncio_midia (id),
  arquivo_midia_id uuid REFERENCES arquivo_midia (id),
  acao text NOT NULL,
  status text NOT NULL,
  ordem integer,
  motivo text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT anuncio_midia_revisao_acao_chk CHECK (acao IN ('ADICIONAR', 'SUBSTITUIR', 'REMOVER', 'REORDENAR')),
  CONSTRAINT anuncio_midia_revisao_status_chk CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'CANCELADA')),
  CONSTRAINT anuncio_midia_revisao_adicionar_chk CHECK (acao <> 'ADICIONAR' OR (arquivo_midia_id IS NOT NULL AND anuncio_midia_id IS NULL)),
  CONSTRAINT anuncio_midia_revisao_substituir_chk CHECK (acao <> 'SUBSTITUIR' OR (arquivo_midia_id IS NOT NULL AND anuncio_midia_id IS NOT NULL)),
  CONSTRAINT anuncio_midia_revisao_remover_chk CHECK (acao <> 'REMOVER' OR anuncio_midia_id IS NOT NULL),
  CONSTRAINT anuncio_midia_revisao_reordenar_chk CHECK (acao <> 'REORDENAR' OR (anuncio_midia_id IS NOT NULL AND ordem IS NOT NULL))
);

CREATE INDEX anuncio_midia_revisao_revisao_idx ON anuncio_midia_revisao (revisao_anuncio_id, status);
CREATE INDEX anuncio_midia_revisao_midia_idx ON anuncio_midia_revisao (anuncio_midia_id);

COMMENT ON TABLE anuncio_midia_revisao IS 'Alteracoes propostas de midia dentro de uma revisao de anuncio.';

CREATE TABLE decisao_moderacao (
  id uuid PRIMARY KEY,
  revisao_anuncio_id uuid NOT NULL REFERENCES revisao_anuncio (id),
  decisao text NOT NULL,
  motivo text,
  ator_usuario_id uuid REFERENCES usuario (id),
  ip_hash text,
  user_agent_hash text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT decisao_moderacao_decisao_chk CHECK (decisao IN ('APROVAR', 'REJEITAR', 'BLOQUEAR', 'SOLICITAR_AJUSTE', 'CANCELAR'))
);

CREATE INDEX decisao_moderacao_revisao_idx ON decisao_moderacao (revisao_anuncio_id, criado_em);
CREATE INDEX decisao_moderacao_ator_idx ON decisao_moderacao (ator_usuario_id, criado_em);

COMMENT ON TABLE decisao_moderacao IS 'Decisao auditavel de moderacao; sempre referencia revisao_anuncio_id.';
