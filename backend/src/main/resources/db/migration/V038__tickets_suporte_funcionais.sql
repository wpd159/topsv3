-- Completa o contrato de tickets sobre as tabelas canonicas criadas na V012.

ALTER TABLE ticket_suporte
  ADD COLUMN IF NOT EXISTS categoria text,
  ADD COLUMN IF NOT EXISTS criacao_idempotency_key varchar(160),
  ADD COLUMN IF NOT EXISTS criado_request_id varchar(128);

UPDATE ticket_suporte
SET categoria = 'OUTROS'
WHERE categoria IS NULL;

ALTER TABLE ticket_suporte
  ALTER COLUMN categoria SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ticket_suporte_categoria_chk'
  ) THEN
    ALTER TABLE ticket_suporte
      ADD CONSTRAINT ticket_suporte_categoria_chk
      CHECK (categoria IN (
        'ERRO_NO_SISTEMA',
        'PROBLEMAS_COM_PAGAMENTO',
        'ACESSO_CONTA',
        'OUTROS'
      ));
  END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ticket_suporte_criacao_idempotencia_uk
  ON ticket_suporte (usuario_id, criacao_idempotency_key)
  WHERE usuario_id IS NOT NULL AND criacao_idempotency_key IS NOT NULL;

ALTER TABLE mensagem_suporte
  ADD COLUMN IF NOT EXISTS idempotency_key varchar(160),
  ADD COLUMN IF NOT EXISTS request_id varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS mensagem_suporte_autor_idempotencia_uk
  ON mensagem_suporte (autor_usuario_id, idempotency_key)
  WHERE autor_usuario_id IS NOT NULL AND idempotency_key IS NOT NULL;

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000010',
  'SUPORTE_ATENDER',
  'Consultar e atender tickets de suporte.',
  TIMESTAMPTZ '2026-07-27 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT
  papeis.papel,
  permissao.id,
  TIMESTAMPTZ '2026-07-27 00:00:00+00'
FROM (VALUES ('ADMIN'), ('MODERADOR')) AS papeis(papel)
CROSS JOIN permissao
WHERE permissao.codigo = 'SUPORTE_ATENDER'
ON CONFLICT (papel, permissao_id) DO NOTHING;
