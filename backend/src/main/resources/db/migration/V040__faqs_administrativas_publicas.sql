-- FAQs administrativas e publicas da V3.

CREATE TABLE faq_item (
  id uuid PRIMARY KEY,
  pergunta varchar(240) NOT NULL,
  resposta text NOT NULL,
  categoria varchar(24) NOT NULL,
  status varchar(16) NOT NULL,
  ordem integer NOT NULL DEFAULT 0,
  criado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  atualizado_por_usuario_id uuid NOT NULL REFERENCES usuario (id),
  criado_request_id varchar(128) NOT NULL,
  publicado_em timestamptz,
  arquivado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT faq_item_pergunta_chk CHECK (
    pergunta = btrim(pergunta) AND char_length(pergunta) BETWEEN 5 AND 240
  ),
  CONSTRAINT faq_item_resposta_chk CHECK (
    resposta = btrim(resposta) AND char_length(resposta) BETWEEN 10 AND 4000
  ),
  CONSTRAINT faq_item_categoria_chk CHECK (
    categoria IN ('GERAL', 'CONTA', 'PAGAMENTOS', 'SEGURANCA', 'ANUNCIOS')
  ),
  CONSTRAINT faq_item_status_chk CHECK (
    status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO')
  ),
  CONSTRAINT faq_item_ordem_chk CHECK (ordem BETWEEN 0 AND 100000),
  CONSTRAINT faq_item_publicacao_chk CHECK (
    (status = 'PUBLICADO' AND publicado_em IS NOT NULL AND arquivado_em IS NULL)
    OR (status = 'RASCUNHO' AND arquivado_em IS NULL)
    OR (status = 'ARQUIVADO' AND arquivado_em IS NOT NULL)
  ),
  CONSTRAINT faq_item_criacao_request_uk UNIQUE (
    criado_por_usuario_id, criado_request_id
  )
);

CREATE INDEX faq_item_admin_idx
  ON faq_item (status, categoria, ordem, atualizado_em DESC);

CREATE INDEX faq_item_publico_idx
  ON faq_item (ordem, categoria, atualizado_em DESC, id)
  WHERE status = 'PUBLICADO';

COMMENT ON TABLE faq_item IS
  'Fonte unica das FAQs V3; somente itens PUBLICADO integram o contrato publico.';
