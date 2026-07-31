-- Stories administrativos cumulativos e inicio diferido do beneficio de fotos extras.
-- A linha singleton existente e preservada como id=1; nao ha criacao retroativa de Stories.

ALTER TABLE story_selecao_administrativa
  DROP CONSTRAINT story_selecao_administrativa_singleton_chk;

ALTER TABLE story_selecao_administrativa
  DROP CONSTRAINT story_selecao_administrativa_estado_chk;

ALTER TABLE story_selecao_administrativa
  RENAME COLUMN singleton_id TO id;

ALTER TABLE story_selecao_administrativa
  ALTER COLUMN id TYPE bigint;

CREATE SEQUENCE story_selecao_administrativa_id_seq AS bigint START WITH 2;

SELECT setval(
  'story_selecao_administrativa_id_seq',
  GREATEST(COALESCE((SELECT MAX(id) + 1 FROM story_selecao_administrativa), 2), 2),
  false
);

ALTER SEQUENCE story_selecao_administrativa_id_seq
  OWNED BY story_selecao_administrativa.id;

ALTER TABLE story_selecao_administrativa
  ALTER COLUMN id SET DEFAULT nextval('story_selecao_administrativa_id_seq');

ALTER TABLE story_selecao_administrativa
  ADD COLUMN expira_em timestamptz,
  ADD COLUMN idempotency_key text;

ALTER TABLE story_selecao_administrativa
  ADD CONSTRAINT story_selecao_administrativa_estado_chk CHECK (
    (
      ativa
      AND anuncio_id IS NOT NULL
      AND ativado_por IS NOT NULL
      AND ativado_em IS NOT NULL
      AND (expira_em IS NULL OR expira_em > ativado_em)
    )
    OR
    (
      NOT ativa
      AND (
        (anuncio_id IS NULL AND ativado_por IS NULL AND ativado_em IS NULL AND expira_em IS NULL)
        OR
        (
          anuncio_id IS NOT NULL
          AND ativado_por IS NOT NULL
          AND ativado_em IS NOT NULL
          AND (expira_em IS NULL OR expira_em > ativado_em)
        )
      )
    )
  );

CREATE UNIQUE INDEX story_selecao_administrativa_idempotency_uk
  ON story_selecao_administrativa (idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX story_selecao_administrativa_anuncio_ativo_uk
  ON story_selecao_administrativa (anuncio_id)
  WHERE ativa;

CREATE INDEX story_selecao_administrativa_feed_idx
  ON story_selecao_administrativa (ativa, expira_em, ativado_em, id);

COMMENT ON TABLE story_selecao_administrativa IS
  'Selecoes administrativas cumulativas de Stories; cada anuncio ativo possui vigencia independente.';

COMMENT ON COLUMN story_selecao_administrativa.expira_em IS
  'Fim persistido da vigencia; nulo apenas para compatibilidade com a selecao criada antes da V046.';

ALTER TABLE ativacao_beneficio
  ALTER COLUMN inicio_em DROP NOT NULL,
  ALTER COLUMN fim_em DROP NOT NULL;

ALTER TABLE ativacao_beneficio
  DROP CONSTRAINT ativacao_beneficio_status_chk;

ALTER TABLE ativacao_beneficio
  DROP CONSTRAINT ativacao_beneficio_janela_chk;

ALTER TABLE ativacao_beneficio
  ADD CONSTRAINT ativacao_beneficio_status_chk CHECK (
    status IN ('AGUARDANDO_MODERACAO', 'AGENDADA', 'ATIVA', 'EXPIRADA', 'REVOGADA', 'CANCELADA')
  );

ALTER TABLE ativacao_beneficio
  ADD CONSTRAINT ativacao_beneficio_janela_chk CHECK (
    (status = 'AGUARDANDO_MODERACAO' AND inicio_em IS NULL AND fim_em IS NULL)
    OR
    (status <> 'AGUARDANDO_MODERACAO' AND inicio_em IS NOT NULL AND fim_em IS NOT NULL AND fim_em > inicio_em)
  );

CREATE UNIQUE INDEX ativacao_beneficio_pendente_anuncio_beneficio_uk
  ON ativacao_beneficio (anuncio_id, beneficio_id)
  WHERE status = 'AGUARDANDO_MODERACAO' AND anuncio_id IS NOT NULL;

COMMENT ON COLUMN ativacao_beneficio.inicio_em IS
  'Inicio efetivo; nulo enquanto o beneficio de fotos extras aguarda aprovacao de capacidade adicional.';

COMMENT ON COLUMN ativacao_beneficio.fim_em IS
  'Fim efetivo calculado no backend a partir da aprovacao que inicia o beneficio.';
