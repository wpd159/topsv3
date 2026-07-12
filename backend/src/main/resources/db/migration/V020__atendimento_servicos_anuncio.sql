-- Atendimento e servicos estruturados do anuncio.
-- Preserva os valores canonicos do modelo vigente sem inferencia por texto livre.

CREATE TABLE anuncio_local_atendimento (
  anuncio_id uuid NOT NULL REFERENCES anuncio (id) ON DELETE CASCADE,
  local_atendimento text NOT NULL,
  criado_em timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT anuncio_local_atendimento_pk PRIMARY KEY (anuncio_id, local_atendimento),
  CONSTRAINT anuncio_local_atendimento_valor_chk CHECK (
    local_atendimento IN ('A_COMBINAR', 'HOTEL_MOTEL', 'MEU_LOCAL')
  )
);

CREATE TABLE anuncio_servicos (
  anuncio_id uuid NOT NULL REFERENCES anuncio (id) ON DELETE CASCADE,
  servico text NOT NULL,
  criado_em timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT anuncio_servicos_pk PRIMARY KEY (anuncio_id, servico),
  CONSTRAINT anuncio_servicos_valor_chk CHECK (
    servico IN (
      'ANAL',
      'ATRIZ_PORNO',
      'FETICHES',
      'MASSAGEM_TANTRICA',
      'ATIVO',
      'BDSM',
      'JOGOS_DE_INTERPRETACAO',
      'ORAL',
      'ATOR_PORNO',
      'EJACULACAO_CORPORAL',
      'MASSAGEM_EROTICA',
      'PASSIVO',
      'NAMORADAS',
      'TRIO',
      'VIDEOCHAMADA'
    )
  )
);

COMMENT ON TABLE anuncio_local_atendimento IS
  'Locais de atendimento estruturados do anuncio; MEU_LOCAL origina o selo publico Com local.';
COMMENT ON TABLE anuncio_servicos IS
  'Servicos estruturados do anuncio; ANAL origina o selo publico Faz anal.';
