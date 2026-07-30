package br.com.topsdojob.v3.persistence.repository.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUsuarioExclusaoJdbcRepository {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z_][a-z0-9_]*$");
    private static final Set<String> AUTH_OUTBOX_TYPES = Set.of(
            "AUTH_CONFIRMACAO_CONTA_SOLICITADA",
            "AUTH_CONFIRMACAO_CONTA_REENVIADA",
            "AUTH_RECUPERACAO_SENHA_SOLICITADA");
    private static final Set<Reference> DISPOSABLE_REFERENCES = Set.of(
            new Reference("public", "credencial_usuario", "usuario_id"),
            new Reference("public", "sessao_usuario", "usuario_id"),
            new Reference("public", "token_seguranca", "usuario_id"),
            new Reference("public", "papel_usuario", "usuario_id"),
            new Reference("public", "favorito_anuncio", "usuario_id"),
            new Reference("public", "saldo_credito_usuario", "usuario_id"),
            new Reference("public", "wizard_progresso", "usuario_id"));
    private static final List<String> DEPENDENCY_ORDER = List.of(
            "USUARIO_IMPORTADO",
            "POSSUI_ANUNCIOS",
            "POSSUI_DOCUMENTOS_KYC",
            "POSSUI_SALDO_OU_LEDGER",
            "POSSUI_PAGAMENTOS",
            "POSSUI_HISTORICO_OPERACIONAL");

    private final NamedParameterJdbcTemplate jdbc;

    public AdminUsuarioExclusaoJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DependencyAnalysis analisar(UUID usuarioId) {
        Map<String, Object> params = Map.of("usuarioId", usuarioId);
        Set<String> tipos = new LinkedHashSet<>();
        long vinculos = 0;

        boolean staff = exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM papel_usuario
                  WHERE usuario_id = :usuarioId
                    AND papel <> 'USUARIO'
                )
                """, params);
        boolean importado = exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM importacao_mapeamento
                  WHERE entidade_tipo = 'USUARIO'
                    AND entidade_v3_id = :usuarioId
                )
                """, params);
        if (importado) {
            tipos.add("USUARIO_IMPORTADO");
            vinculos++;
        }
        long saldo = count("""
                SELECT count(*)
                FROM saldo_credito_usuario
                WHERE usuario_id = :usuarioId
                  AND saldo_atual <> 0
                """, params);
        if (saldo > 0) {
            tipos.add("POSSUI_SALDO_OU_LEDGER");
            vinculos += saldo;
        }
        boolean operacaoConcorrente = exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM outbox_evento
                  WHERE status = 'PROCESSANDO'
                    AND (
                      (aggregate_tipo = 'USUARIO' AND aggregate_id = :usuarioId)
                      OR (
                        aggregate_tipo = 'ANUNCIO'
                        AND aggregate_id IN (
                          SELECT id FROM anuncio WHERE usuario_id = :usuarioId
                        )
                      )
                    )
                )
                """, params);
        long outboxDuravel = count("""
                SELECT count(*)
                FROM outbox_evento
                WHERE aggregate_tipo = 'USUARIO'
                  AND aggregate_id = :usuarioId
                  AND tipo_evento NOT IN (
                    'AUTH_CONFIRMACAO_CONTA_SOLICITADA',
                    'AUTH_CONFIRMACAO_CONTA_REENVIADA',
                    'AUTH_RECUPERACAO_SENHA_SOLICITADA'
                  )
                """, params);
        if (outboxDuravel > 0) {
            tipos.add("POSSUI_HISTORICO_OPERACIONAL");
            vinculos += outboxDuravel;
        }
        long auditoriasSemFk = count("""
                SELECT count(*)
                FROM auditoria_evento
                WHERE recurso_tipo = 'USUARIO'
                  AND recurso_id = :usuarioId
                  AND ator_usuario_id IS DISTINCT FROM :usuarioId
                """, params);
        if (auditoriasSemFk > 0) {
            tipos.add("POSSUI_HISTORICO_OPERACIONAL");
            vinculos += auditoriasSemFk;
        }

        for (Reference reference : userReferences()) {
            if (DISPOSABLE_REFERENCES.contains(reference)) {
                continue;
            }
            long total = count(referenceCountSql(reference), params);
            if (total > 0) {
                tipos.add(blockerFor(reference.table()));
                vinculos += total;
            }
        }

        List<String> ordered = new ArrayList<>(tipos);
        ordered.sort(Comparator.comparingInt(code -> {
            int index = DEPENDENCY_ORDER.indexOf(code);
            return index < 0 ? DEPENDENCY_ORDER.size() : index;
        }));
        return new DependencyAnalysis(
                staff,
                importado,
                operacaoConcorrente,
                vinculos,
                List.copyOf(ordered));
    }

    public java.util.Optional<String> exclusaoConcluida(
            UUID usuarioId,
            UUID atorId,
            String idempotencyHash) {
        List<String> strategies = jdbc.query("""
                SELECT depois_json ->> 'estrategia'
                FROM auditoria_evento
                WHERE acao IN (
                    'USUARIO_EXCLUIDO_FISICAMENTE',
                    'USUARIO_EXCLUIDO_COM_ANONIMIZACAO'
                  )
                  AND recurso_tipo = 'USUARIO'
                  AND recurso_id = :usuarioId
                  AND ator_usuario_id = :atorId
                  AND resultado = 'SUCESSO'
                  AND depois_json ->> 'idempotencyHash' = :idempotencyHash
                ORDER BY criado_em DESC
                LIMIT 1
                """, Map.of(
                        "usuarioId", usuarioId,
                        "atorId", atorId,
                        "idempotencyHash", idempotencyHash),
                (resultSet, rowNumber) -> resultSet.getString(1));
        return strategies.stream().findFirst();
    }

    public java.util.Optional<String> exclusaoConcluidaPorRecurso(
            UUID usuarioId,
            String idempotencyHash) {
        List<String> strategies = jdbc.query("""
                SELECT depois_json ->> 'estrategia'
                FROM auditoria_evento
                WHERE acao IN (
                    'USUARIO_AUTOEXCLUIDO_FISICAMENTE',
                    'USUARIO_AUTOEXCLUIDO_COM_ANONIMIZACAO'
                  )
                  AND recurso_tipo = 'USUARIO'
                  AND recurso_id = :usuarioId
                  AND resultado = 'SUCESSO'
                  AND depois_json ->> 'idempotencyHash' = :idempotencyHash
                ORDER BY criado_em DESC
                LIMIT 1
                """, Map.of(
                        "usuarioId", usuarioId,
                        "idempotencyHash", idempotencyHash),
                (resultSet, rowNumber) -> resultSet.getString(1));
        return strategies.stream().findFirst();
    }

    public TechnicalDeletionResult deleteTechnicalLinks(UUID usuarioId) {
        Map<String, Object> params = Map.of(
                "usuarioId", usuarioId,
                "authTypes", AUTH_OUTBOX_TYPES);
        int outbox = jdbc.update("""
                DELETE FROM outbox_evento
                WHERE aggregate_tipo = 'USUARIO'
                  AND aggregate_id = :usuarioId
                  AND tipo_evento IN (:authTypes)
                  AND status <> 'PROCESSANDO'
                """, params);
        int favorites = jdbc.update(
                "DELETE FROM favorito_anuncio WHERE usuario_id = :usuarioId",
                params);
        int wizard = jdbc.update(
                "DELETE FROM wizard_progresso WHERE usuario_id = :usuarioId",
                params);
        int balances = jdbc.update("""
                DELETE FROM saldo_credito_usuario
                WHERE usuario_id = :usuarioId
                  AND saldo_atual = 0
                """, params);
        int tokens = jdbc.update(
                "DELETE FROM token_seguranca WHERE usuario_id = :usuarioId",
                params);
        int sessions = jdbc.update(
                "DELETE FROM sessao_usuario WHERE usuario_id = :usuarioId",
                params);
        int credentials = jdbc.update(
                "DELETE FROM credencial_usuario WHERE usuario_id = :usuarioId",
                params);
        int roles = jdbc.update(
                "DELETE FROM papel_usuario WHERE usuario_id = :usuarioId",
                params);
        return new TechnicalDeletionResult(
                credentials,
                sessions,
                tokens,
                roles,
                favorites,
                wizard,
                balances,
                outbox);
    }

    public void anonymizeAuxiliaryData(
            UUID usuarioId,
            List<UUID> anuncioIds,
            java.time.OffsetDateTime agora) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("usuarioId", usuarioId);
        params.put("anuncioIds", anuncioIds.isEmpty() ? List.of(new UUID(0L, 0L)) : anuncioIds);
        params.put("agora", agora);
        jdbc.update("""
                UPDATE comercial_contato
                SET nome_contato = 'Conta excluida',
                    email_normalizado = NULL,
                    telefone_normalizado = NULL,
                    observacao_resumida = NULL,
                    atualizado_em = :agora
                WHERE usuario_id = :usuarioId
                """, params);
        jdbc.update("""
                UPDATE outbox_evento
                SET payload_json = '{"conta":"EXCLUIDA","dadosPessoaisOcultos":true}'::jsonb,
                    status = CASE
                      WHEN status IN ('PENDENTE', 'ERRO') THEN 'CANCELADO'
                      ELSE status
                    END,
                    erro_resumido = NULL,
                    atualizado_em = :agora
                WHERE (aggregate_tipo = 'USUARIO' AND aggregate_id = :usuarioId)
                   OR (aggregate_tipo = 'ANUNCIO' AND aggregate_id IN (:anuncioIds))
                """, params);
        jdbc.update("""
                UPDATE auditoria_evento
                SET antes_json = '{"dadosPessoaisOcultos":true}'::jsonb,
                    depois_json = '{"dadosPessoaisOcultos":true}'::jsonb
                WHERE (
                    recurso_tipo = 'USUARIO'
                    AND recurso_id = :usuarioId
                  )
                  OR ator_usuario_id = :usuarioId
                """, params);
    }

    private List<Reference> userReferences() {
        return jdbc.query("""
                SELECT namespace.nspname AS schema_name,
                       relation.relname AS table_name,
                       attribute.attname AS column_name
                FROM pg_constraint constraint_info
                JOIN pg_class relation
                  ON relation.oid = constraint_info.conrelid
                JOIN pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                JOIN LATERAL unnest(constraint_info.conkey) AS key_column(attnum)
                  ON true
                JOIN pg_attribute attribute
                  ON attribute.attrelid = constraint_info.conrelid
                 AND attribute.attnum = key_column.attnum
                WHERE constraint_info.contype = 'f'
                  AND constraint_info.confrelid = 'usuario'::regclass
                ORDER BY namespace.nspname, relation.relname, attribute.attname
                """,
                Map.of(),
                (resultSet, rowNumber) -> new Reference(
                        resultSet.getString("schema_name"),
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name")));
    }

    private String referenceCountSql(Reference reference) {
        return "SELECT count(*) FROM "
                + quote(reference.schema()) + "." + quote(reference.table())
                + " WHERE " + quote(reference.column()) + " = :usuarioId";
    }

    private boolean exists(String sql, Map<String, ?> params) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, params, Boolean.class));
    }

    private long count(String sql, Map<String, ?> params) {
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total == null ? 0L : total;
    }

    private String quote(String identifier) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalStateException("identificador de schema invalido");
        }
        return '"' + identifier + '"';
    }

    private String blockerFor(String table) {
        if ("anuncio".equals(table)) {
            return "POSSUI_ANUNCIOS";
        }
        if ("documento_usuario".equals(table)) {
            return "POSSUI_DOCUMENTOS_KYC";
        }
        if ("movimento_credito".equals(table) || "saldo_credito_usuario".equals(table)) {
            return "POSSUI_SALDO_OU_LEDGER";
        }
        if ("pagamento".equals(table)) {
            return "POSSUI_PAGAMENTOS";
        }
        return "POSSUI_HISTORICO_OPERACIONAL";
    }

    private record Reference(String schema, String table, String column) {
    }

    public record TechnicalDeletionResult(
            int credentials,
            int sessions,
            int tokens,
            int roles,
            int favorites,
            int wizardProgress,
            int balances,
            int outboxEvents) {
    }

    public record DependencyAnalysis(
            boolean contaStaff,
            boolean importado,
            boolean operacaoConcorrente,
            long vinculosDuraveis,
            List<String> tiposVinculo) {

        public boolean exigeAnonimizacao() {
            return importado || vinculosDuraveis > 0;
        }
    }
}
