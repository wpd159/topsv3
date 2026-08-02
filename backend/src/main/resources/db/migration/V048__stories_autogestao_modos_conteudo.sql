-- Stories de autogestao: o anuncio pode ser o conteudo ou possuir uma unica midia exclusiva.
-- Linhas historicas permanecem sem modo explicito e continuam vinculadas a anuncio_midia.

ALTER TABLE story_anuncio
  ALTER COLUMN anuncio_midia_id DROP NOT NULL,
  ADD COLUMN anuncio_id uuid,
  ADD COLUMN modo_conteudo text,
  ADD COLUMN ativacao_beneficio_id uuid,
  ADD COLUMN idempotency_key text,
  ADD COLUMN request_fingerprint text;

CREATE UNIQUE INDEX anuncio_id_usuario_id_uk
  ON anuncio (id, usuario_id);

CREATE UNIQUE INDEX anuncio_midia_anuncio_id_id_uk
  ON anuncio_midia (anuncio_id, id);

CREATE UNIQUE INDEX ativacao_beneficio_id_anuncio_usuario_uk
  ON ativacao_beneficio (id, anuncio_id, usuario_id);

ALTER TABLE story_anuncio
  ADD CONSTRAINT story_anuncio_anuncio_proprietario_fk
    FOREIGN KEY (anuncio_id, criado_por)
    REFERENCES anuncio (id, usuario_id),
  ADD CONSTRAINT story_anuncio_midia_anuncio_fk
    FOREIGN KEY (anuncio_id, anuncio_midia_id)
    REFERENCES anuncio_midia (anuncio_id, id),
  ADD CONSTRAINT story_anuncio_ativacao_anuncio_proprietario_fk
    FOREIGN KEY (ativacao_beneficio_id, anuncio_id, criado_por)
    REFERENCES ativacao_beneficio (id, anuncio_id, usuario_id),
  ADD CONSTRAINT story_anuncio_modo_conteudo_chk
    CHECK (modo_conteudo IS NULL OR modo_conteudo IN ('ANUNCIO', 'MIDIA_UPLOAD')),
  ADD CONSTRAINT story_anuncio_autogestao_consistencia_chk
    CHECK (
      (
        modo_conteudo IS NULL
        AND anuncio_id IS NULL
        AND ativacao_beneficio_id IS NULL
        AND idempotency_key IS NULL
        AND request_fingerprint IS NULL
        AND anuncio_midia_id IS NOT NULL
      )
      OR (
        modo_conteudo = 'ANUNCIO'
        AND anuncio_id IS NOT NULL
        AND anuncio_midia_id IS NULL
        AND ativacao_beneficio_id IS NOT NULL
        AND criado_por IS NOT NULL
        AND idempotency_key IS NOT NULL
        AND btrim(idempotency_key) <> ''
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
      )
      OR (
        modo_conteudo = 'MIDIA_UPLOAD'
        AND anuncio_id IS NOT NULL
        AND anuncio_midia_id IS NOT NULL
        AND ativacao_beneficio_id IS NOT NULL
        AND criado_por IS NOT NULL
        AND idempotency_key IS NOT NULL
        AND btrim(idempotency_key) <> ''
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
      )
    );

CREATE UNIQUE INDEX story_anuncio_autogestao_idempotencia_uk
  ON story_anuncio (anuncio_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX story_anuncio_autogestao_ativacao_uk
  ON story_anuncio (ativacao_beneficio_id)
  WHERE ativacao_beneficio_id IS NOT NULL;

CREATE UNIQUE INDEX story_anuncio_autogestao_status_ativo_uk
  ON story_anuncio (anuncio_id)
  WHERE modo_conteudo IS NOT NULL
    AND status IN ('RASCUNHO', 'PENDENTE', 'PUBLICADO');

CREATE INDEX story_anuncio_autogestao_feed_idx
  ON story_anuncio (status, inicio_em, fim_em, anuncio_id, criado_por)
  WHERE modo_conteudo IS NOT NULL;

COMMENT ON COLUMN story_anuncio.modo_conteudo IS
  'ANUNCIO usa apresentacao publica do anuncio; MIDIA_UPLOAD usa anuncio_midia exclusivo com finalidade STORY.';
COMMENT ON COLUMN story_anuncio.idempotency_key IS
  'Chave opaca de idempotencia da autogestao, escopada pelo anuncio.';
COMMENT ON COLUMN story_anuncio.request_fingerprint IS
  'SHA-256 da intencao completa; impede reutilizar uma chave com modo ou arquivo diferente.';
