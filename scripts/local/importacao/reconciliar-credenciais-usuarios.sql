\set ON_ERROR_STOP on

-- Reconcilia credenciais do snapshot legado em um banco V3 descartavel.
-- Requer snapshot_id e snapshot_fingerprint do dry-run saneado correspondente.
-- O hash e lido somente dentro do banco descartavel e nunca vai para staging,
-- manifesto, log ou resultado agregado.

CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE SCHEMA legacy_credencial;
CREATE SERVER legacy_credencial_source
  FOREIGN DATA WRAPPER postgres_fdw
  OPTIONS (host '/var/run/postgresql', dbname 'source_snapshot');
CREATE USER MAPPING FOR CURRENT_USER
  SERVER legacy_credencial_source
  OPTIONS (user 'topsv3dry');

IMPORT FOREIGN SCHEMA public LIMIT TO (usuarios)
FROM SERVER legacy_credencial_source INTO legacy_credencial;

BEGIN;

CREATE TEMP TABLE dryrun_credencial_context AS
SELECT
  md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id,
  :'snapshot_id'::text AS snapshot_id,
  :'snapshot_fingerprint'::text AS snapshot_fingerprint;

DO $$
DECLARE
  contexto record;
  fingerprint_registrado text;
BEGIN
  SELECT * INTO contexto FROM dryrun_credencial_context;

  SELECT resumo_json ->> 'snapshotFingerprint'
  INTO fingerprint_registrado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF fingerprint_registrado IS NULL THEN
    RAISE EXCEPTION 'snapshot nao registrado para reconciliacao de credenciais';
  END IF;

  IF fingerprint_registrado <> contexto.snapshot_fingerprint THEN
    RAISE EXCEPTION 'snapshotId ja registrado com fingerprint divergente';
  END IF;
END $$;

CREATE TEMP TABLE dryrun_credencial_candidato AS
WITH email_contagem AS (
  SELECT lower(btrim(email)) AS email_normalizado, count(*) AS total
  FROM legacy_credencial.usuarios
  GROUP BY lower(btrim(email))
), origem AS (
  SELECT
    u.id AS origem_id,
    lower(btrim(u.email)) AS email_normalizado,
    u.senha AS hash_origem,
    u.status AS status_origem,
    u.role AS papel_origem,
    u.is_verificado AS email_verificado,
    u.two_factor_ativo AS segundo_fator_ativo,
    coalesce(ec.total, 0) AS email_total,
    m.entidade_v3_id AS usuario_v3_id
  FROM legacy_credencial.usuarios u
  LEFT JOIN email_contagem ec ON ec.email_normalizado = lower(btrim(u.email))
  CROSS JOIN dryrun_credencial_context c
  LEFT JOIN importacao_mapeamento m
    ON m.execucao_id = c.execucao_id
   AND m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
   AND m.tabela_origem = 'usuarios'
   AND m.id_origem = u.id::text
   AND m.entidade_tipo = 'USUARIO'
   AND m.status = 'MAPEADO'
)
SELECT
  o.*,
  CASE
    WHEN o.usuario_v3_id IS NULL THEN 'MAPEAMENTO_AUSENTE'
    WHEN v.id IS NULL THEN 'USUARIO_V3_AUSENTE'
    WHEN o.email_normalizado IS NULL
      OR o.email_normalizado = ''
      OR length(o.email_normalizado) > 320
      OR o.email_normalizado !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
      THEN 'EMAIL_INVALIDO'
    WHEN o.email_total <> 1 THEN 'EMAIL_CONFLITANTE'
    WHEN o.papel_origem NOT IN ('ADMIN', 'MODERADOR', 'USER') THEN 'PAPEL_INCOMPATIVEL'
    WHEN o.hash_origem !~ '^\$2a\$10\$[./A-Za-z0-9]{53}$' THEN 'HASH_INVALIDO_OU_NAO_SUPORTADO'
    ELSE 'ELEGIVEL'
  END AS classificacao,
  CASE
    WHEN o.status_origem <> 'ATIVO' THEN 'RECUSADO_CONTA_DESATIVADA'
    WHEN NOT o.email_verificado THEN 'RECUSADO_EMAIL_PENDENTE'
    ELSE 'PERMITIDO_SENHA'
  END AS acesso_v3,
  CASE
    WHEN o.hash_origem ~ '^\$2a\$10\$[./A-Za-z0-9]{53}$' THEN 'BCRYPT'
    ELSE 'INVALIDO_OU_NAO_SUPORTADO'
  END AS algoritmo,
  coalesce(length(o.hash_origem), 0) AS hash_comprimento
FROM origem o
LEFT JOIN usuario v ON v.id = o.usuario_v3_id;

DO $$
BEGIN
  IF (SELECT count(*) FROM dryrun_credencial_candidato)
      <> (SELECT count(*) FROM legacy_credencial.usuarios) THEN
    RAISE EXCEPTION 'auditoria de credenciais perdeu usuarios da origem';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN usuario u ON u.id = c.usuario_v3_id
    WHERE c.classificacao <> 'MAPEAMENTO_AUSENTE'
      AND (
        u.status <> CASE WHEN c.status_origem = 'ATIVO' THEN 'ATIVO' ELSE 'DESATIVADO' END
        OR u.tipo_conta <> CASE
          WHEN c.papel_origem IN ('ADMIN', 'MODERADOR') THEN 'STAFF'
          ELSE 'ANUNCIANTE'
        END
      )
  ) THEN
    RAISE EXCEPTION 'estado ou tipo de conta V3 diverge do mapa canonico';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN papel_usuario p ON p.usuario_id = c.usuario_v3_id
    WHERE p.papel <> CASE
      WHEN c.papel_origem = 'ADMIN' THEN 'ADMIN'
      WHEN c.papel_origem = 'MODERADOR' THEN 'MODERADOR'
      ELSE 'USUARIO'
    END
  ) OR EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    WHERE c.usuario_v3_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM papel_usuario p WHERE p.usuario_id = c.usuario_v3_id)
  ) THEN
    RAISE EXCEPTION 'papel V3 diverge do papel comprovado na origem';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN usuario outro
      ON outro.email_normalizado = c.email_normalizado
     AND outro.id <> c.usuario_v3_id
    WHERE c.classificacao NOT IN ('MAPEAMENTO_AUSENTE', 'USUARIO_V3_AUSENTE', 'EMAIL_INVALIDO', 'EMAIL_CONFLITANTE')
  ) THEN
    RAISE EXCEPTION 'email normalizado conflita com outro usuario V3';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN usuario u ON u.id = c.usuario_v3_id
    WHERE c.classificacao NOT IN ('MAPEAMENTO_AUSENTE', 'USUARIO_V3_AUSENTE', 'EMAIL_INVALIDO', 'EMAIL_CONFLITANTE')
      AND u.email_normalizado <> c.email_normalizado
      AND u.email_normalizado !~ '^legacy-[0-9]+@example\.invalid$'
  ) THEN
    RAISE EXCEPTION 'usuario V3 ja possui identificador de login divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
    WHERE c.classificacao = 'ELEGIVEL'
      AND (cred.algoritmo <> 'BCRYPT' OR cred.senha_hash <> c.hash_origem)
  ) THEN
    RAISE EXCEPTION 'credencial V3 existente diverge do snapshot';
  END IF;
END $$;

-- O e-mail e o estado de confirmacao sao restaurados para todos os usuarios
-- inequivocamente mapeados, inclusive contas mantidas em quarentena. Como a
-- origem guarda apenas o booleano, snapshot_at registra quando o estado foi
-- observado, e nao inventa uma data historica de confirmacao.
UPDATE usuario u
SET email_normalizado = c.email_normalizado,
    email_verificado_em = CASE WHEN c.email_verificado THEN e.iniciado_em ELSE NULL END,
    atualizado_em = e.iniciado_em
FROM dryrun_credencial_candidato c
CROSS JOIN dryrun_credencial_context ctx
JOIN importacao_execucao e ON e.id = ctx.execucao_id
WHERE u.id = c.usuario_v3_id
  AND c.classificacao NOT IN (
    'MAPEAMENTO_AUSENTE',
    'USUARIO_V3_AUSENTE',
    'EMAIL_INVALIDO',
    'EMAIL_CONFLITANTE'
  )
  AND (
    u.email_normalizado IS DISTINCT FROM c.email_normalizado
    OR u.email_verificado_em IS DISTINCT FROM CASE WHEN c.email_verificado THEN e.iniciado_em ELSE NULL END
  );

CREATE TEMP TABLE dryrun_credencial_resultado (credenciais_criadas bigint NOT NULL);

WITH contexto AS (
  SELECT c.*, e.iniciado_em AS snapshot_at
  FROM dryrun_credencial_context c
  JOIN importacao_execucao e ON e.id = c.execucao_id
), inseridas AS (
  INSERT INTO credencial_usuario (
    id, usuario_id, senha_hash, algoritmo, alterada_em, precisa_redefinir, criado_em
  )
  SELECT
    md5('legacy:credencial:' || candidato.origem_id)::uuid,
    candidato.usuario_v3_id,
    candidato.hash_origem,
    'BCRYPT',
    contexto.snapshot_at,
    false,
    contexto.snapshot_at
  FROM dryrun_credencial_candidato candidato
  CROSS JOIN contexto
  WHERE candidato.classificacao = 'ELEGIVEL'
    AND NOT EXISTS (
      SELECT 1 FROM credencial_usuario existente
      WHERE existente.usuario_id = candidato.usuario_v3_id
    )
  ON CONFLICT DO NOTHING
  RETURNING 1
)
INSERT INTO dryrun_credencial_resultado (credenciais_criadas)
SELECT count(*) FROM inseridas;

UPDATE stg_usuario s
SET payload_normalizado_json = s.payload_normalizado_json || jsonb_build_object(
      'credencialClassificacao', c.classificacao,
      'credencialAlgoritmo', c.algoritmo,
      'credencialHashComprimento', c.hash_comprimento,
      'credencialImportada', c.classificacao = 'ELEGIVEL'
        AND EXISTS (SELECT 1 FROM credencial_usuario cred WHERE cred.usuario_id = c.usuario_v3_id),
      'acessoV3', c.acesso_v3,
      'emailOrigemRestaurado', u.email_normalizado = c.email_normalizado,
      'emailVerificadoOrigem', c.email_verificado,
      'segundoFatorAtivoOrigem', c.segundo_fator_ativo,
      'segundoFatorLegadoDesativadoV3', c.segundo_fator_ativo,
      'statusOrigem', c.status_origem,
      'papelOrigem', c.papel_origem,
      'versaoImportadorCredencial', 'credenciais-usuarios-v2'
    )
FROM dryrun_credencial_candidato c
CROSS JOIN dryrun_credencial_context ctx
LEFT JOIN usuario u ON u.id = c.usuario_v3_id
WHERE s.execucao_id = ctx.execucao_id
  AND s.sistema_origem = 'TOPSDOJOB_PRODUCAO'
  AND s.tabela_origem = 'usuarios'
  AND s.id_origem = c.origem_id::text;

INSERT INTO importacao_pendencia (
  id, execucao_id, codigo, severidade, status, entidade_tipo,
  id_origem, detalhe_resumido, criado_em
)
SELECT
  md5('pendencia:credencial:' || c.origem_id || ':' || c.classificacao)::uuid,
  ctx.execucao_id,
  CASE c.classificacao
    WHEN 'HASH_INVALIDO_OU_NAO_SUPORTADO' THEN 'CREDENCIAL_HASH_NAO_SUPORTADO'
    WHEN 'EMAIL_INVALIDO' THEN 'CREDENCIAL_EMAIL_INVALIDO'
    WHEN 'EMAIL_CONFLITANTE' THEN 'CREDENCIAL_EMAIL_CONFLITANTE'
    WHEN 'PAPEL_INCOMPATIVEL' THEN 'CREDENCIAL_PAPEL_INCOMPATIVEL'
    ELSE 'CREDENCIAL_MAPEAMENTO_INCONSISTENTE'
  END,
  CASE WHEN c.classificacao IN ('MAPEAMENTO_AUSENTE', 'USUARIO_V3_AUSENTE', 'EMAIL_CONFLITANTE')
    THEN 'CRITICA' ELSE 'ALTA' END,
  'ABERTA',
  'CREDENCIAL_USUARIO',
  c.origem_id::text,
  CASE c.classificacao
    WHEN 'HASH_INVALIDO_OU_NAO_SUPORTADO' THEN 'Formato de hash ausente, invalido ou fora do contrato comprovado.'
    ELSE 'Credencial retida por conflito de identidade ou mapeamento.'
  END,
  e.iniciado_em
FROM dryrun_credencial_candidato c
CROSS JOIN dryrun_credencial_context ctx
JOIN importacao_execucao e ON e.id = ctx.execucao_id
WHERE c.classificacao <> 'ELEGIVEL'
ON CONFLICT (id) DO NOTHING;

UPDATE importacao_execucao e
SET resumo_json = e.resumo_json || jsonb_build_object(
  'credenciais', jsonb_build_object(
    'versaoImportador', 'credenciais-usuarios-v2',
    'auditadas', (SELECT count(*) FROM dryrun_credencial_candidato),
    'elegiveis', (SELECT count(*) FROM dryrun_credencial_candidato WHERE classificacao = 'ELEGIVEL'),
    'importadas', (
      SELECT count(*)
      FROM dryrun_credencial_candidato c
      JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
      WHERE c.classificacao = 'ELEGIVEL'
    ),
    'quarentena', (SELECT count(*) FROM dryrun_credencial_candidato WHERE classificacao <> 'ELEGIVEL'),
    'autenticaveis', (SELECT count(*) FROM dryrun_credencial_candidato WHERE acesso_v3 = 'PERMITIDO_SENHA'),
    'segundoFatorLegadoDesativadoV3', (SELECT count(*) FROM dryrun_credencial_candidato WHERE segundo_fator_ativo),
    'emailPendente', (SELECT count(*) FROM dryrun_credencial_candidato WHERE acesso_v3 = 'RECUSADO_EMAIL_PENDENTE'),
    'contaDesativada', (SELECT count(*) FROM dryrun_credencial_candidato WHERE acesso_v3 = 'RECUSADO_CONTA_DESATIVADA'),
    'hashInvalidoOuNaoSuportado', (SELECT count(*) FROM dryrun_credencial_candidato WHERE classificacao = 'HASH_INVALIDO_OU_NAO_SUPORTADO'),
    'sessoesImportadas', 0,
    'tokensImportados', 0
  )
)
FROM dryrun_credencial_context ctx
WHERE e.id = ctx.execucao_id;

DO $$
DECLARE
  elegiveis bigint;
  importadas bigint;
BEGIN
  SELECT count(*) INTO elegiveis
  FROM dryrun_credencial_candidato
  WHERE classificacao = 'ELEGIVEL';

  SELECT count(*) INTO importadas
  FROM dryrun_credencial_candidato c
  JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
  WHERE c.classificacao = 'ELEGIVEL';

  IF importadas <> elegiveis THEN
    RAISE EXCEPTION 'credenciais elegiveis nao foram reconciliadas integralmente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
    WHERE c.classificacao = 'ELEGIVEL'
      AND (cred.senha_hash <> c.hash_origem OR cred.algoritmo <> 'BCRYPT')
  ) THEN
    RAISE EXCEPTION 'hash importado diverge do hash comprovado na origem';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM dryrun_credencial_candidato c
    JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
    WHERE c.classificacao <> 'ELEGIVEL'
  ) THEN
    RAISE EXCEPTION 'credencial em quarentena foi promovida';
  END IF;
END $$;

SELECT
  'CREDENCIAIS_USUARIOS|novas=' || r.credenciais_criadas
  || '|auditadas=' || count(*)
  || '|elegiveis=' || count(*) FILTER (WHERE c.classificacao = 'ELEGIVEL')
  || '|importadas=' || count(cred.id) FILTER (WHERE c.classificacao = 'ELEGIVEL')
  || '|quarentena=' || count(*) FILTER (WHERE c.classificacao <> 'ELEGIVEL')
  || '|autenticaveis=' || count(*) FILTER (WHERE c.acesso_v3 = 'PERMITIDO_SENHA')
  || '|segundo_fator_desativado_v3=' || count(*) FILTER (WHERE c.segundo_fator_ativo)
  || '|email_pendente=' || count(*) FILTER (WHERE c.acesso_v3 = 'RECUSADO_EMAIL_PENDENTE')
  || '|conta_desativada=' || count(*) FILTER (WHERE c.acesso_v3 = 'RECUSADO_CONTA_DESATIVADA')
  || '|hash_invalido=' || count(*) FILTER (WHERE c.classificacao = 'HASH_INVALIDO_OU_NAO_SUPORTADO')
FROM dryrun_credencial_candidato c
CROSS JOIN dryrun_credencial_resultado r
LEFT JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
GROUP BY r.credenciais_criadas;

COMMIT;

DROP SCHEMA legacy_credencial CASCADE;
DROP SERVER legacy_credencial_source CASCADE;
DROP EXTENSION postgres_fdw;
