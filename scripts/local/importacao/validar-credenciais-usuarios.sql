\set ON_ERROR_STOP on

-- Valida a reconciliacao sem exibir e-mail, hash, token ou identificador pessoal.

CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE SCHEMA legacy_credencial_validacao;
CREATE SERVER legacy_credencial_validacao_source
  FOREIGN DATA WRAPPER postgres_fdw
  OPTIONS (host '/var/run/postgresql', dbname 'source_snapshot');
CREATE USER MAPPING FOR CURRENT_USER
  SERVER legacy_credencial_validacao_source
  OPTIONS (user 'topsv3dry');

IMPORT FOREIGN SCHEMA public LIMIT TO (usuarios)
FROM SERVER legacy_credencial_validacao_source INTO legacy_credencial_validacao;

CREATE TEMP TABLE validar_credencial_context AS
SELECT
  md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id,
  :'snapshot_fingerprint'::text AS snapshot_fingerprint;

CREATE TEMP TABLE validar_credencial_candidato AS
SELECT
  origem.id AS origem_id,
  mapa.entidade_v3_id AS usuario_v3_id,
  origem.senha AS hash_origem,
  origem.status AS status_origem,
  origem.role AS papel_origem,
  origem.is_verificado AS email_verificado,
  origem.two_factor_ativo AS segundo_fator_ativo,
  CASE
    WHEN origem.senha !~ '^\$2a\$10\$[./A-Za-z0-9]{53}$' THEN 'HASH_INVALIDO_OU_NAO_SUPORTADO'
    ELSE 'ELEGIVEL'
  END AS classificacao,
  CASE
    WHEN origem.status <> 'ATIVO' THEN 'RECUSADO_CONTA_DESATIVADA'
    WHEN NOT origem.is_verificado THEN 'RECUSADO_EMAIL_PENDENTE'
    ELSE 'PERMITIDO_SENHA'
  END AS acesso_v3
FROM legacy_credencial_validacao.usuarios origem
CROSS JOIN validar_credencial_context contexto
JOIN importacao_mapeamento mapa
  ON mapa.execucao_id = contexto.execucao_id
 AND mapa.sistema_origem = 'TOPSDOJOB_PRODUCAO'
 AND mapa.tabela_origem = 'usuarios'
 AND mapa.id_origem = origem.id::text
 AND mapa.entidade_tipo = 'USUARIO'
 AND mapa.status = 'MAPEADO';

BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

DO $$
DECLARE
  contexto record;
  fingerprint_registrado text;
BEGIN
  SELECT * INTO contexto FROM validar_credencial_context;
  SELECT resumo_json ->> 'snapshotFingerprint'
  INTO fingerprint_registrado
  FROM importacao_execucao
  WHERE id = contexto.execucao_id;

  IF fingerprint_registrado IS NULL OR fingerprint_registrado <> contexto.snapshot_fingerprint THEN
    RAISE EXCEPTION 'snapshot ou fingerprint invalido para validar credenciais';
  END IF;

  IF (SELECT count(*) FROM validar_credencial_candidato)
      <> (SELECT count(*) FROM legacy_credencial_validacao.usuarios) THEN
    RAISE EXCEPTION 'usuario da origem sem mapa canonico unico';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM validar_credencial_candidato c
    JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
    WHERE c.classificacao = 'ELEGIVEL'
      AND (cred.senha_hash <> c.hash_origem OR cred.algoritmo <> 'BCRYPT')
  ) OR EXISTS (
    SELECT 1
    FROM validar_credencial_candidato c
    WHERE c.classificacao = 'ELEGIVEL'
      AND NOT EXISTS (SELECT 1 FROM credencial_usuario cred WHERE cred.usuario_id = c.usuario_v3_id)
  ) THEN
    RAISE EXCEPTION 'credencial elegivel ausente ou divergente';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM validar_credencial_candidato c
    JOIN credencial_usuario cred ON cred.usuario_id = c.usuario_v3_id
    WHERE c.classificacao <> 'ELEGIVEL'
  ) THEN
    RAISE EXCEPTION 'credencial invalida ou inconsistente foi promovida';
  END IF;

  IF EXISTS (
    SELECT usuario_id FROM credencial_usuario GROUP BY usuario_id HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'usuario recebeu credenciais duplicadas';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM validar_credencial_candidato c
    JOIN usuario u ON u.id = c.usuario_v3_id
    WHERE u.status <> CASE WHEN c.status_origem = 'ATIVO' THEN 'ATIVO' ELSE 'DESATIVADO' END
       OR u.tipo_conta <> CASE WHEN c.papel_origem IN ('ADMIN', 'MODERADOR') THEN 'STAFF' ELSE 'ANUNCIANTE' END
       OR (u.email_verificado_em IS NOT NULL) <> c.email_verificado
  ) THEN
    RAISE EXCEPTION 'estado, tipo ou confirmacao de conta nao foram preservados';
  END IF;

  IF (SELECT count(*) FROM sessao_usuario) <> 0 OR (SELECT count(*) FROM token_seguranca) <> 0 THEN
    RAISE EXCEPTION 'sessao ou token foi importado indevidamente';
  END IF;

  IF EXISTS (
    SELECT 1 FROM stg_usuario
    WHERE execucao_id = contexto.execucao_id
      AND payload_normalizado_json::text ~* '(senha_hash|\\$2a\\$10\\$|codigo_verificacao|two_factor_secret|reset_code)'
  ) THEN
    RAISE EXCEPTION 'staging contem material de credencial legado';
  END IF;
END $$;

SELECT 'CREDENCIAL_AUDITADA|' || count(*) FROM validar_credencial_candidato;
SELECT 'CREDENCIAL_ALGORITMO|BCRYPT_2A_CUSTO_10|' || count(*)
FROM legacy_credencial_validacao.usuarios
WHERE senha ~ '^\$2a\$10\$[./A-Za-z0-9]{53}$';
SELECT 'CREDENCIAL_ELEGIVEL|' || count(*)
FROM validar_credencial_candidato WHERE classificacao = 'ELEGIVEL';
SELECT 'CREDENCIAL_IMPORTADA|' || count(*) FROM credencial_usuario;
SELECT 'CREDENCIAL_QUARENTENA|' || count(*)
FROM validar_credencial_candidato WHERE classificacao <> 'ELEGIVEL';
SELECT 'CREDENCIAL_AUTENTICAVEL|' || count(*)
FROM validar_credencial_candidato WHERE acesso_v3 = 'PERMITIDO_SENHA';
SELECT 'SEGUNDO_FATOR_LEGADO_DESATIVADO_V3|' || count(*)
FROM validar_credencial_candidato WHERE segundo_fator_ativo;
SELECT 'CREDENCIAL_EMAIL_PENDENTE|' || count(*)
FROM validar_credencial_candidato WHERE acesso_v3 = 'RECUSADO_EMAIL_PENDENTE';
SELECT 'CREDENCIAL_CONTA_DESATIVADA|' || count(*)
FROM validar_credencial_candidato WHERE acesso_v3 = 'RECUSADO_CONTA_DESATIVADA';
SELECT 'CREDENCIAL_QUARENTENA_HASH|' || count(*)
FROM validar_credencial_candidato WHERE classificacao = 'HASH_INVALIDO_OU_NAO_SUPORTADO';
SELECT 'CREDENCIAL_DUPLICADA|' || count(*)
FROM (SELECT usuario_id FROM credencial_usuario GROUP BY usuario_id HAVING count(*) > 1) d;
SELECT 'SESSAO_IMPORTADA|' || count(*) FROM sessao_usuario;
SELECT 'TOKEN_IMPORTADO|' || count(*) FROM token_seguranca;
SELECT 'CREDENCIAL_FINGERPRINT|' || md5(coalesce(string_agg(
  concat_ws(':', cred.id, cred.usuario_id, cred.algoritmo, length(cred.senha_hash),
            u.status, u.tipo_conta, u.email_verificado_em IS NOT NULL),
  '|' ORDER BY cred.usuario_id), ''))
FROM credencial_usuario cred
JOIN usuario u ON u.id = cred.usuario_id;

COMMIT;

DROP SCHEMA legacy_credencial_validacao CASCADE;
DROP SERVER legacy_credencial_validacao_source CASCADE;
DROP EXTENSION postgres_fdw;
