-- Cataloga a permissao granular de revisao de midia e a vincula ao papel ADMIN.

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000001',
  'MIDIA_REVISAR',
  'Revisar e classificar midias de anuncios.',
  TIMESTAMPTZ '2026-07-13 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT
  'ADMIN',
  permissao.id,
  TIMESTAMPTZ '2026-07-13 00:00:00+00'
FROM permissao
WHERE permissao.codigo = 'MIDIA_REVISAR'
ON CONFLICT (papel, permissao_id) DO NOTHING;
