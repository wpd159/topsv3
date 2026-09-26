-- Arquivo privado prospectivo de Stories com direito MIDIA. Nao reconstrui legado.
-- O limite finito e imposto pela propria janela publica do Story, sem depender de job.

CREATE TABLE arquivo_publicidade_story_veiculacao (
  id uuid PRIMARY KEY,
  story_id uuid NOT NULL REFERENCES story_anuncio (id),
  anuncio_id uuid REFERENCES anuncio (id),
  contratante_usuario_id uuid NOT NULL REFERENCES usuario (id),
  ativacao_beneficio_id uuid NOT NULL REFERENCES ativacao_beneficio (id),
  grupo_ativacao_id uuid REFERENCES grupo_ativacao_beneficio (id),
  movimento_credito_id uuid REFERENCES movimento_credito (id),
  pagamento_id uuid REFERENCES pagamento (id),
  modo_conteudo text NOT NULL,
  classificacao text NOT NULL,
  relacao_material text NOT NULL,
  cobertura text NOT NULL,
  inicio_em timestamptz NOT NULL,
  fim_em timestamptz NOT NULL,
  retencao_ate timestamptz NOT NULL,
  encerramento_motivo text NOT NULL,
  capturado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT arquivo_publicidade_story_veiculacao_story_uk UNIQUE (story_id),
  CONSTRAINT arquivo_publicidade_story_modo_chk CHECK (modo_conteudo IN ('ANUNCIO', 'MIDIA_UPLOAD')),
  CONSTRAINT arquivo_publicidade_story_classificacao_chk CHECK (
    classificacao IN ('REMUNERADA', 'PROMOCIONAL', 'ADMINISTRATIVA', 'ORIGEM_INDETERMINADA')),
  CONSTRAINT arquivo_publicidade_story_relacao_chk CHECK (relacao_material IN ('SIM', 'NAO', 'DESCONHECIDA')),
  CONSTRAINT arquivo_publicidade_story_cobertura_chk CHECK (cobertura IN ('ABRANGIDA', 'PREVENTIVA')),
  CONSTRAINT arquivo_publicidade_story_janela_chk CHECK (fim_em >= inicio_em),
  CONSTRAINT arquivo_publicidade_story_retencao_chk CHECK (retencao_ate >= fim_em + INTERVAL '1 year'),
  CONSTRAINT arquivo_publicidade_story_anuncio_modo_chk CHECK (
    (modo_conteudo = 'ANUNCIO' AND anuncio_id IS NOT NULL)
    OR modo_conteudo = 'MIDIA_UPLOAD')
);
CREATE INDEX arquivo_publicidade_story_usuario_periodo_idx
  ON arquivo_publicidade_story_veiculacao (contratante_usuario_id, inicio_em DESC);
CREATE INDEX arquivo_publicidade_story_anuncio_periodo_idx
  ON arquivo_publicidade_story_veiculacao (anuncio_id, inicio_em DESC)
  WHERE anuncio_id IS NOT NULL;
CREATE INDEX arquivo_publicidade_story_retencao_idx
  ON arquivo_publicidade_story_veiculacao (retencao_ate);

CREATE TABLE arquivo_publicidade_story_versao (
  id uuid PRIMARY KEY,
  veiculacao_id uuid NOT NULL REFERENCES arquivo_publicidade_story_veiculacao (id),
  numero integer NOT NULL,
  vigente_desde timestamptz NOT NULL,
  vigente_ate timestamptz NOT NULL,
  capturado_em timestamptz NOT NULL,
  motivo text NOT NULL,
  request_id text,
  conteudo_json jsonb NOT NULL,
  contratante_json jsonb NOT NULL,
  comercial_json jsonb NOT NULL,
  segmentacao_json jsonb NOT NULL,
  alcance_json jsonb NOT NULL,
  conteudo_sha256 text NOT NULL,
  CONSTRAINT arquivo_publicidade_story_versao_numero_uk UNIQUE (veiculacao_id, numero),
  CONSTRAINT arquivo_publicidade_story_versao_numero_chk CHECK (numero > 0),
  CONSTRAINT arquivo_publicidade_story_versao_janela_chk CHECK (vigente_ate >= vigente_desde),
  CONSTRAINT arquivo_publicidade_story_versao_hash_chk CHECK (conteudo_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX arquivo_publicidade_story_versao_periodo_idx
  ON arquivo_publicidade_story_versao (veiculacao_id, vigente_desde DESC);

CREATE TABLE arquivo_publicidade_story_midia (
  id uuid PRIMARY KEY,
  versao_id uuid NOT NULL REFERENCES arquivo_publicidade_story_versao (id),
  anuncio_midia_id uuid REFERENCES anuncio_midia (id),
  arquivo_midia_id uuid NOT NULL REFERENCES arquivo_midia (id),
  variante text NOT NULL,
  storage_provider text NOT NULL,
  bucket text NOT NULL,
  chave_privada text NOT NULL,
  sha256 text NOT NULL,
  mime_type text NOT NULL,
  tamanho_bytes bigint NOT NULL,
  ordem integer NOT NULL,
  CONSTRAINT arquivo_publicidade_story_midia_variante_chk CHECK (variante IN ('ORIGINAL', 'PREVIEW_RESTRITO')),
  CONSTRAINT arquivo_publicidade_story_midia_hash_chk CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT arquivo_publicidade_story_midia_tamanho_chk CHECK (tamanho_bytes > 0),
  CONSTRAINT arquivo_publicidade_story_midia_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT arquivo_publicidade_story_midia_unica_uk UNIQUE (versao_id, arquivo_midia_id, variante, ordem),
  CONSTRAINT arquivo_publicidade_story_midia_storage_uk UNIQUE (storage_provider, bucket, chave_privada)
);
CREATE INDEX arquivo_publicidade_story_midia_versao_idx
  ON arquivo_publicidade_story_midia (versao_id, ordem);

CREATE TABLE arquivo_publicidade_story_hold (
  id uuid PRIMARY KEY,
  veiculacao_id uuid NOT NULL REFERENCES arquivo_publicidade_story_veiculacao (id),
  fundamento text NOT NULL,
  responsavel_usuario_id uuid NOT NULL REFERENCES usuario (id),
  inicio_em timestamptz NOT NULL,
  revisar_em timestamptz NOT NULL,
  encerrado_em timestamptz,
  CONSTRAINT arquivo_publicidade_story_hold_janela_chk CHECK (
    revisar_em > inicio_em AND (encerrado_em IS NULL OR encerrado_em >= inicio_em))
);
CREATE INDEX arquivo_publicidade_story_hold_ativo_idx
  ON arquivo_publicidade_story_hold (veiculacao_id, revisar_em) WHERE encerrado_em IS NULL;

COMMENT ON TABLE arquivo_publicidade_story_veiculacao IS
  'Uma janela privada prospectiva por Story efetivamente exibivel; fim contratual finito e guarda minima individual.';
COMMENT ON TABLE arquivo_publicidade_story_versao IS
  'Versao imutavel do Story; conteudo exibido e identidade historica, sem documentos de KYC.';
COMMENT ON TABLE arquivo_publicidade_story_midia IS
  'Copia privada dos bytes exibidos; independente da limpeza operacional de Story.';
