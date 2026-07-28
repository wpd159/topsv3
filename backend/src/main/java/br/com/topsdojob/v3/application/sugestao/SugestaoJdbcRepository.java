package br.com.topsdojob.v3.application.sugestao;

import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Indicadores;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SugestaoJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SugestaoJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SugestaoRow> porIdempotencia(UUID usuarioId, String chave) {
        return primeiro("""
                SELECT s.*, u.nome AS usuario_nome, u.email_normalizado AS usuario_email,
                       responsavel.nome AS responsavel_nome
                FROM feedback_sugestao s
                JOIN usuario u ON u.id = s.usuario_id
                LEFT JOIN usuario responsavel ON responsavel.id = s.responsavel_usuario_id
                WHERE s.usuario_id = :usuarioId AND s.idempotency_key = :chave
                """, Map.of("usuarioId", usuarioId, "chave", chave));
    }

    public int inserir(
            UUID id,
            UUID usuarioId,
            String tipo,
            String titulo,
            String descricao,
            String chave,
            String requestId,
            OffsetDateTime agora) {
        return jdbc.update("""
                INSERT INTO feedback_sugestao (
                  id, usuario_id, tipo, titulo, descricao_resumida, status,
                  idempotency_key, request_id, criado_em, atualizado_em
                ) VALUES (
                  :id, :usuarioId, :tipo, :titulo, :descricao, 'PENDENTE',
                  :chave, :requestId, :agora, :agora
                )
                ON CONFLICT (usuario_id, idempotency_key) DO NOTHING
                """, Map.of(
                "id", id,
                "usuarioId", usuarioId,
                "tipo", tipo,
                "titulo", titulo,
                "descricao", descricao,
                "chave", chave,
                "requestId", requestId,
                "agora", agora));
    }

    public List<SugestaoRow> listar(
            String termo,
            String status,
            int limite,
            long offset) {
        Map<String, Object> params = filtros(termo, status);
        params.put("limite", limite);
        params.put("offset", offset);
        return jdbc.query("""
                SELECT s.*, u.nome AS usuario_nome, u.email_normalizado AS usuario_email,
                       responsavel.nome AS responsavel_nome
                FROM feedback_sugestao s
                JOIN usuario u ON u.id = s.usuario_id
                LEFT JOIN usuario responsavel ON responsavel.id = s.responsavel_usuario_id
                WHERE (
                  CAST(:termo AS text) IS NULL
                  OR unaccent(lower(s.titulo)) LIKE unaccent(:termoLike)
                  OR unaccent(lower(s.descricao_resumida)) LIKE unaccent(:termoLike)
                  OR CAST(s.id AS text) LIKE :termoLike
                )
                  AND (CAST(:status AS text) IS NULL OR s.status = :status)
                ORDER BY s.criado_em DESC, s.id
                LIMIT :limite OFFSET :offset
                """, params, this::mapear);
    }

    public long contar(String termo, String status) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM feedback_sugestao s
                WHERE (
                  CAST(:termo AS text) IS NULL
                  OR unaccent(lower(s.titulo)) LIKE unaccent(:termoLike)
                  OR unaccent(lower(s.descricao_resumida)) LIKE unaccent(:termoLike)
                  OR CAST(s.id AS text) LIKE :termoLike
                )
                  AND (CAST(:status AS text) IS NULL OR s.status = :status)
                """, filtros(termo, status), Long.class);
        return total == null ? 0 : total;
    }

    public Indicadores indicadores() {
        return jdbc.queryForObject("""
                SELECT
                  count(*) AS total,
                  count(*) FILTER (WHERE status = 'PENDENTE') AS novas,
                  count(*) FILTER (WHERE status = 'EM_ANALISE') AS em_analise,
                  count(*) FILTER (WHERE status = 'RESOLVIDO') AS resolvidas,
                  count(*) FILTER (WHERE status = 'RECUSADO') AS recusadas
                FROM feedback_sugestao
                """, Map.of(), (rs, rowNum) -> new Indicadores(
                rs.getLong("total"),
                rs.getLong("novas"),
                rs.getLong("em_analise"),
                rs.getLong("resolvidas"),
                rs.getLong("recusadas")));
    }

    public Optional<SugestaoRow> porId(UUID id) {
        return primeiro("""
                SELECT s.*, u.nome AS usuario_nome, u.email_normalizado AS usuario_email,
                       responsavel.nome AS responsavel_nome
                FROM feedback_sugestao s
                JOIN usuario u ON u.id = s.usuario_id
                LEFT JOIN usuario responsavel ON responsavel.id = s.responsavel_usuario_id
                WHERE s.id = :id
                """, Map.of("id", id));
    }

    public Optional<SugestaoRow> porIdComLock(UUID id) {
        return primeiro("""
                SELECT s.*, u.nome AS usuario_nome, u.email_normalizado AS usuario_email,
                       responsavel.nome AS responsavel_nome
                FROM feedback_sugestao s
                JOIN usuario u ON u.id = s.usuario_id
                LEFT JOIN usuario responsavel ON responsavel.id = s.responsavel_usuario_id
                WHERE s.id = :id
                FOR UPDATE OF s
                """, Map.of("id", id));
    }

    public int atualizarStatus(
            UUID id,
            String status,
            String providencia,
            UUID responsavelId,
            OffsetDateTime decididoEm,
            OffsetDateTime agora) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("status", status);
        params.put("providencia", providencia);
        params.put("responsavelId", responsavelId);
        params.put("decididoEm", decididoEm);
        params.put("agora", agora);
        return jdbc.update("""
                UPDATE feedback_sugestao
                SET status = :status,
                    providencia_resumida = :providencia,
                    responsavel_usuario_id = :responsavelId,
                    decidido_em = :decididoEm,
                    atualizado_em = :agora,
                    versao = versao + 1
                WHERE id = :id
                """, params);
    }

    public List<HistoricoRow> historico(UUID sugestaoId) {
        return jdbc.query("""
                SELECT
                  ae.id,
                  ae.acao,
                  ae.depois_json ->> 'status' AS status,
                  ae.depois_json ->> 'providencia' AS providencia,
                  coalesce(u.nome, 'Sistema') AS ator_nome,
                  ae.criado_em,
                  ae.request_id
                FROM auditoria_evento ae
                LEFT JOIN usuario u ON u.id = ae.ator_usuario_id
                WHERE ae.recurso_tipo = 'FEEDBACK_SUGESTAO'
                  AND ae.recurso_id = :sugestaoId
                ORDER BY ae.criado_em, ae.id
                """, Map.of("sugestaoId", sugestaoId), (rs, rowNum) -> new HistoricoRow(
                rs.getObject("id", UUID.class),
                rs.getString("acao"),
                rs.getString("status"),
                rs.getString("providencia"),
                rs.getString("ator_nome"),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getString("request_id")));
    }

    private Optional<SugestaoRow> primeiro(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, this::mapear).stream().findFirst();
    }

    private Map<String, Object> filtros(String termo, String status) {
        String termoSeguro = termo == null || termo.isBlank()
                ? null
                : termo.trim().toLowerCase();
        Map<String, Object> params = new HashMap<>();
        params.put("termo", termoSeguro);
        params.put("termoLike", termoSeguro == null ? "" : "%" + termoSeguro + "%");
        params.put("status", status);
        return params;
    }

    private SugestaoRow mapear(ResultSet rs, int rowNum) throws SQLException {
        return new SugestaoRow(
                rs.getObject("id", UUID.class),
                rs.getObject("usuario_id", UUID.class),
                rs.getString("tipo"),
                rs.getString("titulo"),
                rs.getString("descricao_resumida"),
                rs.getString("status"),
                rs.getString("providencia_resumida"),
                rs.getObject("responsavel_usuario_id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("request_id"),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getObject("atualizado_em", OffsetDateTime.class),
                rs.getObject("decidido_em", OffsetDateTime.class),
                rs.getLong("versao"),
                rs.getString("usuario_nome"),
                rs.getString("usuario_email"),
                rs.getString("responsavel_nome"));
    }

    public record SugestaoRow(
            UUID id,
            UUID usuarioId,
            String tipo,
            String titulo,
            String descricao,
            String status,
            String providencia,
            UUID responsavelId,
            String idempotencyKey,
            String requestId,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            OffsetDateTime decididoEm,
            long versao,
            String usuarioNome,
            String usuarioEmail,
            String responsavelNome) {
    }

    public record HistoricoRow(
            UUID id,
            String acao,
            String status,
            String providencia,
            String atorNome,
            OffsetDateTime criadoEm,
            String requestId) {
    }
}
