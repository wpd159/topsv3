-- Consolida KYC do anunciante e moderacao de documentos privados no fluxo V3.

ALTER TABLE usuario
  ADD COLUMN nome_civil text,
  ADD COLUMN cpf_normalizado text;

ALTER TABLE usuario
  ADD CONSTRAINT usuario_nome_civil_chk
    CHECK (nome_civil IS NULL OR length(trim(nome_civil)) BETWEEN 3 AND 180),
  ADD CONSTRAINT usuario_cpf_normalizado_chk
    CHECK (cpf_normalizado IS NULL OR cpf_normalizado ~ '^[0-9]{11}$');

CREATE UNIQUE INDEX usuario_cpf_normalizado_uk
  ON usuario (cpf_normalizado)
  WHERE cpf_normalizado IS NOT NULL;

COMMENT ON COLUMN usuario.nome_civil IS
  'Nome civil privado informado no KYC; nunca exposto em DTO publico.';
COMMENT ON COLUMN usuario.cpf_normalizado IS
  'CPF privado com onze digitos; nunca exposto em DTO publico.';

ALTER TABLE documento_usuario
  ADD COLUMN envio_id uuid,
  ADD COLUMN parte text,
  ADD COLUMN motivo_moderacao text,
  ADD COLUMN revisado_por uuid REFERENCES usuario (id),
  ADD COLUMN revisado_em timestamptz;

UPDATE documento_usuario
SET envio_id = id,
    parte = 'UNICO',
    revisado_por = validado_por,
    revisado_em = validado_em
WHERE envio_id IS NULL OR parte IS NULL;

ALTER TABLE documento_usuario
  ALTER COLUMN envio_id SET NOT NULL,
  ALTER COLUMN parte SET NOT NULL,
  DROP CONSTRAINT documento_usuario_status_chk,
  ADD CONSTRAINT documento_usuario_status_chk
    CHECK (status IN (
      'PENDENTE', 'EM_ANALISE', 'VALIDADO', 'REJEITADO',
      'AJUSTE_SOLICITADO', 'REMOVIDO', 'EXPURGADO'
    )),
  ADD CONSTRAINT documento_usuario_parte_chk
    CHECK (parte IN ('UNICO', 'FRENTE', 'VERSO')),
  ADD CONSTRAINT documento_usuario_motivo_chk
    CHECK (motivo_moderacao IS NULL OR length(trim(motivo_moderacao)) BETWEEN 3 AND 240);

CREATE INDEX documento_usuario_envio_idx
  ON documento_usuario (envio_id, criado_em, id);
CREATE UNIQUE INDEX documento_usuario_envio_parte_uk
  ON documento_usuario (envio_id, parte)
  WHERE removido_em IS NULL AND expurgado_em IS NULL;

COMMENT ON COLUMN documento_usuario.envio_id IS
  'Agrupa PDF unico ou imagens frente/verso pertencentes a uma submissao de KYC.';
COMMENT ON COLUMN documento_usuario.parte IS
  'Parte logica do documento privado: UNICO, FRENTE ou VERSO.';
COMMENT ON COLUMN documento_usuario.motivo_moderacao IS
  'Motivo sanitizado de rejeicao ou ajuste; nunca contem o documento ou CPF.';

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000002',
  'DOCUMENTO_REVISAR',
  'Revisar documentos privados de KYC.',
  TIMESTAMPTZ '2026-07-13 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT papel, permissao.id, TIMESTAMPTZ '2026-07-13 00:00:00+00'
FROM (VALUES ('ADMIN'), ('MODERADOR')) AS papeis(papel)
CROSS JOIN permissao
WHERE permissao.codigo = 'DOCUMENTO_REVISAR'
ON CONFLICT (papel, permissao_id) DO NOTHING;
