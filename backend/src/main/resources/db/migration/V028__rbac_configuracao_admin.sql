-- Cataloga a permissao de configuracao administrativa e a vincula somente ao papel ADMIN.

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000006',
  'ADMIN_CONFIGURAR',
  'Configurar recursos administrativos da plataforma.',
  TIMESTAMPTZ '2026-07-16 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT
  'ADMIN',
  permissao.id,
  TIMESTAMPTZ '2026-07-16 00:00:00+00'
FROM permissao
WHERE permissao.codigo = 'ADMIN_CONFIGURAR'
ON CONFLICT (papel, permissao_id) DO NOTHING;
