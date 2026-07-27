-- Age gate completo por escopo.
-- Estruturas aditivas para challenge, risco, tokens seguros e fallback documental.

ALTER TABLE evento_verificacao_etaria
  ADD COLUMN IF NOT EXISTS challenge_id uuid,
  ADD COLUMN IF NOT EXISTS session_hash text,
  ADD COLUMN IF NOT EXISTS estado text,
  ADD COLUMN IF NOT EXISTS escopo text,
  ADD COLUMN IF NOT EXISTS motivo_sanitizado text,
  ADD COLUMN IF NOT EXISTS documento_status text;

CREATE INDEX IF NOT EXISTS evento_verificacao_etaria_challenge_idx
  ON evento_verificacao_etaria (challenge_id, criado_em DESC)
  WHERE challenge_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS evento_verificacao_etaria_session_idx
  ON evento_verificacao_etaria (session_hash, criado_em DESC)
  WHERE session_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS compliance_visitor_challenge (
  id uuid PRIMARY KEY,
  session_hash text NOT NULL,
  nivel_solicitado text NOT NULL,
  nivel_efetivo text NOT NULL,
  escopo text NOT NULL,
  anuncio_id uuid REFERENCES anuncio (id),
  anuncio_midia_id uuid REFERENCES anuncio_midia (id),
  story_referencia text,
  rota_sanitizada text,
  status text NOT NULL,
  tentativas integer NOT NULL DEFAULT 0,
  max_tentativas integer NOT NULL DEFAULT 3,
  risco_score integer NOT NULL DEFAULT 0,
  risco_decisao text NOT NULL,
  motivo_sanitizado text,
  exige_aceite_explicito boolean NOT NULL DEFAULT false,
  exige_documento boolean NOT NULL DEFAULT false,
  idempotencia_hash text NOT NULL,
  verificacao_idempotencia_hash text,
  expira_em timestamptz NOT NULL,
  verificado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT compliance_challenge_session_hash_chk
    CHECK (session_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_challenge_nivel_solicitado_chk
    CHECK (nivel_solicitado IN ('LIGHT', 'REINFORCED', 'STRONG')),
  CONSTRAINT compliance_challenge_nivel_efetivo_chk
    CHECK (nivel_efetivo IN ('NONE', 'LIGHT', 'REINFORCED', 'STRONG')),
  CONSTRAINT compliance_challenge_escopo_chk
    CHECK (escopo IN ('MIDIA_RESTRITA', 'WHATSAPP', 'STORY', 'CONTEUDO_EXPLICITO')),
  CONSTRAINT compliance_challenge_status_chk
    CHECK (status IN (
      'ACTIVE',
      'VERIFIED',
      'FAILED',
      'BLOCKED',
      'EXPIRED',
      'DOCUMENT_PENDING',
      'DOCUMENT_APPROVED',
      'DOCUMENT_REJECTED'
    )),
  CONSTRAINT compliance_challenge_risco_decisao_chk
    CHECK (risco_decisao IN (
      'ALLOW_LEVEL_1',
      'REQUIRE_LEVEL_2',
      'REQUIRE_LEVEL_3',
      'REVIEW_FLAG',
      'TEMP_BLOCK',
      'HARD_BLOCK'
    )),
  CONSTRAINT compliance_challenge_tentativas_chk
    CHECK (tentativas >= 0 AND max_tentativas BETWEEN 1 AND 10),
  CONSTRAINT compliance_challenge_risco_score_chk
    CHECK (risco_score >= 0),
  CONSTRAINT compliance_challenge_idempotencia_hash_chk
    CHECK (idempotencia_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_challenge_verificacao_idempotencia_hash_chk
    CHECK (verificacao_idempotencia_hash IS NULL OR verificacao_idempotencia_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS compliance_challenge_session_status_idx
  ON compliance_visitor_challenge (session_hash, status, criado_em DESC);

CREATE INDEX IF NOT EXISTS compliance_challenge_expiracao_idx
  ON compliance_visitor_challenge (status, expira_em);

CREATE UNIQUE INDEX IF NOT EXISTS compliance_challenge_idempotencia_uk
  ON compliance_visitor_challenge (session_hash, idempotencia_hash);

CREATE TABLE IF NOT EXISTS compliance_visitor_token (
  id uuid PRIMARY KEY,
  token_hash text NOT NULL,
  session_hash text NOT NULL,
  user_agent_hash text NOT NULL,
  challenge_id uuid NOT NULL REFERENCES compliance_visitor_challenge (id),
  escopo_token text NOT NULL,
  nivel_acesso text NOT NULL,
  escopo_conteudo text,
  status text NOT NULL,
  risco_score integer NOT NULL DEFAULT 0,
  risco_decisao text NOT NULL,
  emitido_em timestamptz NOT NULL,
  expira_em timestamptz NOT NULL,
  revogado_em timestamptz,
  motivo_revogacao text,
  ultima_validacao_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT compliance_token_hash_chk
    CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_token_session_hash_chk
    CHECK (session_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_token_user_agent_hash_chk
    CHECK (user_agent_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_token_escopo_chk
    CHECK (escopo_token IN ('GENERAL', 'EXPLICIT')),
  CONSTRAINT compliance_token_nivel_chk
    CHECK (nivel_acesso IN ('LIGHT', 'REINFORCED', 'STRONG')),
  CONSTRAINT compliance_token_status_chk
    CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
  CONSTRAINT compliance_token_risco_decisao_chk
    CHECK (risco_decisao IN (
      'ALLOW_LEVEL_1',
      'REQUIRE_LEVEL_2',
      'REQUIRE_LEVEL_3',
      'REVIEW_FLAG',
      'TEMP_BLOCK',
      'HARD_BLOCK'
    )),
  CONSTRAINT compliance_token_risco_score_chk
    CHECK (risco_score >= 0),
  CONSTRAINT compliance_token_expiracao_chk
    CHECK (expira_em > emitido_em)
);

CREATE UNIQUE INDEX IF NOT EXISTS compliance_token_hash_uk
  ON compliance_visitor_token (token_hash);

CREATE INDEX IF NOT EXISTS compliance_token_session_status_idx
  ON compliance_visitor_token (session_hash, status, expira_em DESC);

CREATE UNIQUE INDEX IF NOT EXISTS compliance_token_challenge_scope_uk
  ON compliance_visitor_token (challenge_id, escopo_token);

CREATE TABLE IF NOT EXISTS compliance_visitor_risk_profile (
  id uuid PRIMARY KEY,
  session_hash text NOT NULL,
  score_atual integer NOT NULL DEFAULT 0,
  decisao_atual text NOT NULL DEFAULT 'ALLOW_LEVEL_1',
  reputacao_interna integer NOT NULL DEFAULT 0,
  falhas_consecutivas integer NOT NULL DEFAULT 0,
  acessos_restritos integer NOT NULL DEFAULT 0,
  acessos_explicitos integer NOT NULL DEFAULT 0,
  sinalizado_revisao boolean NOT NULL DEFAULT false,
  bloqueado_temporariamente_ate timestamptz,
  bloqueado_definitivamente_ate timestamptz,
  ultimo_motivo_sanitizado text,
  ultima_rota_sanitizada text,
  ultimo_anuncio_id uuid REFERENCES anuncio (id),
  visto_em timestamptz NOT NULL,
  ultimo_challenge_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT compliance_risk_session_hash_chk
    CHECK (session_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_risk_score_chk
    CHECK (score_atual >= 0),
  CONSTRAINT compliance_risk_contadores_chk
    CHECK (
      falhas_consecutivas >= 0
      AND acessos_restritos >= 0
      AND acessos_explicitos >= 0
    ),
  CONSTRAINT compliance_risk_decisao_chk
    CHECK (decisao_atual IN (
      'ALLOW_LEVEL_1',
      'REQUIRE_LEVEL_2',
      'REQUIRE_LEVEL_3',
      'REVIEW_FLAG',
      'TEMP_BLOCK',
      'HARD_BLOCK'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS compliance_risk_session_uk
  ON compliance_visitor_risk_profile (session_hash);

CREATE INDEX IF NOT EXISTS compliance_risk_score_idx
  ON compliance_visitor_risk_profile (score_atual DESC, atualizado_em DESC);

CREATE TABLE IF NOT EXISTS compliance_visitor_documento (
  id uuid PRIMARY KEY,
  session_hash text NOT NULL,
  challenge_id uuid NOT NULL REFERENCES compliance_visitor_challenge (id),
  anuncio_id uuid REFERENCES anuncio (id),
  status text NOT NULL,
  storage_provider text NOT NULL,
  bucket text NOT NULL,
  chave_objeto text NOT NULL,
  mime_type text NOT NULL,
  tamanho_bytes bigint NOT NULL,
  sha256 text NOT NULL,
  idempotencia_hash text NOT NULL,
  motivo_publico_sanitizado text,
  revisado_por uuid REFERENCES usuario (id),
  revisado_em timestamptz,
  criado_em timestamptz NOT NULL,
  atualizado_em timestamptz NOT NULL,
  versao bigint NOT NULL DEFAULT 0,
  CONSTRAINT compliance_documento_session_hash_chk
    CHECK (session_hash ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_documento_status_chk
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT compliance_documento_tamanho_chk
    CHECK (tamanho_bytes > 0 AND tamanho_bytes <= 12582912),
  CONSTRAINT compliance_documento_sha256_chk
    CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT compliance_documento_idempotencia_hash_chk
    CHECK (idempotencia_hash ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS compliance_documento_session_idempotencia_uk
  ON compliance_visitor_documento (session_hash, idempotencia_hash);

CREATE INDEX IF NOT EXISTS compliance_documento_status_data_idx
  ON compliance_visitor_documento (status, criado_em DESC);

CREATE INDEX IF NOT EXISTS compliance_documento_challenge_idx
  ON compliance_visitor_documento (challenge_id, criado_em DESC);

CREATE UNIQUE INDEX IF NOT EXISTS compliance_documento_pendente_challenge_uk
  ON compliance_visitor_documento (challenge_id)
  WHERE status = 'PENDING';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'evento_verificacao_etaria_challenge_fk'
      AND conrelid = 'evento_verificacao_etaria'::regclass
  ) THEN
    ALTER TABLE evento_verificacao_etaria
      ADD CONSTRAINT evento_verificacao_etaria_challenge_fk
      FOREIGN KEY (challenge_id)
      REFERENCES compliance_visitor_challenge (id);
  END IF;
END
$$;

COMMENT ON TABLE compliance_visitor_challenge IS
  'Challenges opacos e antirreplay do age gate reforcado; nao contem CPF ou nascimento.';

COMMENT ON TABLE compliance_visitor_token IS
  'Tokens de acesso por escopo; somente o hash do token assinado e persistido.';

COMMENT ON TABLE compliance_visitor_risk_profile IS
  'Perfil pseudonimo e sanitizado do motor de risco do visitante.';

COMMENT ON TABLE compliance_visitor_documento IS
  'Fallback documental privado do visitante; nenhuma URL publica e emitida.';
