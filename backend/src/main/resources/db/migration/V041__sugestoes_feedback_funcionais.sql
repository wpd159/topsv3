-- Sugestoes e relatos de bugs enviados por usuarios autenticados.

CREATE TABLE feedback_sugestao (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id),
  tipo varchar(16) NOT NULL,
  titulo varchar(160) NOT NULL,
  descricao_resumida varchar(3000) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDENTE',
  providencia_resumida varchar(2000),
  responsavel_usuario_id uuid REFERENCES usuario (id),
  idempotency_key varchar(160) NOT NULL,
  request_id varchar(128) NOT NULL,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  decidido_em timestamptz,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT feedback_sugestao_tipo_chk CHECK (
    tipo IN ('FEATURE', 'BUG')
  ),
  CONSTRAINT feedback_sugestao_status_chk CHECK (
    status IN ('PENDENTE', 'EM_ANALISE', 'RESOLVIDO', 'RECUSADO')
  ),
  CONSTRAINT feedback_sugestao_titulo_chk CHECK (
    titulo = btrim(titulo) AND char_length(titulo) BETWEEN 5 AND 160
  ),
  CONSTRAINT feedback_sugestao_descricao_chk CHECK (
    descricao_resumida = btrim(descricao_resumida)
    AND char_length(descricao_resumida) BETWEEN 10 AND 3000
  ),
  CONSTRAINT feedback_sugestao_providencia_chk CHECK (
    providencia_resumida IS NULL
    OR char_length(providencia_resumida) BETWEEN 3 AND 2000
  ),
  CONSTRAINT feedback_sugestao_decisao_chk CHECK (
    (status IN ('RESOLVIDO', 'RECUSADO') AND decidido_em IS NOT NULL)
    OR (status IN ('PENDENTE', 'EM_ANALISE') AND decidido_em IS NULL)
  ),
  CONSTRAINT feedback_sugestao_idempotencia_uk UNIQUE (
    usuario_id, idempotency_key
  )
);

CREATE INDEX feedback_sugestao_fila_idx
  ON feedback_sugestao (status, criado_em DESC, id);

CREATE INDEX feedback_sugestao_tipo_idx
  ON feedback_sugestao (tipo, criado_em DESC);

COMMENT ON TABLE feedback_sugestao IS
  'Sugestoes e bugs autenticados; providencia administrativa nao e resposta publica.';
