-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Constraints finais que dependem de objetos criados em migrations anteriores.

ALTER TABLE anuncio_localizacao
  ADD CONSTRAINT anuncio_localizacao_anuncio_fk
  FOREIGN KEY (anuncio_id) REFERENCES anuncio (id);

ALTER TABLE anuncio_localizacao
  ADD CONSTRAINT anuncio_localizacao_bairro_cidade_chk
  CHECK (bairro_id IS NULL OR cidade_id IS NOT NULL);

ALTER TABLE anuncio_localizacao
  ADD CONSTRAINT anuncio_localizacao_cidade_estado_fk
  FOREIGN KEY (cidade_id, estado_id) REFERENCES cidade (id, estado_id);

ALTER TABLE anuncio_localizacao
  ADD CONSTRAINT anuncio_localizacao_bairro_cidade_fk
  FOREIGN KEY (bairro_id, cidade_id) REFERENCES bairro (id, cidade_id);

ALTER TABLE documento_busca_anuncio
  ADD CONSTRAINT documento_busca_anuncio_bairro_cidade_chk
  CHECK (bairro_id IS NULL OR cidade_id IS NOT NULL);

ALTER TABLE documento_busca_anuncio
  ADD CONSTRAINT documento_busca_anuncio_cidade_estado_fk
  FOREIGN KEY (cidade_id, estado_id) REFERENCES cidade (id, estado_id);

ALTER TABLE documento_busca_anuncio
  ADD CONSTRAINT documento_busca_anuncio_bairro_cidade_fk
  FOREIGN KEY (bairro_id, cidade_id) REFERENCES bairro (id, cidade_id);

ALTER TABLE pagamento_conciliacao
  ADD CONSTRAINT pagamento_conciliacao_movimento_obrigatorio_chk
  CHECK (status <> 'CONCILIADO' OR movimento_credito_id IS NOT NULL);

ALTER TABLE seo_redirect
  ADD CONSTRAINT seo_redirect_sem_rota_alternativa_chk
  CHECK (
    destino_caminho !~ '^/(anuncio|perfil|acompanhante|ads)(/|$)'
  );

ALTER TABLE story_anuncio
  ADD CONSTRAINT story_anuncio_inicio_obrigatorio_chk
  CHECK (inicio_em IS NOT NULL);

COMMENT ON CONSTRAINT anuncio_localizacao_anuncio_fk ON anuncio_localizacao IS 'FK final para anuncio apos criacao da tabela anuncio.';
COMMENT ON CONSTRAINT anuncio_localizacao_cidade_estado_fk ON anuncio_localizacao IS 'Garante que cidade pertence ao estado informado.';
COMMENT ON CONSTRAINT anuncio_localizacao_bairro_cidade_fk ON anuncio_localizacao IS 'Garante que bairro pertence a cidade informada.';
COMMENT ON CONSTRAINT documento_busca_anuncio_cidade_estado_fk ON documento_busca_anuncio IS 'Garante consistencia de estado/cidade na projecao de busca.';
COMMENT ON CONSTRAINT documento_busca_anuncio_bairro_cidade_fk ON documento_busca_anuncio IS 'Garante consistencia de cidade/bairro na projecao de busca.';
COMMENT ON CONSTRAINT seo_redirect_sem_rota_alternativa_chk ON seo_redirect IS 'Redirect nao pode consolidar rota alternativa de anuncio como destino canonico.';
