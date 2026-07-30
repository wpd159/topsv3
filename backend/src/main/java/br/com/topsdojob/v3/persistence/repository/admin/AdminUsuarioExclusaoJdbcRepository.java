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
            new Reference("public", "saldo_credito_usuario", "usuario_id"));
    private static final List<String> BLOCKER_ORDER = List.of(
            "CONTA_STAFF",
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

    public List<String> bloqueios(UUID usuarioId) {
        Map<String, Object> params = Map.of("usuarioId", usuarioId);
        Set<String> bloqueios = new LinkedHashSet<>();

        if (exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM papel_usuario
                  WHERE usuario_id = :usuarioId
                    AND papel <> 'USUARIO'
                )
                """, params)) {
            bloqueios.add("CONTA_STAFF");
        }
        if (exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM importacao_mapeamento
                  WHERE entidade_tipo = 'USUARIO'
                    AND entidade_v3_id = :usuarioId
                )
                """, params)) {
            bloqueios.add("USUARIO_IMPORTADO");
        }
        if (exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM saldo_credito_usuario
                  WHERE usuario_id = :usuarioId
                    AND saldo_atual <> 0
                )
                """, params)) {
            bloqueios.add("POSSUI_SALDO_OU_LEDGER");
        }
        if (exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM outbox_evento
                  WHERE aggregate_tipo = 'USUARIO'
                    AND aggregate_id = :usuarioId
                    AND (
                      tipo_evento NOT IN (
                        'AUTH_CONFIRMACAO_CONTA_SOLICITADA',
                        'AUTH_CONFIRMACAO_CONTA_REENVIADA',
                        'AUTH_RECUPERACAO_SENHA_SOLICITADA'
                      )
                      OR status = 'PROCESSANDO'
                    )
                )
                """, params)) {
            bloqueios.add("POSSUI_HISTORICO_OPERACIONAL");
        }

        for (Reference reference : userReferences()) {
            if (DISPOSABLE_REFERENCES.contains(reference)) {
                continue;
            }
            if (exists(referenceExistsSql(reference), params)) {
                bloqueios.add(blockerFor(reference.table()));
            }
        }

        List<String> ordered = new ArrayList<>(bloqueios);
        ordered.sort(Comparator.comparingInt(code -> {
            int index = BLOCKER_ORDER.indexOf(code);
            return index < 0 ? BLOCKER_ORDER.size() : index;
        }));
        return List.copyOf(ordered);
    }

    public boolean exclusaoConcluida(
            UUID usuarioId,
            UUID atorId,
            String idempotencyHash) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM auditoria_evento
                  WHERE acao = 'USUARIO_EXCLUIDO_FISICAMENTE'
                    AND recurso_tipo = 'USUARIO'
                    AND recurso_id = :usuarioId
                    AND ator_usuario_id = :atorId
                    AND resultado = 'SUCESSO'
                    AND depois_json ->> 'idempotencyHash' = :idempotencyHash
                )
                """, Map.of(
                        "usuarioId", usuarioId,
                        "atorId", atorId,
                        "idempotencyHash", idempotencyHash));
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
                balances,
                outbox);
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

    private String referenceExistsSql(Reference reference) {
        return "SELECT EXISTS (SELECT 1 FROM "
                + quote(reference.schema()) + "." + quote(reference.table())
                + " WHERE " + quote(reference.column()) + " = :usuarioId)";
    }

    private boolean exists(String sql, Map<String, ?> params) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, params, Boolean.class));
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
            int balances,
            int outboxEvents) {
    }
}
