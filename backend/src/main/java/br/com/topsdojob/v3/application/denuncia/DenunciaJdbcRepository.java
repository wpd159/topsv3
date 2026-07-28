package br.com.topsdojob.v3.application.denuncia;

import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Indicadores;
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
public class DenunciaJdbcRepository {

    private static final String SELECT_BASE = """
            SELECT
              d.id,
              d.anuncio_id,
              d.denunciante_usuario_id,
              d.denunciante_contexto_hash,
              d.motivo,
              d.descricao_resumida,
              d.status,
              d.providencia_resumida,
              d.responsavel_usuario_id,
              d.idempotency_key,
              d.request_id,
              d.ip_hash,
              d.user_agent_hash,
              d.criado_em,
              d.atualizado_em,
              d.decidido_em,
              d.versao,
              a.titulo AS anuncio_titulo,
              a.slug AS anuncio_slug,
              a.status AS anuncio_status,
              a.status_moderacao AS anuncio_status_moderacao,
              denunciante.nome AS denunciante_nome,
              denunciante.email_normalizado AS denunciante_email,
              responsavel.nome AS responsavel_nome
            FROM denuncia_anuncio d
            JOIN anuncio a ON a.id = d.anuncio_id
            LEFT JOIN usuario denunciante ON denunciante.id = d.denunciante_usuario_id
            LEFT JOIN usuario responsavel ON responsavel.id = d.responsavel_usuario_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DenunciaJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int inserir(
            UUID id,
            UUID anuncioId,
            UUID denuncianteUsuarioId,
            String contextoHash,
            String motivo,
            String descricao,
            String idempotencyKey,
            String requestId,
            String ipHash,
            String userAgentHash,
            OffsetDateTime agora) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("anuncioId", anuncioId);
        params.put("denuncianteUsuarioId", denuncianteUsuarioId);
        params.put("contextoHash", contextoHash);
        params.put("motivo", motivo);
        params.put("descricao", descricao);
        params.put("idempotencyKey", idempotencyKey);
        params.put("requestId", requestId);
        params.put("ipHash", ipHash);
        params.put("userAgentHash", userAgentHash);
        params.put("agora", agora);
        return jdbc.update("""
                INSERT INTO denuncia_anuncio (
                  id, anuncio_id, denunciante_usuario_id, denunciante_contexto_hash,
                  motivo, descricao_resumida, status, idempotency_key, request_id,
                  ip_hash, user_agent_hash, criado_em, atualizado_em, versao
                )
                VALUES (
                  :id, :anuncioId, :denuncianteUsuarioId, :contextoHash,
                  :motivo, :descricao, 'PENDENTE', :idempotencyKey, :requestId,
                  :ipHash, :userAgentHash, :agora, :agora, 0
                )
                ON CONFLICT (anuncio_id, denunciante_contexto_hash, idempotency_key)
                DO NOTHING
                """, params);
    }

    public Optional<DenunciaRow> porIdempotencia(
            UUID anuncioId,
            String contextoHash,
            String idempotencyKey) {
        return primeiro(
                SELECT_BASE + """
                        WHERE d.anuncio_id = :anuncioId
                          AND d.denunciante_contexto_hash = :contextoHash
                          AND d.idempotency_key = :idempotencyKey
                        """,
                Map.of(
                        "anuncioId", anuncioId,
                        "contextoHash", contextoHash,
                        "idempotencyKey", idempotencyKey));
    }

    public Optional<DenunciaRow> porId(UUID id) {
        return primeiro(SELECT_BASE + " WHERE d.id = :id ", Map.of("id", id));
    }

    public Optional<DenunciaRow> porIdComLock(UUID id) {
        List<DenunciaRow> rows = jdbc.query("""
                SELECT
                  d.id, d.anuncio_id, d.denunciante_usuario_id, d.denunciante_contexto_hash,
                  d.motivo, d.descricao_resumida, d.status, d.providencia_resumida,
                  d.responsavel_usuario_id, d.idempotency_key, d.request_id,
                  d.ip_hash, d.user_agent_hash, d.criado_em, d.atualizado_em,
                  d.decidido_em, d.versao,
                  a.titulo AS anuncio_titulo, a.slug AS anuncio_slug,
                  a.status AS anuncio_status, a.status_moderacao AS anuncio_status_moderacao,
                  denunciante.nome AS denunciante_nome,
                  denunciante.email_normalizado AS denunciante_email,
                  responsavel.nome AS responsavel_nome
                FROM denuncia_anuncio d
                JOIN anuncio a ON a.id = d.anuncio_id
                LEFT JOIN usuario denunciante ON denunciante.id = d.denunciante_usuario_id
                LEFT JOIN usuario responsavel ON responsavel.id = d.responsavel_usuario_id
                WHERE d.id = :id
                FOR UPDATE OF d
                """, Map.of("id", id), this::mapear);
        return rows.stream().findFirst();
    }

    public List<DenunciaRow> listar(
            String termo,
            String motivo,
            String status,
            OffsetDateTime inicio,
            OffsetDateTime fim,
            int limite,
            long offset) {
        Map<String, Object> params = filtros(termo, motivo, status, inicio, fim);
        params.put("limite", limite);
        params.put("offset", offset);
        return jdbc.query(SELECT_BASE + """
                WHERE (
                  CAST(:termo AS text) IS NULL
                  OR unaccent(lower(coalesce(a.titulo, ''))) LIKE unaccent(:termoLike)
                  OR lower(coalesce(a.slug, '')) LIKE :termoLike
                  OR CAST(d.id AS text) LIKE :termoLike
                  OR CAST(a.id AS text) LIKE :termoLike
                )
                  AND (CAST(:motivo AS text) IS NULL OR d.motivo = :motivo)
                  AND (CAST(:status AS text) IS NULL OR d.status = :status)
                  AND (CAST(:inicio AS timestamptz) IS NULL OR d.criado_em >= :inicio)
                  AND (CAST(:fim AS timestamptz) IS NULL OR d.criado_em < :fim)
                ORDER BY d.criado_em DESC, d.id
                LIMIT :limite OFFSET :offset
                """, params, this::mapear);
    }

    public long contar(
            String termo,
            String motivo,
            String status,
            OffsetDateTime inicio,
            OffsetDateTime fim) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM denuncia_anuncio d
                JOIN anuncio a ON a.id = d.anuncio_id
                WHERE (
                  CAST(:termo AS text) IS NULL
                  OR unaccent(lower(coalesce(a.titulo, ''))) LIKE unaccent(:termoLike)
                  OR lower(coalesce(a.slug, '')) LIKE :termoLike
                  OR CAST(d.id AS text) LIKE :termoLike
                  OR CAST(a.id AS text) LIKE :termoLike
                )
                  AND (CAST(:motivo AS text) IS NULL OR d.motivo = :motivo)
                  AND (CAST(:status AS text) IS NULL OR d.status = :status)
                  AND (CAST(:inicio AS timestamptz) IS NULL OR d.criado_em >= :inicio)
                  AND (CAST(:fim AS timestamptz) IS NULL OR d.criado_em < :fim)
                """, filtros(termo, motivo, status, inicio, fim), Long.class);
        return total == null ? 0 : total;
    }

    public Indicadores indicadores() {
        return jdbc.queryForObject("""
                SELECT
                  count(*) AS total,
                  count(*) FILTER (WHERE status = 'PENDENTE') AS pendentes,
                  count(*) FILTER (WHERE status = 'PUNIDA') AS punidas,
                  count(*) FILTER (WHERE status = 'IGNORADA') AS ignoradas
                FROM denuncia_anuncio
                """, Map.of(), (rs, rowNum) -> new Indicadores(
                rs.getLong("total"),
                rs.getLong("pendentes"),
                rs.getLong("punidas"),
                rs.getLong("ignoradas")));
    }

    public void atualizarStatus(
            UUID id,
            String status,
            String providencia,
            UUID responsavelId,
            OffsetDateTime agora) {
        jdbc.update("""
                UPDATE denuncia_anuncio
                SET status = :status,
                    providencia_resumida = :providencia,
                    responsavel_usuario_id = :responsavelId,
                    decidido_em = :agora,
                    atualizado_em = :agora,
                    versao = versao + 1
                WHERE id = :id
                """, Map.of(
                "id", id,
                "status", status,
                "providencia", providencia,
                "responsavelId", responsavelId,
                "agora", agora));
    }

    public List<HistoricoRow> historico(UUID denunciaId) {
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
                WHERE ae.recurso_tipo = 'DENUNCIA_ANUNCIO'
                  AND ae.recurso_id = :denunciaId
                ORDER BY ae.criado_em, ae.id
                """, Map.of("denunciaId", denunciaId), (rs, rowNum) -> new HistoricoRow(
                rs.getObject("id", UUID.class),
                rs.getString("acao"),
                rs.getString("status"),
                rs.getString("providencia"),
                rs.getString("ator_nome"),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getString("request_id")));
    }

    private Optional<DenunciaRow> primeiro(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, this::mapear).stream().findFirst();
    }

    private Map<String, Object> filtros(
            String termo,
            String motivo,
            String status,
            OffsetDateTime inicio,
            OffsetDateTime fim) {
        String termoSeguro = termo == null || termo.isBlank() ? null : termo.trim().toLowerCase();
        Map<String, Object> params = new HashMap<>();
        params.put("termo", termoSeguro);
        params.put("termoLike", termoSeguro == null ? "" : "%" + termoSeguro + "%");
        params.put("motivo", motivo);
        params.put("status", status);
        params.put("inicio", inicio);
        params.put("fim", fim);
        return params;
    }

    private DenunciaRow mapear(ResultSet rs, int rowNum) throws SQLException {
        return new DenunciaRow(
                rs.getObject("id", UUID.class),
                rs.getObject("anuncio_id", UUID.class),
                rs.getObject("denunciante_usuario_id", UUID.class),
                rs.getString("denunciante_contexto_hash"),
                rs.getString("motivo"),
                rs.getString("descricao_resumida"),
                rs.getString("status"),
                rs.getString("providencia_resumida"),
                rs.getObject("responsavel_usuario_id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("request_id"),
                rs.getString("ip_hash"),
                rs.getString("user_agent_hash"),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getObject("atualizado_em", OffsetDateTime.class),
                rs.getObject("decidido_em", OffsetDateTime.class),
                rs.getInt("versao"),
                rs.getString("anuncio_titulo"),
                rs.getString("anuncio_slug"),
                rs.getString("anuncio_status"),
                rs.getString("anuncio_status_moderacao"),
                rs.getString("denunciante_nome"),
                rs.getString("denunciante_email"),
                rs.getString("responsavel_nome"));
    }

    public record DenunciaRow(
            UUID id,
            UUID anuncioId,
            UUID denuncianteUsuarioId,
            String contextoHash,
            String motivo,
            String descricao,
            String status,
            String providencia,
            UUID responsavelId,
            String idempotencyKey,
            String requestId,
            String ipHash,
            String userAgentHash,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            OffsetDateTime decididaEm,
            int versao,
            String anuncioTitulo,
            String anuncioSlug,
            String anuncioStatus,
            String anuncioStatusModeracao,
            String denuncianteNome,
            String denuncianteEmail,
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
