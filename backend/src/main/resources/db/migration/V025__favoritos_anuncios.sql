-- Favoritos publicos V3: relacao idempotente entre a sessao publica e o anuncio.

CREATE TABLE favorito_anuncio (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id) ON DELETE CASCADE,
  criado_em timestamptz NOT NULL,
  CONSTRAINT favorito_anuncio_usuario_anuncio_uk UNIQUE (usuario_id, anuncio_id)
);

CREATE INDEX favorito_anuncio_usuario_criado_idx
  ON favorito_anuncio (usuario_id, criado_em DESC);

CREATE INDEX favorito_anuncio_anuncio_idx
  ON favorito_anuncio (anuncio_id);

COMMENT ON TABLE favorito_anuncio IS
  'Favoritos do usuario autenticado; unicidade impede duplicidade por usuario e anuncio.';
