-- Visibilidade individual de midia; substitui classificacao etaria global.

ALTER TABLE anuncio_midia
  ADD COLUMN visibilidade_midia text;

UPDATE anuncio
SET status_moderacao = 'REJEITADO'
WHERE status_moderacao = 'BLOQUEADO';

ALTER TABLE anuncio DROP CONSTRAINT anuncio_status_moderacao_chk;
ALTER TABLE anuncio
  ADD CONSTRAINT anuncio_status_moderacao_chk
    CHECK (status_moderacao IN ('NAO_ENVIADO', 'PENDENTE', 'APROVADO', 'REJEITADO'));

ALTER TABLE anuncio_midia DROP CONSTRAINT anuncio_midia_status_chk;
ALTER TABLE anuncio_midia
  ADD CONSTRAINT anuncio_midia_status_chk
    CHECK (status IN ('PENDENTE', 'AJUSTE_SOLICITADO', 'PUBLICAVEL', 'REJEITADA', 'REMOVIDA'));

UPDATE anuncio_midia am
SET visibilidade_midia = CASE
  WHEN am.tipo IN ('VIDEO', 'STORY') THEN 'RESTRITA_18'
  WHEN am.status = 'PENDENTE' THEN NULL
  WHEN am.classificacao_conteudo = 'LIVRE'
    AND COALESCE(a.classificacao_conteudo, 'BLOQUEADO') = 'LIVRE'
    AND COALESCE(ar.classificacao_conteudo, 'BLOQUEADO') = 'LIVRE'
    THEN 'LIVRE'
  ELSE 'RESTRITA_18'
END
FROM anuncio a, arquivo_midia ar
WHERE a.id = am.anuncio_id
  AND ar.id = am.arquivo_midia_id;

-- Vinculos sem referencias validas nao sao publicados e permanecem conservadores.
UPDATE anuncio_midia
SET visibilidade_midia = CASE
  WHEN tipo IN ('VIDEO', 'STORY') THEN 'RESTRITA_18'
  WHEN status = 'PENDENTE' THEN NULL
  ELSE 'RESTRITA_18'
END
WHERE visibilidade_midia IS NULL
  AND status <> 'PENDENTE';

ALTER TABLE anuncio_midia
  ADD CONSTRAINT anuncio_midia_visibilidade_chk
    CHECK (visibilidade_midia IS NULL OR visibilidade_midia IN ('LIVRE', 'RESTRITA_18')),
  ADD CONSTRAINT anuncio_midia_visibilidade_tipo_chk
    CHECK (tipo = 'FOTO' OR visibilidade_midia = 'RESTRITA_18'),
  ADD CONSTRAINT anuncio_midia_publicavel_visibilidade_chk
    CHECK (status <> 'PUBLICAVEL' OR visibilidade_midia IS NOT NULL);

CREATE INDEX anuncio_midia_visibilidade_idx
  ON anuncio_midia (visibilidade_midia, status, tipo);

COMMENT ON COLUMN anuncio_midia.visibilidade_midia IS
  'Visibilidade individual ligada ao ID do vinculo: FOTO aceita LIVRE/RESTRITA_18; VIDEO e STORY exigem RESTRITA_18.';

ALTER TABLE anuncio_midia DROP CONSTRAINT anuncio_midia_classificacao_chk;
ALTER TABLE anuncio_midia DROP COLUMN classificacao_conteudo;

ALTER TABLE arquivo_midia DROP CONSTRAINT arquivo_midia_classificacao_chk;
ALTER TABLE arquivo_midia DROP COLUMN classificacao_conteudo;

ALTER TABLE anuncio DROP CONSTRAINT anuncio_classificacao_conteudo_chk;
ALTER TABLE anuncio DROP COLUMN classificacao_conteudo;
