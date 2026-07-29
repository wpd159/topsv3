-- Observabilidade operacional do wizard de anuncios.
-- Registra somente etapa, modo, estado e vinculos canonicos; nao persiste campos do formulario.

CREATE TABLE wizard_progresso (
  id uuid PRIMARY KEY,
  sessao_id varchar(80) NOT NULL,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  anuncio_id uuid REFERENCES anuncio (id),
  modo varchar(8) NOT NULL,
  ultimo_step varchar(16) NOT NULL,
  maior_step_ordem smallint NOT NULL,
  status varchar(24) NOT NULL,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  concluido_em timestamptz,
  CONSTRAINT wizard_progresso_sessao_usuario_uk UNIQUE (usuario_id, sessao_id),
  CONSTRAINT wizard_progresso_sessao_formato_chk
    CHECK (sessao_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$'),
  CONSTRAINT wizard_progresso_modo_chk
    CHECK (modo IN ('CREATE', 'EDIT')),
  CONSTRAINT wizard_progresso_step_chk
    CHECK (ultimo_step IN (
      'PERFIL', 'LOCALIZACAO', 'SERVICOS', 'FOTOS',
      'REVISAO', 'PREMIUM', 'KYC', 'CONCLUIDO'
    )),
  CONSTRAINT wizard_progresso_step_ordem_chk
    CHECK (maior_step_ordem BETWEEN 0 AND 7),
  CONSTRAINT wizard_progresso_status_chk
    CHECK (status IN (
      'EM_PREENCHIMENTO', 'AGUARDANDO_MODERACAO',
      'PUBLICADO', 'REJEITADO'
    )),
  CONSTRAINT wizard_progresso_conclusao_chk
    CHECK (
      (status = 'EM_PREENCHIMENTO' AND concluido_em IS NULL)
      OR (status <> 'EM_PREENCHIMENTO' AND concluido_em IS NOT NULL)
    )
);

CREATE INDEX wizard_progresso_periodo_idx
  ON wizard_progresso (criado_em, atualizado_em);
CREATE INDEX wizard_progresso_status_idx
  ON wizard_progresso (status, atualizado_em);
CREATE INDEX wizard_progresso_usuario_idx
  ON wizard_progresso (usuario_id, atualizado_em DESC);
CREATE INDEX wizard_progresso_anuncio_idx
  ON wizard_progresso (anuncio_id)
  WHERE anuncio_id IS NOT NULL;

COMMENT ON TABLE wizard_progresso IS
  'Observabilidade do wizard de anuncios sem dados pessoais ou conteudo do formulario.';
COMMENT ON COLUMN wizard_progresso.sessao_id IS
  'Identificador opaco gerado pelo cliente e reutilizado em retries da mesma jornada.';
COMMENT ON COLUMN wizard_progresso.maior_step_ordem IS
  'Maior etapa comprovadamente alcancada; impede regressao do funil por navegacao ou retry tardio.';
