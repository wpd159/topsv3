-- Cataloga as permissoes da moderacao canonica e as vincula somente aos papeis administrativos autorizados.

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES
  (
    'f4000000-0000-4000-8000-000000000007',
    'ANUNCIO_LER',
    'Consultar anuncios na moderacao administrativa.',
    TIMESTAMPTZ '2026-07-22 00:00:00+00'
  ),
  (
    'f4000000-0000-4000-8000-000000000008',
    'ANUNCIO_MODERAR',
    'Decidir a moderacao administrativa de anuncios.',
    TIMESTAMPTZ '2026-07-22 00:00:00+00'
  )
ON CONFLICT (codigo) DO UPDATE
SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT
  papeis.papel,
  permissao.id,
  TIMESTAMPTZ '2026-07-22 00:00:00+00'
FROM (VALUES ('ADMIN'), ('MODERADOR')) AS papeis(papel)
CROSS JOIN permissao
WHERE permissao.codigo IN ('ANUNCIO_LER', 'ANUNCIO_MODERAR')
ON CONFLICT (papel, permissao_id) DO NOTHING;
