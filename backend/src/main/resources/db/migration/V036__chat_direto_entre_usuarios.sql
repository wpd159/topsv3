-- Chat direto entre usuarios autenticados.
-- O conteudo pertence ao historico da conversa e nunca e replicado na auditoria operacional.

CREATE TABLE chat_conversa (
  id uuid PRIMARY KEY,
  participante_a_id uuid NOT NULL REFERENCES usuario (id),
  participante_b_id uuid NOT NULL REFERENCES usuario (id),
  criado_request_id varchar(128) NOT NULL,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT chat_conversa_participantes_distintos_chk
    CHECK (participante_a_id <> participante_b_id),
  CONSTRAINT chat_conversa_ordem_canonica_chk
    CHECK (participante_a_id < participante_b_id),
  CONSTRAINT chat_conversa_janela_chk
    CHECK (atualizado_em >= criado_em),
  CONSTRAINT chat_conversa_request_id_chk
    CHECK (criado_request_id ~ '^[A-Za-z0-9._:-]{8,128}$'),
  CONSTRAINT chat_conversa_participantes_uk
    UNIQUE (participante_a_id, participante_b_id)
);

CREATE INDEX chat_conversa_participante_a_idx
  ON chat_conversa (participante_a_id, atualizado_em DESC);

CREATE INDEX chat_conversa_participante_b_idx
  ON chat_conversa (participante_b_id, atualizado_em DESC);

CREATE TABLE chat_mensagem (
  id uuid PRIMARY KEY,
  conversa_id uuid NOT NULL REFERENCES chat_conversa (id),
  remetente_usuario_id uuid NOT NULL REFERENCES usuario (id),
  destinatario_usuario_id uuid NOT NULL REFERENCES usuario (id),
  corpo text NOT NULL,
  idempotency_key varchar(160) NOT NULL,
  request_id varchar(128) NOT NULL,
  criado_em timestamptz NOT NULL,
  lido_em timestamptz,
  CONSTRAINT chat_mensagem_participantes_distintos_chk
    CHECK (remetente_usuario_id <> destinatario_usuario_id),
  CONSTRAINT chat_mensagem_corpo_chk
    CHECK (
      corpo = btrim(corpo)
      AND char_length(corpo) BETWEEN 1 AND 2000
    ),
  CONSTRAINT chat_mensagem_idempotency_key_chk
    CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{8,160}$'),
  CONSTRAINT chat_mensagem_request_id_chk
    CHECK (request_id ~ '^[A-Za-z0-9._:-]{8,128}$'),
  CONSTRAINT chat_mensagem_leitura_chk
    CHECK (lido_em IS NULL OR lido_em >= criado_em),
  CONSTRAINT chat_mensagem_idempotencia_uk
    UNIQUE (remetente_usuario_id, idempotency_key)
);

CREATE INDEX chat_mensagem_conversa_data_idx
  ON chat_mensagem (conversa_id, criado_em, id);

CREATE INDEX chat_mensagem_nao_lida_idx
  ON chat_mensagem (destinatario_usuario_id, criado_em)
  WHERE lido_em IS NULL;

CREATE OR REPLACE FUNCTION validar_participantes_chat_mensagem()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  conversa chat_conversa%ROWTYPE;
BEGIN
  SELECT *
  INTO conversa
  FROM chat_conversa
  WHERE id = NEW.conversa_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'conversa de chat inexistente';
  END IF;

  IF NOT (
    (NEW.remetente_usuario_id = conversa.participante_a_id
      AND NEW.destinatario_usuario_id = conversa.participante_b_id)
    OR
    (NEW.remetente_usuario_id = conversa.participante_b_id
      AND NEW.destinatario_usuario_id = conversa.participante_a_id)
  ) THEN
    RAISE EXCEPTION 'participantes da mensagem nao pertencem a conversa';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER chat_mensagem_participantes_trg
BEFORE INSERT OR UPDATE OF conversa_id, remetente_usuario_id, destinatario_usuario_id
ON chat_mensagem
FOR EACH ROW
EXECUTE FUNCTION validar_participantes_chat_mensagem();

COMMENT ON TABLE chat_conversa IS
  'Conversa direta unica entre dois anunciantes autenticados.';

COMMENT ON TABLE chat_mensagem IS
  'Mensagem direta em texto puro; requestId e idempotencia sao persistidos sem replicar o corpo em logs.';
