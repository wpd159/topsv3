-- Estado juridico canonico de anuncios e trilha imutavel das intervencoes administrativas.

ALTER TABLE anuncio DROP CONSTRAINT anuncio_status_chk;
ALTER TABLE anuncio
  ADD CONSTRAINT anuncio_status_chk
    CHECK (status IN (
      'RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO',
      'PAUSADO', 'REJEITADO', 'BLOQUEADO', 'REMOVIDO'
    ));

ALTER TABLE anuncio_status_historico DROP CONSTRAINT anuncio_status_historico_status_novo_chk;
ALTER TABLE anuncio_status_historico
  ADD CONSTRAINT anuncio_status_historico_status_novo_chk
    CHECK (status_novo IN (
      'RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO',
      'PAUSADO', 'REJEITADO', 'BLOQUEADO', 'REMOVIDO'
    ));

ALTER TABLE anuncio_status_historico DROP CONSTRAINT anuncio_status_historico_status_anterior_chk;
ALTER TABLE anuncio_status_historico
  ADD CONSTRAINT anuncio_status_historico_status_anterior_chk
    CHECK (status_anterior IS NULL OR status_anterior IN (
      'RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO',
      'PAUSADO', 'REJEITADO', 'BLOQUEADO', 'REMOVIDO'
    ));

CREATE TABLE anuncio_bloqueio_juridico (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  escopo text NOT NULL,
  categoria text NOT NULL,
  status_usuario_anterior text,
  motivo text NOT NULL,
  observacao_interna text,
  bloqueado_por_id uuid NOT NULL REFERENCES usuario (id),
  bloqueado_em timestamptz NOT NULL,
  bloqueio_request_id text NOT NULL,
  anuncio_desbloqueado_por_id uuid REFERENCES usuario (id),
  anuncio_desbloqueado_em timestamptz,
  anuncio_desbloqueio_request_id text,
  usuario_desbloqueado_por_id uuid REFERENCES usuario (id),
  usuario_desbloqueado_em timestamptz,
  usuario_desbloqueio_request_id text,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT anuncio_bloqueio_juridico_escopo_chk
    CHECK (escopo IN ('ANUNCIO', 'ANUNCIO_E_USUARIO')),
  CONSTRAINT anuncio_bloqueio_juridico_categoria_chk
    CHECK (categoria IN (
      'DENUNCIA_GRAVE',
      'USO_NAO_AUTORIZADO_IMAGEM',
      'FRAUDE',
      'ORDEM_OU_RISCO_JURIDICO',
      'OUTRA_INTERVENCAO'
    )),
  CONSTRAINT anuncio_bloqueio_juridico_status_usuario_chk
    CHECK (status_usuario_anterior IS NULL OR status_usuario_anterior IN (
      'ATIVO', 'PENDENTE', 'SUSPENSO', 'DESATIVADO', 'IMPORTADO'
    )),
  CONSTRAINT anuncio_bloqueio_juridico_motivo_chk
    CHECK (char_length(btrim(motivo)) BETWEEN 5 AND 1000),
  CONSTRAINT anuncio_bloqueio_juridico_observacao_chk
    CHECK (observacao_interna IS NULL OR char_length(observacao_interna) <= 2000),
  CONSTRAINT anuncio_bloqueio_juridico_request_chk
    CHECK (char_length(btrim(bloqueio_request_id)) BETWEEN 1 AND 200),
  CONSTRAINT anuncio_bloqueio_juridico_usuario_escopo_chk
    CHECK (
      (escopo = 'ANUNCIO' AND status_usuario_anterior IS NULL
        AND usuario_desbloqueado_por_id IS NULL
        AND usuario_desbloqueado_em IS NULL
        AND usuario_desbloqueio_request_id IS NULL)
      OR
      (escopo = 'ANUNCIO_E_USUARIO' AND status_usuario_anterior IS NOT NULL)
    ),
  CONSTRAINT anuncio_bloqueio_juridico_desbloqueio_anuncio_chk
    CHECK (
      (anuncio_desbloqueado_por_id IS NULL
        AND anuncio_desbloqueado_em IS NULL
        AND anuncio_desbloqueio_request_id IS NULL)
      OR
      (anuncio_desbloqueado_por_id IS NOT NULL
        AND anuncio_desbloqueado_em IS NOT NULL
        AND anuncio_desbloqueio_request_id IS NOT NULL
        AND char_length(btrim(anuncio_desbloqueio_request_id)) BETWEEN 1 AND 200)
    ),
  CONSTRAINT anuncio_bloqueio_juridico_desbloqueio_usuario_chk
    CHECK (
      (usuario_desbloqueado_por_id IS NULL
        AND usuario_desbloqueado_em IS NULL
        AND usuario_desbloqueio_request_id IS NULL)
      OR
      (usuario_desbloqueado_por_id IS NOT NULL
        AND usuario_desbloqueado_em IS NOT NULL
        AND usuario_desbloqueio_request_id IS NOT NULL
        AND char_length(btrim(usuario_desbloqueio_request_id)) BETWEEN 1 AND 200)
    ),
  CONSTRAINT anuncio_bloqueio_juridico_ordem_datas_chk
    CHECK (
      (anuncio_desbloqueado_em IS NULL OR anuncio_desbloqueado_em >= bloqueado_em)
      AND (usuario_desbloqueado_em IS NULL OR usuario_desbloqueado_em >= bloqueado_em)
    ),
  CONSTRAINT anuncio_bloqueio_juridico_versao_chk CHECK (versao >= 0)
);

CREATE UNIQUE INDEX anuncio_bloqueio_juridico_anuncio_ativo_uk
  ON anuncio_bloqueio_juridico (anuncio_id)
  WHERE anuncio_desbloqueado_em IS NULL;

CREATE UNIQUE INDEX anuncio_bloqueio_juridico_usuario_ativo_uk
  ON anuncio_bloqueio_juridico (usuario_id)
  WHERE escopo = 'ANUNCIO_E_USUARIO' AND usuario_desbloqueado_em IS NULL;

CREATE INDEX anuncio_bloqueio_juridico_anuncio_historico_idx
  ON anuncio_bloqueio_juridico (anuncio_id, bloqueado_em DESC);

CREATE INDEX anuncio_bloqueio_juridico_usuario_historico_idx
  ON anuncio_bloqueio_juridico (usuario_id, bloqueado_em DESC);

COMMENT ON TABLE anuncio_bloqueio_juridico IS
  'Trilha juridica protegida de bloqueios e desbloqueios; nao integra contratos publicos.';
