-- Arquivo privado prospectivo das veiculacoes. Nenhum acervo anterior e migrado.
-- Sem beneficio/relação material demonstrada, as trilhas operacionais existentes
-- seguem como fonte de consulta; nao se abre arquivo pessoal gratuito por padrao.

CREATE TABLE arquivo_publicidade_veiculacao (
  id uuid PRIMARY KEY,
  anuncio_id uuid NOT NULL REFERENCES anuncio (id),
  contratante_usuario_id uuid NOT NULL REFERENCES usuario (id),
  ativacao_beneficio_id uuid REFERENCES ativacao_beneficio (id),
  grupo_ativacao_id uuid REFERENCES grupo_ativacao_beneficio (id),
  movimento_credito_id uuid REFERENCES movimento_credito (id),
  pagamento_id uuid REFERENCES pagamento (id),
  classificacao text NOT NULL,
  relacao_material text NOT NULL,
  cobertura text NOT NULL,
  inicio_em timestamptz NOT NULL,
  fim_em timestamptz,
  retencao_ate timestamptz,
  encerramento_motivo text,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  CONSTRAINT arquivo_publicidade_classificacao_chk CHECK (
    classificacao IN ('GRATUITA', 'REMUNERADA', 'PROMOCIONAL', 'ADMINISTRATIVA', 'ORIGEM_INDETERMINADA')
  ),
  CONSTRAINT arquivo_publicidade_relacao_chk CHECK (relacao_material IN ('SIM', 'NAO', 'DESCONHECIDA')),
  CONSTRAINT arquivo_publicidade_cobertura_chk CHECK (
    cobertura IN ('ABRANGIDA', 'PREVENTIVA', 'NAO_ABRANGIDA', 'PENDENTE')
  ),
  CONSTRAINT arquivo_publicidade_janela_chk CHECK (fim_em IS NULL OR fim_em >= inicio_em),
  CONSTRAINT arquivo_publicidade_retencao_chk CHECK (
    (fim_em IS NULL AND retencao_ate IS NULL AND encerramento_motivo IS NULL)
    OR (fim_em IS NOT NULL AND encerramento_motivo IS NOT NULL AND (
      (cobertura IN ('ABRANGIDA', 'PREVENTIVA') AND retencao_ate IS NOT NULL
        AND retencao_ate >= fim_em + INTERVAL '1 year')
      OR (cobertura IN ('NAO_ABRANGIDA', 'PENDENTE') AND retencao_ate IS NULL)
    ))
  )
);

-- Beneficios tem fim logico maximo conhecido na captura; a mesma ativacao pode
-- abrir outra janela apos uma pausa, sempre com novo inicio_em.
CREATE UNIQUE INDEX arquivo_publicidade_beneficio_inicio_uk
  ON arquivo_publicidade_veiculacao (ativacao_beneficio_id, inicio_em)
  WHERE ativacao_beneficio_id IS NOT NULL;
CREATE INDEX arquivo_publicidade_anuncio_periodo_idx
  ON arquivo_publicidade_veiculacao (anuncio_id, inicio_em DESC);
CREATE INDEX arquivo_publicidade_usuario_periodo_idx
  ON arquivo_publicidade_veiculacao (contratante_usuario_id, inicio_em DESC);
CREATE INDEX arquivo_publicidade_retencao_idx
  ON arquivo_publicidade_veiculacao (retencao_ate) WHERE retencao_ate IS NOT NULL;

CREATE TABLE arquivo_publicidade_versao (
  id uuid PRIMARY KEY,
  veiculacao_id uuid NOT NULL REFERENCES arquivo_publicidade_veiculacao (id),
  numero integer NOT NULL,
  vigente_desde timestamptz NOT NULL,
  vigente_ate timestamptz,
  capturado_em timestamptz NOT NULL,
  motivo text NOT NULL,
  request_id text,
  conteudo_json jsonb NOT NULL,
  contratante_json jsonb NOT NULL,
  comercial_json jsonb NOT NULL,
  segmentacao_json jsonb NOT NULL,
  alcance_json jsonb NOT NULL,
  conteudo_sha256 text NOT NULL,
  CONSTRAINT arquivo_publicidade_versao_numero_chk CHECK (numero > 0),
  CONSTRAINT arquivo_publicidade_versao_janela_chk CHECK (vigente_ate IS NULL OR vigente_ate >= vigente_desde),
  CONSTRAINT arquivo_publicidade_versao_hash_chk CHECK (conteudo_sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT arquivo_publicidade_versao_numero_uk UNIQUE (veiculacao_id, numero)
);

CREATE INDEX arquivo_publicidade_versao_periodo_idx
  ON arquivo_publicidade_versao (veiculacao_id, vigente_desde DESC);

CREATE TABLE arquivo_publicidade_midia (
  id uuid PRIMARY KEY,
  versao_id uuid NOT NULL REFERENCES arquivo_publicidade_versao (id),
  anuncio_midia_id uuid NOT NULL REFERENCES anuncio_midia (id),
  arquivo_midia_id uuid NOT NULL REFERENCES arquivo_midia (id),
  variante text NOT NULL,
  storage_provider text NOT NULL,
  bucket text NOT NULL,
  chave_privada text NOT NULL,
  sha256 text NOT NULL,
  mime_type text NOT NULL,
  tamanho_bytes bigint NOT NULL,
  ordem integer NOT NULL,
  CONSTRAINT arquivo_publicidade_midia_variante_chk CHECK (variante IN ('ORIGINAL', 'PREVIEW_RESTRITO')),
  CONSTRAINT arquivo_publicidade_midia_hash_chk CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT arquivo_publicidade_midia_tamanho_chk CHECK (tamanho_bytes > 0),
  CONSTRAINT arquivo_publicidade_midia_ordem_chk CHECK (ordem >= 0),
  CONSTRAINT arquivo_publicidade_midia_unica_uk UNIQUE (versao_id, anuncio_midia_id, variante),
  CONSTRAINT arquivo_publicidade_midia_storage_uk UNIQUE (storage_provider, bucket, chave_privada)
);

CREATE INDEX arquivo_publicidade_midia_versao_idx ON arquivo_publicidade_midia (versao_id, ordem);

CREATE TABLE arquivo_publicidade_hold (
  id uuid PRIMARY KEY,
  veiculacao_id uuid NOT NULL REFERENCES arquivo_publicidade_veiculacao (id),
  fundamento text NOT NULL,
  responsavel_usuario_id uuid NOT NULL REFERENCES usuario (id),
  inicio_em timestamptz NOT NULL,
  revisar_em timestamptz NOT NULL,
  encerrado_em timestamptz,
  CONSTRAINT arquivo_publicidade_hold_janela_chk CHECK (
    revisar_em > inicio_em AND (encerrado_em IS NULL OR encerrado_em >= inicio_em)
  )
);
CREATE INDEX arquivo_publicidade_hold_ativo_idx
  ON arquivo_publicidade_hold (veiculacao_id, revisar_em) WHERE encerrado_em IS NULL;

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000011',
  'ARQUIVO_PUBLICIDADE_LER',
  'Consultar metadados e registros do arquivo privado de publicidade.',
  TIMESTAMPTZ '2026-09-25 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE SET descricao = EXCLUDED.descricao;

INSERT INTO papel_permissao (papel, permissao_id, criado_em)
SELECT 'ADMIN', id, TIMESTAMPTZ '2026-09-25 00:00:00+00'
FROM permissao WHERE codigo = 'ARQUIVO_PUBLICIDADE_LER'
ON CONFLICT (papel, permissao_id) DO NOTHING;

INSERT INTO permissao (id, codigo, descricao, criado_em)
VALUES (
  'f4000000-0000-4000-8000-000000000012',
  'ARQUIVO_PUBLICIDADE_EXPORTAR',
  'Exportar registros e bytes privados do arquivo de publicidade.',
  TIMESTAMPTZ '2026-09-25 00:00:00+00'
)
ON CONFLICT (codigo) DO UPDATE SET descricao = EXCLUDED.descricao;

-- EXPORTAR nao e atribuido automaticamente ao papel ADMIN. Sua concessao
-- depende de necessidade operacional/juridica comprovada e aprovacao separada.

COMMENT ON TABLE arquivo_publicidade_veiculacao IS
  'Janelas prospectivas de exposicao comercial/material; sem BASE gratuita pessoal nem backfill historico.';
COMMENT ON TABLE arquivo_publicidade_versao IS
  'Versoes imutaveis de conteudo exibivel; JSON privado completo apenas sob cobertura ABRANGIDA ou PREVENTIVA.';
COMMENT ON TABLE arquivo_publicidade_midia IS
  'Bytes preservados no bucket privado; nunca referencia documento KYC nem cria URL publica.';
