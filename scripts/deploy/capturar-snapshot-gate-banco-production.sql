SELECT kind, name, value
FROM (
  SELECT
    'META'::text AS kind,
    'database_identity'::text AS name,
    current_database()
      || ':' || (SELECT oid::text FROM pg_database WHERE datname = current_database())
      || ':' || (SELECT system_identifier::text FROM pg_control_system()) AS value

  UNION ALL

  SELECT
    'META',
    'flyway_version',
    COALESCE((
      SELECT version
      FROM flyway_schema_history
      WHERE success
      ORDER BY installed_rank DESC
      LIMIT 1
    ), 'AUSENTE')

  UNION ALL SELECT 'TABLE', 'anuncio', count(*)::text FROM anuncio
  UNION ALL SELECT 'TABLE', 'arquivo_midia', count(*)::text FROM arquivo_midia
  UNION ALL SELECT 'TABLE', 'ativacao_beneficio', count(*)::text FROM ativacao_beneficio
  UNION ALL SELECT 'TABLE', 'credencial_usuario', count(*)::text FROM credencial_usuario
  UNION ALL SELECT 'TABLE', 'documento_usuario', count(*)::text FROM documento_usuario
  UNION ALL SELECT 'TABLE', 'movimento_credito', count(*)::text FROM movimento_credito
  UNION ALL SELECT 'TABLE', 'usuario', count(*)::text FROM usuario

  UNION ALL SELECT 'STATUS', 'anuncio.APROVADO', count(*)::text FROM anuncio WHERE status = 'APROVADO'
  UNION ALL SELECT 'STATUS', 'anuncio.BLOQUEADO', count(*)::text FROM anuncio WHERE status = 'BLOQUEADO'
  UNION ALL SELECT 'STATUS', 'anuncio.PAUSADO', count(*)::text FROM anuncio WHERE status = 'PAUSADO'
  UNION ALL SELECT 'STATUS', 'anuncio.PENDENTE_REVISAO', count(*)::text FROM anuncio WHERE status = 'PENDENTE_REVISAO'
  UNION ALL SELECT 'STATUS', 'anuncio.PUBLICADO', count(*)::text FROM anuncio WHERE status = 'PUBLICADO'
  UNION ALL SELECT 'STATUS', 'anuncio.RASCUNHO', count(*)::text FROM anuncio WHERE status = 'RASCUNHO'
  UNION ALL SELECT 'STATUS', 'anuncio.REJEITADO', count(*)::text FROM anuncio WHERE status = 'REJEITADO'
  UNION ALL SELECT 'STATUS', 'anuncio.REMOVIDO', count(*)::text FROM anuncio WHERE status = 'REMOVIDO'

  UNION ALL

  SELECT 'INVARIANT', 'tabelas_criticas_ausentes', count(*)::text
  FROM (VALUES
    ('anuncio'),
    ('arquivo_midia'),
    ('ativacao_beneficio'),
    ('credencial_usuario'),
    ('documento_usuario'),
    ('movimento_credito'),
    ('usuario')
  ) AS critical_table(table_name)
  WHERE to_regclass('public.' || critical_table.table_name) IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'constraints_nao_validadas', count(*)::text
  FROM pg_constraint
  WHERE connamespace = 'public'::regnamespace
    AND NOT convalidated

  UNION ALL

  SELECT 'INVARIANT', 'anuncio_sem_usuario', count(*)::text
  FROM anuncio a
  LEFT JOIN usuario u ON u.id = a.usuario_id
  WHERE u.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'anuncio_midia_sem_anuncio', count(*)::text
  FROM anuncio_midia am
  LEFT JOIN anuncio a ON a.id = am.anuncio_id
  WHERE a.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'anuncio_midia_sem_arquivo', count(*)::text
  FROM anuncio_midia am
  LEFT JOIN arquivo_midia arquivo ON arquivo.id = am.arquivo_midia_id
  WHERE arquivo.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'credencial_sem_usuario', count(*)::text
  FROM credencial_usuario c
  LEFT JOIN usuario u ON u.id = c.usuario_id
  WHERE u.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'documento_sem_usuario', count(*)::text
  FROM documento_usuario d
  LEFT JOIN usuario u ON u.id = d.usuario_id
  WHERE u.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'documento_sem_arquivo', count(*)::text
  FROM documento_usuario d
  LEFT JOIN arquivo_midia arquivo ON arquivo.id = d.arquivo_midia_id
  WHERE arquivo.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'ativacao_sem_usuario', count(*)::text
  FROM ativacao_beneficio ativacao
  LEFT JOIN usuario u ON u.id = ativacao.usuario_id
  WHERE u.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'ativacao_sem_beneficio', count(*)::text
  FROM ativacao_beneficio ativacao
  LEFT JOIN beneficio_premium beneficio ON beneficio.id = ativacao.beneficio_id
  WHERE beneficio.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'movimento_sem_usuario', count(*)::text
  FROM movimento_credito movimento
  LEFT JOIN usuario u ON u.id = movimento.usuario_id
  WHERE u.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'movimento_saldo_incoerente', count(*)::text
  FROM movimento_credito
  WHERE NOT (
    (direcao = 'CREDITO' AND saldo_depois = saldo_antes + quantidade)
    OR (direcao = 'DEBITO' AND saldo_depois = saldo_antes - quantidade)
  )

  UNION ALL

  SELECT 'INVARIANT', 'movimento_idempotencia_duplicada', count(*)::text
  FROM (
    SELECT idempotency_key
    FROM movimento_credito
    WHERE idempotency_key IS NOT NULL
    GROUP BY idempotency_key
    HAVING count(*) > 1
  ) duplicadas

  UNION ALL

  SELECT 'INVARIANT', 'saldo_credito_sem_usuario', count(*)::text
  FROM saldo_credito_usuario saldo
  LEFT JOIN usuario u ON u.id = saldo.usuario_id
  WHERE u.id IS NULL

  UNION ALL

  SELECT 'INVARIANT', 'saldo_credito_negativo', count(*)::text
  FROM saldo_credito_usuario
  WHERE saldo_atual < 0

  UNION ALL

  SELECT
    'ACTIVITY',
    'eventos_metricas_estimados',
    COALESCE(sum(n_live_tup), 0)::bigint::text
  FROM pg_stat_user_tables
  WHERE relname IN (
    'agregado_clique_whatsapp_diario',
    'agregado_visualizacao_diaria',
    'agregado_visualizacao_inicial',
    'auditoria_evento',
    'clique_whatsapp',
    'evento_verificacao_etaria',
    'evento_visualizacao',
    'outbox_evento'
  )
) snapshot
ORDER BY kind, name;
