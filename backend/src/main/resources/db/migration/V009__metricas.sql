-- Tops do Job V3 - Fase 1D
-- Status: AGUARDANDO_REVISAO_PRO.
-- Metricas preservaveis/migraveis com minimizacao de dados.

CREATE TABLE evento_visualizacao (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  visitante_hash text,
  ip_hash text,
  user_agent_hash text,
  referer_hash text,
  origem_pais char(2),
  origem_uf char(2),
  origem_cidade text,
  dispositivo text,
  request_id text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT evento_visualizacao_dispositivo_chk CHECK (dispositivo IS NULL OR dispositivo IN ('DESKTOP', 'MOBILE', 'TABLET', 'BOT', 'DESCONHECIDO')),
  CONSTRAINT evento_visualizacao_origem_pais_chk CHECK (origem_pais IS NULL OR origem_pais ~ '^[A-Z]{2}$'),
  CONSTRAINT evento_visualizacao_origem_uf_chk CHECK (origem_uf IS NULL OR origem_uf ~ '^[A-Z]{2}$')
);

CREATE INDEX evento_visualizacao_anuncio_data_idx ON evento_visualizacao (anuncio_id, criado_em);
CREATE INDEX evento_visualizacao_origem_idx ON evento_visualizacao (origem_uf, origem_cidade, criado_em);
CREATE INDEX evento_visualizacao_visitante_idx ON evento_visualizacao (visitante_hash, criado_em) WHERE visitante_hash IS NOT NULL;

COMMENT ON TABLE evento_visualizacao IS 'Eventos de visualizacao de anuncio com IP e user-agent minimizados por hash.';

CREATE TABLE agregado_visualizacao_diaria (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  data_referencia date NOT NULL,
  origem_uf char(2),
  origem_cidade text,
  origem_uf_chave text NOT NULL DEFAULT 'DESCONHECIDA',
  origem_cidade_chave text NOT NULL DEFAULT 'DESCONHECIDA',
  total_visualizacoes bigint NOT NULL DEFAULT 0,
  visitantes_estimados bigint NOT NULL DEFAULT 0,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT agregado_visualizacao_origem_uf_chave_chk CHECK (origem_uf_chave = 'DESCONHECIDA' OR origem_uf_chave ~ '^[A-Z]{2}$'),
  CONSTRAINT agregado_visualizacao_origem_cidade_chave_chk CHECK (length(origem_cidade_chave) > 0),
  CONSTRAINT agregado_visualizacao_total_chk CHECK (total_visualizacoes >= 0),
  CONSTRAINT agregado_visualizacao_visitantes_chk CHECK (visitantes_estimados >= 0)
);

CREATE UNIQUE INDEX agregado_visualizacao_diaria_uk ON agregado_visualizacao_diaria (anuncio_id, data_referencia, origem_uf_chave, origem_cidade_chave);
CREATE INDEX agregado_visualizacao_data_idx ON agregado_visualizacao_diaria (data_referencia, total_visualizacoes DESC);

COMMENT ON TABLE agregado_visualizacao_diaria IS 'Agregado diario para desempenho por anuncio; origem desconhecida usa chaves normalizadas sem exigir origem nula em PK.';

CREATE TABLE clique_whatsapp (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  visitante_hash text,
  ip_hash text,
  user_agent_hash text,
  origem_pais char(2),
  origem_uf char(2),
  origem_cidade text,
  dispositivo text,
  permitido boolean NOT NULL DEFAULT true,
  motivo_bloqueio text,
  request_id text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT clique_whatsapp_dispositivo_chk CHECK (dispositivo IS NULL OR dispositivo IN ('DESKTOP', 'MOBILE', 'TABLET', 'BOT', 'DESCONHECIDO')),
  CONSTRAINT clique_whatsapp_origem_pais_chk CHECK (origem_pais IS NULL OR origem_pais ~ '^[A-Z]{2}$'),
  CONSTRAINT clique_whatsapp_origem_uf_chk CHECK (origem_uf IS NULL OR origem_uf ~ '^[A-Z]{2}$')
);

CREATE INDEX clique_whatsapp_anuncio_data_idx ON clique_whatsapp (anuncio_id, criado_em);
CREATE INDEX clique_whatsapp_origem_idx ON clique_whatsapp (origem_uf, origem_cidade, criado_em);
CREATE INDEX clique_whatsapp_visitante_idx ON clique_whatsapp (visitante_hash, criado_em) WHERE visitante_hash IS NOT NULL;

COMMENT ON TABLE clique_whatsapp IS 'Cliques no WhatsApp preservaveis como prova de resultado; sem limite comercial diario.';

CREATE TABLE agregado_clique_whatsapp_diario (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  data_referencia date NOT NULL,
  origem_uf char(2),
  origem_cidade text,
  origem_uf_chave text NOT NULL DEFAULT 'DESCONHECIDA',
  origem_cidade_chave text NOT NULL DEFAULT 'DESCONHECIDA',
  total_cliques bigint NOT NULL DEFAULT 0,
  visitantes_estimados bigint NOT NULL DEFAULT 0,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT agregado_clique_origem_uf_chave_chk CHECK (origem_uf_chave = 'DESCONHECIDA' OR origem_uf_chave ~ '^[A-Z]{2}$'),
  CONSTRAINT agregado_clique_origem_cidade_chave_chk CHECK (length(origem_cidade_chave) > 0),
  CONSTRAINT agregado_clique_total_chk CHECK (total_cliques >= 0),
  CONSTRAINT agregado_clique_visitantes_chk CHECK (visitantes_estimados >= 0)
);

CREATE UNIQUE INDEX agregado_clique_whatsapp_diaria_uk ON agregado_clique_whatsapp_diario (anuncio_id, data_referencia, origem_uf_chave, origem_cidade_chave);
CREATE INDEX agregado_clique_data_idx ON agregado_clique_whatsapp_diario (data_referencia, total_cliques DESC);

COMMENT ON TABLE agregado_clique_whatsapp_diario IS 'Agregado diario de cliques WhatsApp; origem desconhecida usa chaves normalizadas sem exigir origem nula em PK.';

CREATE TABLE evento_verificacao_etaria (
  id uuid PRIMARY KEY,
  usuario_id uuid REFERENCES usuario (id),
  anuncio_id uuid REFERENCES anuncio (id),
  resultado text NOT NULL,
  metodo text NOT NULL,
  ip_hash text,
  user_agent_hash text,
  request_id text,
  criado_em timestamptz NOT NULL,
  CONSTRAINT evento_verificacao_etaria_resultado_chk CHECK (resultado IN ('PERMITIDO', 'NEGADO', 'INDETERMINADO')),
  CONSTRAINT evento_verificacao_etaria_metodo_chk CHECK (metodo IN ('DECLARACAO', 'DOCUMENTO', 'STAFF', 'IMPORTACAO'))
);

CREATE INDEX evento_verificacao_etaria_usuario_idx ON evento_verificacao_etaria (usuario_id, criado_em);
CREATE INDEX evento_verificacao_etaria_anuncio_idx ON evento_verificacao_etaria (anuncio_id, criado_em);

COMMENT ON TABLE evento_verificacao_etaria IS 'Registro minimizado de verificacao etaria para age gate e auditoria futura.';
