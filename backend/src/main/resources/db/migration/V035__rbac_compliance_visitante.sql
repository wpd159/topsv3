-- Cataloga a permissao de seguranca e a vincula somente ao papel ADMIN.

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000009',
  'SEGURANCA_GERENCIAR',
  'Gerenciar seguranca e compliance administrativos.',
  TIMESTAMPTZ '2026-07-27 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT
  'ADMIN',
  permissao.id,
  TIMESTAMPTZ '2026-07-27 00:00:00+00'
FROM permissao
WHERE permissao.codigo = 'SEGURANCA_GERENCIAR'
ON CONFLICT (papel, permissao_id) DO NOTHING;
