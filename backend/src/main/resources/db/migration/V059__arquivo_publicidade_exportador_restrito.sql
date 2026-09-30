-- Papel tecnico aditivo: nao concede ADMIN, LER ou acesso a qualquer conta.
-- A vinculacao a um responsavel exige uma operacao separada, auditada e autorizada.
ALTER TABLE papel_usuario DROP CONSTRAINT papel_usuario_papel_chk;
ALTER TABLE papel_usuario ADD CONSTRAINT papel_usuario_papel_chk
  CHECK (papel IN ('ADMIN', 'MODERADOR', 'COMERCIAL', 'USUARIO', 'ARQUIVO_EXPORTADOR'));

ALTER TABLE papel_permissao DROP CONSTRAINT papel_permissao_papel_chk;
ALTER TABLE papel_permissao ADD CONSTRAINT papel_permissao_papel_chk
  CHECK (papel IN ('ADMIN', 'MODERADOR', 'COMERCIAL', 'USUARIO', 'ARQUIVO_EXPORTADOR'));

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT 'ARQUIVO_EXPORTADOR', id, TIMESTAMPTZ '2026-09-30 00:00:00+00'
FROM permissao
WHERE codigo = 'ARQUIVO_PUBLICIDADE_EXPORTAR'
ON CONFLICT (papel, permissao_id) DO NOTHING;

COMMENT ON CONSTRAINT papel_usuario_papel_chk ON papel_usuario IS
  'ARQUIVO_EXPORTADOR e um papel tecnico restrito; nao substitui ADMIN e nao e concedido pela migration.';
