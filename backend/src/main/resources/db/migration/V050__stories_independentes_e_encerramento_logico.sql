-- Stories independentes de anuncio, encerramento logico e reaproveitamento de direito em falha tecnica.
-- Nenhuma linha historica e reescrita nesta migration.

ALTER TABLE story_anuncio
  ADD COLUMN arquivo_midia_id uuid REFERENCES arquivo_midia (id),
  ADD COLUMN encerrado_em timestamptz,
  ADD COLUMN encerrado_por uuid REFERENCES usuario (id),
  ADD COLUMN origem_encerramento text,
  ADD COLUMN motivo_encerramento text,
  ADD COLUMN descricao_encerramento text,
  ADD COLUMN direito_preservado boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX ativacao_beneficio_id_usuario_uk
  ON ativacao_beneficio (id, usuario_id);

ALTER TABLE story_anuncio
  ADD CONSTRAINT story_anuncio_ativacao_proprietario_fk
    FOREIGN KEY (ativacao_beneficio_id, criado_por)
    REFERENCES ativacao_beneficio (id, usuario_id),
  ADD CONSTRAINT story_anuncio_origem_encerramento_chk
    CHECK (origem_encerramento IS NULL OR origem_encerramento IN ('USUARIO', 'ADMIN')),
  ADD CONSTRAINT story_anuncio_encerramento_consistencia_chk
    CHECK (
      (encerrado_em IS NULL AND encerrado_por IS NULL AND origem_encerramento IS NULL
        AND motivo_encerramento IS NULL AND descricao_encerramento IS NULL)
      OR (encerrado_em IS NOT NULL AND encerrado_por IS NOT NULL AND origem_encerramento IS NOT NULL)
    ),
  ADD CONSTRAINT story_anuncio_motivo_admin_chk
    CHECK (
      origem_encerramento IS DISTINCT FROM 'ADMIN'
      OR motivo_encerramento IN (
        'VIOLACAO_REGRAS', 'DENUNCIA_PROCEDENTE', 'DETERMINACAO_JURIDICA', 'ERRO_TECNICO', 'OUTRO'
      )
    ),
  ADD CONSTRAINT story_anuncio_outro_descricao_chk
    CHECK (
      motivo_encerramento IS DISTINCT FROM 'OUTRO'
      OR descricao_encerramento IS NOT NULL AND btrim(descricao_encerramento) <> ''
    );

ALTER TABLE story_anuncio
  DROP CONSTRAINT story_anuncio_autogestao_consistencia_chk;

ALTER TABLE story_anuncio
  ADD CONSTRAINT story_anuncio_autogestao_consistencia_chk
    CHECK (
      (
        modo_conteudo IS NULL AND anuncio_id IS NULL AND arquivo_midia_id IS NULL
        AND ativacao_beneficio_id IS NULL AND idempotency_key IS NULL
        AND request_fingerprint IS NULL AND anuncio_midia_id IS NOT NULL
      )
      OR (
        modo_conteudo = 'ANUNCIO' AND anuncio_id IS NOT NULL
        AND anuncio_midia_id IS NULL AND arquivo_midia_id IS NULL
        AND ativacao_beneficio_id IS NOT NULL AND criado_por IS NOT NULL
        AND idempotency_key IS NOT NULL AND btrim(idempotency_key) <> ''
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
      )
      OR (
        modo_conteudo = 'MIDIA_UPLOAD' AND ativacao_beneficio_id IS NOT NULL
        AND criado_por IS NOT NULL AND idempotency_key IS NOT NULL
        AND btrim(idempotency_key) <> '' AND request_fingerprint ~ '^[0-9a-f]{64}$'
        AND (
          (anuncio_id IS NULL AND anuncio_midia_id IS NULL AND arquivo_midia_id IS NOT NULL)
          OR (anuncio_id IS NOT NULL AND anuncio_midia_id IS NOT NULL AND arquivo_midia_id IS NULL)
        )
      )
    );

DROP INDEX story_anuncio_autogestao_idempotencia_uk;
CREATE UNIQUE INDEX story_anuncio_idempotencia_anuncio_uk
  ON story_anuncio (criado_por, anuncio_id, idempotency_key)
  WHERE modo_conteudo = 'ANUNCIO'
    AND anuncio_id IS NOT NULL
    AND idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX story_anuncio_idempotencia_midia_upload_uk
  ON story_anuncio (criado_por, idempotency_key)
  WHERE modo_conteudo = 'MIDIA_UPLOAD'
    AND anuncio_id IS NULL
    AND idempotency_key IS NOT NULL;

DROP INDEX story_anuncio_autogestao_ativacao_uk;
CREATE UNIQUE INDEX story_anuncio_autogestao_ativacao_uk
  ON story_anuncio (ativacao_beneficio_id)
  WHERE ativacao_beneficio_id IS NOT NULL AND direito_preservado = false;

DROP INDEX story_anuncio_autogestao_status_ativo_uk;
CREATE UNIQUE INDEX story_anuncio_autogestao_status_ativo_uk
  ON story_anuncio (anuncio_id)
  WHERE modo_conteudo = 'ANUNCIO'
    AND encerrado_em IS NULL
    AND status IN ('RASCUNHO', 'PENDENTE', 'PUBLICADO');

CREATE UNIQUE INDEX story_anuncio_arquivo_direto_uk
  ON story_anuncio (arquivo_midia_id)
  WHERE arquivo_midia_id IS NOT NULL;

CREATE INDEX story_anuncio_conta_gestao_idx
  ON story_anuncio (criado_por, encerrado_em, criado_em DESC, id DESC)
  WHERE modo_conteudo IS NOT NULL;

COMMENT ON COLUMN story_anuncio.arquivo_midia_id IS
  'Arquivo exclusivo do modo MIDIA_UPLOAD independente de anuncio e de galeria.';
COMMENT ON COLUMN story_anuncio.direito_preservado IS
  'True somente quando falha tecnica comprovada libera a ativacao para nova tentativa sem debito.';
COMMENT ON COLUMN story_anuncio.encerrado_em IS
  'Encerramento logico; a limpeza do objeto ocorre apos o commit pelo ObjectStorage canonico.';
