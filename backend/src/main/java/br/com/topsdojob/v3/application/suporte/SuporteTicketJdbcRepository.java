package br.com.topsdojob.v3.application.suporte;

import br.com.topsdojob.v3.application.suporte.SuporteDtos.Indicadores;
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
public class SuporteTicketJdbcRepository {

    private static final String SELECT_TICKET = """
            SELECT
              t.id,
              t.assunto,
              t.categoria,
              t.status,
              t.prioridade,
              t.usuario_id,
              t.responsavel_usuario_id,
              t.criado_em,
              t.atualizado_em,
              t.encerrado_em,
              count(m.id) FILTER (WHERE NOT m.privado_staff) AS total_mensagens,
              count(m.id) FILTER (
                WHERE NOT m.privado_staff AND m.origem = 'STAFF' AND m.nao_lida_usuario
              ) AS nao_lidas
            FROM ticket_suporte t
            LEFT JOIN mensagem_suporte m ON m.ticket_id = t.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SuporteTicketJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int inserirTicket(
            UUID id,
            UUID usuarioId,
            String assunto,
            String categoria,
            String idempotencyKey,
            String requestId,
            OffsetDateTime agora) {
        return jdbc.update("""
                INSERT INTO ticket_suporte (
                  id, usuario_id, assunto, categoria, status, prioridade,
                  criacao_idempotency_key, criado_request_id,
                  criado_em, atualizado_em, versao
                )
                VALUES (
                  :id, :usuarioId, :assunto, :categoria, 'ABERTO', 'MEDIA',
                  :idempotencyKey, :requestId, :agora, :agora, 0
                )
                ON CONFLICT (usuario_id, criacao_idempotency_key)
                WHERE usuario_id IS NOT NULL AND criacao_idempotency_key IS NOT NULL
                DO NOTHING
                """, Map.of(
                "id", id,
                "usuarioId", usuarioId,
                "assunto", assunto,
                "categoria", categoria,
                "idempotencyKey", idempotencyKey,
                "requestId", requestId,
                "agora", agora));
    }

    public int inserirMensagem(
            UUID id,
            UUID ticketId,
            UUID autorId,
            String origem,
            String corpo,
            boolean privadoStaff,
            boolean naoLidaUsuario,
            String idempotencyKey,
            String requestId,
            OffsetDateTime agora) {
        return jdbc.update("""
                INSERT INTO mensagem_suporte (
                  id, ticket_id, autor_usuario_id, origem, corpo_resumido,
                  privado_staff, nao_lida_usuario, idempotency_key, request_id, criado_em
                )
                VALUES (
                  :id, :ticketId, :autorId, :origem, :corpo,
                  :privadoStaff, :naoLidaUsuario, :idempotencyKey, :requestId, :agora
                )
                ON CONFLICT (autor_usuario_id, idempotency_key)
                WHERE autor_usuario_id IS NOT NULL AND idempotency_key IS NOT NULL
                DO NOTHING
                """, Map.of(
                "id", id,
                "ticketId", ticketId,
                "autorId", autorId,
                "origem", origem,
                "corpo", corpo,
                "privadoStaff", privadoStaff,
                "naoLidaUsuario", naoLidaUsuario,
                "idempotencyKey", idempotencyKey,
                "requestId", requestId,
                "agora", agora));
    }

    public Optional<TicketRow> porCriacao(UUID usuarioId, String idempotencyKey) {
        return primeiro(
                SELECT_TICKET + """
                        WHERE t.usuario_id = :usuarioId
                          AND t.criacao_idempotency_key = :idempotencyKey
                        GROUP BY t.id
                        """,
                Map.of("usuarioId", usuarioId, "idempotencyKey", idempotencyKey));
    }

    public Optional<TicketRow> porId(UUID id) {
        return primeiro(
                SELECT_TICKET + " WHERE t.id = :id GROUP BY t.id ",
                Map.of("id", id));
    }

    public Optional<TicketRow> porIdComLock(UUID id) {
        List<TicketRow> rows = jdbc.query("""
                SELECT
                  t.id, t.assunto, t.categoria, t.status, t.prioridade,
                  t.usuario_id, t.responsavel_usuario_id, t.criado_em,
                  t.atualizado_em, t.encerrado_em,
                  (SELECT count(*) FROM mensagem_suporte m WHERE m.ticket_id = t.id) AS total_mensagens,
                  (SELECT count(*) FROM mensagem_suporte m
                   WHERE m.ticket_id = t.id
                     AND NOT m.privado_staff
                     AND m.origem = 'STAFF'
                     AND m.nao_lida_usuario) AS nao_lidas
                FROM ticket_suporte t
                WHERE t.id = :id
                FOR UPDATE
                """, Map.of("id", id), this::mapearTicket);
        return rows.stream().findFirst();
    }

    public List<TicketRow> listarDoUsuario(
            UUID usuarioId,
            String grupo,
            int limite,
            long offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("usuarioId", usuarioId);
        params.put("grupo", grupo);
        params.put("limite", limite);
        params.put("offset", offset);
        return jdbc.query(SELECT_TICKET + """
                WHERE t.usuario_id = :usuarioId
                  AND (
                    :grupo = 'TODOS'
                    OR (:grupo = 'ABERTOS' AND t.status IN ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO'))
                    OR (:grupo = 'ENCERRADOS' AND t.status IN ('RESOLVIDO', 'ENCERRADO'))
                  )
                GROUP BY t.id
                ORDER BY t.atualizado_em DESC, t.id
                LIMIT :limite OFFSET :offset
                """, params, this::mapearTicket);
    }

    public long contarDoUsuario(UUID usuarioId, String grupo) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM ticket_suporte t
                WHERE t.usuario_id = :usuarioId
                  AND (
                    :grupo = 'TODOS'
                    OR (:grupo = 'ABERTOS' AND t.status IN ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO'))
                    OR (:grupo = 'ENCERRADOS' AND t.status IN ('RESOLVIDO', 'ENCERRADO'))
                  )
                """, Map.of("usuarioId", usuarioId, "grupo", grupo), Long.class);
        return total == null ? 0 : total;
    }

    public List<AdminTicketRow> listarAdmin(
            String termo,
            String categoria,
            String status,
            String ordenacao,
            int limite,
            long offset) {
        Map<String, Object> params = filtrosAdmin(termo, categoria, status);
        params.put("limite", limite);
        params.put("offset", offset);
        String ordem = "ANTIGOS".equals(ordenacao)
                ? " ORDER BY t.criado_em ASC, t.id "
                : " ORDER BY t.atualizado_em DESC, t.id ";
        return jdbc.query("""
                SELECT
                  t.id, t.assunto, t.categoria, t.status, t.prioridade,
                  t.usuario_id, t.responsavel_usuario_id, t.criado_em,
                  t.atualizado_em, t.encerrado_em,
                  count(m.id) AS total_mensagens,
                  count(m.id) FILTER (
                    WHERE NOT m.privado_staff AND m.origem = 'STAFF' AND m.nao_lida_usuario
                  ) AS nao_lidas,
                  u.nome AS usuario_nome,
                  u.email_normalizado AS usuario_email,
                  (
                    SELECT inicial.corpo_resumido
                    FROM mensagem_suporte inicial
                    WHERE inicial.ticket_id = t.id
                    ORDER BY inicial.criado_em, inicial.id
                    LIMIT 1
                  ) AS mensagem_inicial
                FROM ticket_suporte t
                JOIN usuario u ON u.id = t.usuario_id
                LEFT JOIN mensagem_suporte m ON m.ticket_id = t.id
                WHERE (
                  CAST(:termo AS text) IS NULL
                  OR unaccent(lower(coalesce(t.assunto, ''))) LIKE unaccent(:termoLike)
                  OR unaccent(lower(coalesce(u.nome, ''))) LIKE unaccent(:termoLike)
                  OR lower(coalesce(u.email_normalizado, '')) LIKE :termoLike
                  OR CAST(t.id AS text) LIKE :termoLike
                )
                  AND (CAST(:categoria AS text) IS NULL OR t.categoria = :categoria)
                  AND (
                    CAST(:status AS text) IS NULL
                    OR t.status = :status
                    OR (:status = 'ABERTOS' AND t.status = 'ABERTO')
                    OR (:status = 'EM_ANDAMENTO' AND t.status IN ('EM_ATENDIMENTO', 'AGUARDANDO_USUARIO'))
                    OR (:status = 'FECHADOS' AND t.status IN ('RESOLVIDO', 'ENCERRADO'))
                  )
                GROUP BY t.id, u.id
                """ + ordem + " LIMIT :limite OFFSET :offset", params, this::mapearAdmin);
    }

    public long contarAdmin(String termo, String categoria, String status) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM ticket_suporte t
                JOIN usuario u ON u.id = t.usuario_id
                WHERE (
                  CAST(:termo AS text) IS NULL
                  OR unaccent(lower(coalesce(t.assunto, ''))) LIKE unaccent(:termoLike)
                  OR unaccent(lower(coalesce(u.nome, ''))) LIKE unaccent(:termoLike)
                  OR lower(coalesce(u.email_normalizado, '')) LIKE :termoLike
                  OR CAST(t.id AS text) LIKE :termoLike
                )
                  AND (CAST(:categoria AS text) IS NULL OR t.categoria = :categoria)
                  AND (
                    CAST(:status AS text) IS NULL
                    OR t.status = :status
                    OR (:status = 'ABERTOS' AND t.status = 'ABERTO')
                    OR (:status = 'EM_ANDAMENTO' AND t.status IN ('EM_ATENDIMENTO', 'AGUARDANDO_USUARIO'))
                    OR (:status = 'FECHADOS' AND t.status IN ('RESOLVIDO', 'ENCERRADO'))
                  )
                """, filtrosAdmin(termo, categoria, status), Long.class);
        return total == null ? 0 : total;
    }

    public Optional<AdminTicketRow> detalheAdmin(UUID id) {
        List<AdminTicketRow> rows = jdbc.query("""
                SELECT
                  t.id, t.assunto, t.categoria, t.status, t.prioridade,
                  t.usuario_id, t.responsavel_usuario_id, t.criado_em,
                  t.atualizado_em, t.encerrado_em,
                  count(m.id) AS total_mensagens,
                  count(m.id) FILTER (
                    WHERE NOT m.privado_staff AND m.origem = 'STAFF' AND m.nao_lida_usuario
                  ) AS nao_lidas,
                  u.nome AS usuario_nome,
                  u.email_normalizado AS usuario_email,
                  (
                    SELECT inicial.corpo_resumido
                    FROM mensagem_suporte inicial
                    WHERE inicial.ticket_id = t.id
                    ORDER BY inicial.criado_em, inicial.id
                    LIMIT 1
                  ) AS mensagem_inicial
                FROM ticket_suporte t
                JOIN usuario u ON u.id = t.usuario_id
                LEFT JOIN mensagem_suporte m ON m.ticket_id = t.id
                WHERE t.id = :id
                GROUP BY t.id, u.id
                """, Map.of("id", id), this::mapearAdmin);
        return rows.stream().findFirst();
    }

    public List<MensagemRow> mensagens(UUID ticketId, boolean incluirPrivadas) {
        return jdbc.query("""
                SELECT
                  m.id, m.ticket_id, m.autor_usuario_id, m.origem,
                  m.corpo_resumido, m.privado_staff, m.nao_lida_usuario, m.criado_em,
                  coalesce(u.nome, CASE WHEN m.origem = 'STAFF' THEN 'Equipe' ELSE 'Sistema' END) AS remetente
                FROM mensagem_suporte m
                LEFT JOIN usuario u ON u.id = m.autor_usuario_id
                WHERE m.ticket_id = :ticketId
                  AND (:incluirPrivadas OR NOT m.privado_staff)
                ORDER BY m.criado_em, m.id
                """, Map.of("ticketId", ticketId, "incluirPrivadas", incluirPrivadas),
                (rs, rowNum) -> new MensagemRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getObject("autor_usuario_id", UUID.class),
                        rs.getString("origem"),
                        rs.getString("corpo_resumido"),
                        rs.getBoolean("privado_staff"),
                        rs.getBoolean("nao_lida_usuario"),
                        rs.getObject("criado_em", OffsetDateTime.class),
                        rs.getString("remetente")));
    }

    public Optional<MensagemRow> mensagemPorIdempotencia(UUID autorId, String idempotencyKey) {
        List<MensagemRow> rows = jdbc.query("""
                SELECT
                  m.id, m.ticket_id, m.autor_usuario_id, m.origem,
                  m.corpo_resumido, m.privado_staff, m.nao_lida_usuario, m.criado_em,
                  coalesce(u.nome, CASE WHEN m.origem = 'STAFF' THEN 'Equipe' ELSE 'Sistema' END) AS remetente
                FROM mensagem_suporte m
                LEFT JOIN usuario u ON u.id = m.autor_usuario_id
                WHERE m.autor_usuario_id = :autorId
                  AND m.idempotency_key = :idempotencyKey
                """, Map.of("autorId", autorId, "idempotencyKey", idempotencyKey),
                (rs, rowNum) -> new MensagemRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getObject("autor_usuario_id", UUID.class),
                        rs.getString("origem"),
                        rs.getString("corpo_resumido"),
                        rs.getBoolean("privado_staff"),
                        rs.getBoolean("nao_lida_usuario"),
                        rs.getObject("criado_em", OffsetDateTime.class),
                        rs.getString("remetente")));
        return rows.stream().findFirst();
    }

    public void atualizarStatus(
            UUID id,
            String status,
            UUID responsavelId,
            OffsetDateTime encerradoEm,
            OffsetDateTime agora) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("status", status);
        params.put("responsavelId", responsavelId);
        params.put("encerradoEm", encerradoEm);
        params.put("agora", agora);
        jdbc.update("""
                UPDATE ticket_suporte
                SET status = :status,
                    responsavel_usuario_id = coalesce(:responsavelId, responsavel_usuario_id),
                    encerrado_em = :encerradoEm,
                    atualizado_em = :agora,
                    versao = versao + 1
                WHERE id = :id
                """, params);
    }

    public void tocar(UUID id, OffsetDateTime agora) {
        jdbc.update("""
                UPDATE ticket_suporte
                SET atualizado_em = :agora, versao = versao + 1
                WHERE id = :id
                """, Map.of("id", id, "agora", agora));
    }

    public long contarNaoLidasDoUsuario(UUID usuarioId) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM mensagem_suporte m
                JOIN ticket_suporte t ON t.id = m.ticket_id
                WHERE t.usuario_id = :usuarioId
                  AND m.origem = 'STAFF'
                  AND NOT m.privado_staff
                  AND m.nao_lida_usuario
                """, Map.of("usuarioId", usuarioId), Long.class);
        return total == null ? 0 : total;
    }

    public void marcarRespostasComoLidas(UUID ticketId, UUID usuarioId) {
        jdbc.update("""
                UPDATE mensagem_suporte m
                SET nao_lida_usuario = false
                WHERE m.ticket_id = :ticketId
                  AND m.origem = 'STAFF'
                  AND NOT m.privado_staff
                  AND m.nao_lida_usuario
                  AND EXISTS (
                    SELECT 1 FROM ticket_suporte t
                    WHERE t.id = m.ticket_id AND t.usuario_id = :usuarioId
                  )
                """, Map.of("ticketId", ticketId, "usuarioId", usuarioId));
    }

    public Indicadores indicadores() {
        return jdbc.queryForObject("""
                SELECT
                  count(*) AS total,
                  count(*) FILTER (WHERE status = 'ABERTO') AS abertos,
                  count(*) FILTER (WHERE status = 'EM_ATENDIMENTO') AS em_atendimento,
                  count(*) FILTER (WHERE status = 'AGUARDANDO_USUARIO') AS aguardando_usuario,
                  count(*) FILTER (WHERE status = 'RESOLVIDO') AS resolvidos,
                  count(*) FILTER (WHERE status = 'ENCERRADO') AS encerrados,
                  count(*) FILTER (WHERE status IN ('ABERTO', 'EM_ATENDIMENTO')) AS pendentes_equipe
                FROM ticket_suporte
                """, Map.of(), (rs, rowNum) -> new Indicadores(
                rs.getLong("total"),
                rs.getLong("abertos"),
                rs.getLong("em_atendimento"),
                rs.getLong("aguardando_usuario"),
                rs.getLong("resolvidos"),
                rs.getLong("encerrados"),
                rs.getLong("pendentes_equipe")));
    }

    private Optional<TicketRow> primeiro(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, this::mapearTicket).stream().findFirst();
    }

    private Map<String, Object> filtrosAdmin(String termo, String categoria, String status) {
        String termoSeguro = termo == null || termo.isBlank() ? null : termo.trim().toLowerCase();
        Map<String, Object> params = new HashMap<>();
        params.put("termo", termoSeguro);
        params.put("termoLike", termoSeguro == null ? "" : "%" + termoSeguro + "%");
        params.put("categoria", categoria);
        params.put("status", status);
        return params;
    }

    private TicketRow mapearTicket(ResultSet rs, int rowNum) throws SQLException {
        return new TicketRow(
                rs.getObject("id", UUID.class),
                rs.getString("assunto"),
                rs.getString("categoria"),
                rs.getString("status"),
                rs.getString("prioridade"),
                rs.getObject("usuario_id", UUID.class),
                rs.getObject("responsavel_usuario_id", UUID.class),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getObject("atualizado_em", OffsetDateTime.class),
                rs.getObject("encerrado_em", OffsetDateTime.class),
                rs.getLong("total_mensagens"),
                rs.getLong("nao_lidas"));
    }

    private AdminTicketRow mapearAdmin(ResultSet rs, int rowNum) throws SQLException {
        TicketRow ticket = mapearTicket(rs, rowNum);
        return new AdminTicketRow(
                ticket,
                rs.getString("usuario_nome"),
                rs.getString("usuario_email"),
                rs.getString("mensagem_inicial"));
    }

    public record TicketRow(
            UUID id,
            String assunto,
            String categoria,
            String status,
            String prioridade,
            UUID usuarioId,
            UUID responsavelId,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            OffsetDateTime encerradoEm,
            long totalMensagens,
            long naoLidas) {
    }

    public record AdminTicketRow(
            TicketRow ticket,
            String usuarioNome,
            String usuarioEmail,
            String mensagemInicial) {
    }

    public record MensagemRow(
            UUID id,
            UUID ticketId,
            UUID autorId,
            String origem,
            String corpo,
            boolean privadoStaff,
            boolean naoLidaUsuario,
            OffsetDateTime criadoEm,
            String remetente) {
    }
}
