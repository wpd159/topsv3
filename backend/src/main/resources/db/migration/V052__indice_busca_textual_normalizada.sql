-- Suporta a busca publica por substring normalizada na projecao canonica.
CREATE INDEX documento_busca_anuncio_texto_normalizado_trgm_idx
  ON documento_busca_anuncio
  USING gin ((lower(translate(
    coalesce(texto_busca, ''),
    'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
    'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'
  ))) gin_trgm_ops);

COMMENT ON INDEX documento_busca_anuncio_texto_normalizado_trgm_idx IS
  'Indice trigram da expressao usada pela busca publica literal e sem acentos.';
