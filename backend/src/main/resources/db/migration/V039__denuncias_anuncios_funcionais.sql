CREATE TABLE denuncia_anuncio (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio(id),
  denunciante_usuario_id uuid REFERENCES usuario(id),
  denunciante_contexto_hash varchar(64) NOT NULL,
  motivo varchar(40) NOT NULL,
  descricao_resumida varchar(1000),
  status varchar(20) NOT NULL DEFAULT 'PENDENTE',
  providencia_resumida varchar(2000),
  responsavel_usuario_id uuid REFERENCES usuario(id),
  idempotency_key varchar(160) NOT NULL,
  request_id varchar(128) NOT NULL,
  ip_hash varchar(64),
  user_agent_hash varchar(64),
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  decidido_em timestamptz,
  versao integer NOT NULL DEFAULT 0,
  CONSTRAINT denuncia_anuncio_motivo_chk CHECK (
    motivo IN ('CONTEUDO_INADEQUADO', 'PERFIL_FALSO', 'GOLPE', 'SPAM', 'OUTROS')
  ),
  CONSTRAINT denuncia_anuncio_status_chk CHECK (
    status IN ('PENDENTE', 'PUNIDA', 'IGNORADA')
  ),
  CONSTRAINT denuncia_anuncio_descricao_chk CHECK (
    descricao_resumida IS NULL OR length(descricao_resumida) BETWEEN 1 AND 1000
  ),
  CONSTRAINT denuncia_anuncio_providencia_chk CHECK (
    providencia_resumida IS NULL OR length(providencia_resumida) BETWEEN 3 AND 2000
  )
);

CREATE UNIQUE INDEX denuncia_anuncio_idempotencia_uk
  ON denuncia_anuncio (anuncio_id, denunciante_contexto_hash, idempotency_key);

CREATE INDEX denuncia_anuncio_fila_idx
  ON denuncia_anuncio (status, criado_em DESC, id);

CREATE INDEX denuncia_anuncio_motivo_idx
  ON denuncia_anuncio (motivo, criado_em DESC);

CREATE INDEX denuncia_anuncio_anuncio_idx
  ON denuncia_anuncio (anuncio_id, criado_em DESC);
