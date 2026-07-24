-- Sexo virtual passa a ser modalidade adicional do anuncio.
-- VENDA_DE_CONTEUDO permanece como codigo editorial de filtro da Home.

ALTER TABLE anuncio
  ADD COLUMN IF NOT EXISTS atendimento_exclusivamente_virtual boolean NOT NULL DEFAULT false;

INSERT INTO anuncio_servicos (anuncio_id, servico, criado_em)
SELECT
  a.id,
  'VIDEOCHAMADA',
  coalesce(a.criado_em, now())
FROM anuncio a
WHERE a.categoria = 'VENDA_DE_CONTEUDO'
ON CONFLICT (anuncio_id, servico) DO NOTHING;

UPDATE anuncio
SET categoria = 'ACOMPANHANTE_FEMININA'
WHERE categoria = 'VENDA_DE_CONTEUDO';

UPDATE documento_busca_anuncio
SET categoria = 'ACOMPANHANTE_FEMININA'
WHERE categoria = 'VENDA_DE_CONTEUDO';

CREATE INDEX IF NOT EXISTS anuncio_sexo_virtual_catalogo_idx
  ON anuncio (atendimento_exclusivamente_virtual, status, status_moderacao)
  WHERE removido_em IS NULL;

COMMENT ON COLUMN anuncio.atendimento_exclusivamente_virtual IS
  'Verdadeiro somente quando VIDEOCHAMADA e a unica modalidade; localidade permanece preservada, mas nao participa dos recortes presenciais.';
