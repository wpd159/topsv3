-- Vincula cada card da Home a uma categoria canonica e permite imagem gerenciada no R2.

DROP INDEX categoria_home_categoria_enum_uk;

UPDATE categoria_home
SET categoria_enum = 'VENDA_DE_CONTEUDO',
    destino = '/anuncios?categoria=VENDA_DE_CONTEUDO',
    ativo = false
WHERE id = 'f2000000-0000-4000-8000-000000000006'
  AND categoria_enum = 'ENCONTROS_CASUAIS';

ALTER TABLE categoria_home
  DROP CONSTRAINT categoria_home_categoria_enum_chk,
  DROP CONSTRAINT categoria_home_destino_chk,
  DROP CONSTRAINT categoria_home_imagem_publica_chk;

ALTER TABLE categoria_home
  ADD COLUMN imagem_object_key text,
  ALTER COLUMN imagem_publica_url DROP NOT NULL;

UPDATE categoria_home
SET destino = '/anuncios?categoria=' || categoria_enum;

ALTER TABLE categoria_home
  ADD CONSTRAINT categoria_home_categoria_enum_chk CHECK (
    categoria_enum IN (
      'ACOMPANHANTE_FEMININA',
      'ACOMPANHANTE_MASCULINO',
      'TRANSEX_TRAVESTIS',
      'MASSAGENS',
      'VENDA_DE_CONTEUDO'
    )
  ),
  ADD CONSTRAINT categoria_home_destino_canonico_chk CHECK (
    destino = '/anuncios?categoria=' || categoria_enum
  ),
  ADD CONSTRAINT categoria_home_imagem_origem_chk CHECK (
    (
      imagem_publica_url IS NOT NULL
      AND imagem_object_key IS NULL
      AND imagem_publica_url ~ '^/[a-zA-Z0-9/._-]+$'
      AND imagem_publica_url NOT LIKE '//%'
    )
    OR
    (
      imagem_publica_url IS NULL
      AND imagem_object_key IS NOT NULL
      AND imagem_object_key ~ '^[a-zA-Z0-9/._-]+$'
      AND imagem_object_key NOT LIKE '/%'
      AND imagem_object_key NOT LIKE '%..%'
    )
  );

CREATE UNIQUE INDEX categoria_home_categoria_ativa_uk
  ON categoria_home (categoria_enum)
  WHERE ativo;

COMMENT ON COLUMN categoria_home.categoria_enum IS
  'Codigo obrigatorio da taxonomia canonica escolhida no wizard do anuncio.';
COMMENT ON COLUMN categoria_home.destino IS
  'Destino derivado da categoria canonica; nunca informado livremente pelo frontend.';
COMMENT ON COLUMN categoria_home.imagem_object_key IS
  'Chave interna da imagem aprovada no ObjectStorage; nunca exposta em DTO.';
