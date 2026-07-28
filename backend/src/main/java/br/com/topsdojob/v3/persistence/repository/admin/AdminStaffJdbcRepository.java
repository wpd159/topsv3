package br.com.topsdojob.v3.persistence.repository.admin;

import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Historico;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Indicadores;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Permissao;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminStaffJdbcRepository {
  private static final String ROLE = """
      LEFT JOIN LATERAL (
        SELECT pu.papel
        FROM papel_usuario pu
        WHERE pu.usuario_id = u.id
        ORDER BY CASE pu.papel
          WHEN 'ADMIN' THEN 1
          WHEN 'MODERADOR' THEN 2
          ELSE 3
        END
        LIMIT 1
      ) papel_atual ON true
      """;

  private static final String SELECT = """
      SELECT
        u.id,
        u.nome,
        u.email_normalizado,
        coalesce(papel_atual.papel, 'SEM_PAPEL') AS papel,
        u.status,
        u.criado_em,
        u.atualizado_em,
        u.versao,
        coalesce(c.precisa_redefinir, true) AS acesso_pendente
      FROM usuario u
      """ + ROLE + """
      LEFT JOIN credencial_usuario c ON c.usuario_id = u.id
      """;

  private static final String WHERE = """
      WHERE u.tipo_conta = 'STAFF'
        AND (
          CAST(:termo AS text) IS NULL
          OR unaccent(lower(coalesce(u.nome, ''))) LIKE unaccent(:termoLike)
          OR lower(coalesce(u.email_normalizado, '')) LIKE :termoLike
        )
        AND (CAST(:papel AS text) IS NULL OR papel_atual.papel = :papel)
        AND (
          CAST(:ativo AS boolean) IS NULL
          OR (:ativo = true AND u.status = 'ATIVO')
          OR (:ativo = false AND u.status <> 'ATIVO')
        )
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AdminStaffJdbcRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Resumo> listar(
      String termo,
      String papel,
      Boolean ativo,
      String ordenacao,
      int pagina,
      int tamanho) {
    Map<String, Object> parametros = parametros(termo, papel, ativo);
    parametros.put("limite", tamanho);
    parametros.put("offset", (long) pagina * tamanho);
    String order = "ANTIGOS".equals(ordenacao)
        ? " ORDER BY u.criado_em ASC, u.id ASC "
        : " ORDER BY u.criado_em DESC, u.id ASC ";
    return jdbc.query(
        SELECT + WHERE + order + " LIMIT :limite OFFSET :offset",
        parametros,
        (rs, row) -> resumo(rs));
  }

  public long contar(String termo, String papel, Boolean ativo) {
    Long total = jdbc.queryForObject(
        "SELECT count(*) FROM usuario u " + ROLE + WHERE,
        parametros(termo, papel, ativo),
        Long.class);
    return total == null ? 0L : total;
  }

  public Optional<Resumo> detalhar(UUID id) {
    List<Resumo> rows = jdbc.query(
        SELECT + " WHERE u.id = :id AND u.tipo_conta = 'STAFF'",
        Map.of("id", id),
        (rs, row) -> resumo(rs));
    return rows.stream().findFirst();
  }

  public Indicadores indicadores() {
    return jdbc.queryForObject(
        """
        SELECT
          count(DISTINCT u.id) AS total,
          count(DISTINCT u.id) FILTER (WHERE u.status = 'ATIVO') AS ativos,
          count(DISTINCT u.id) FILTER (WHERE u.status <> 'ATIVO') AS inativos,
          count(DISTINCT u.id) FILTER (WHERE pu.papel = 'ADMIN') AS administradores,
          count(DISTINCT u.id) FILTER (WHERE pu.papel = 'MODERADOR') AS moderadores
        FROM usuario u
        LEFT JOIN papel_usuario pu
          ON pu.usuario_id = u.id
         AND pu.papel IN ('ADMIN', 'MODERADOR')
        WHERE u.tipo_conta = 'STAFF'
        """,
        Map.of(),
        (rs, row) -> new Indicadores(
            rs.getLong("total"),
            rs.getLong("ativos"),
            rs.getLong("inativos"),
            rs.getLong("administradores"),
            rs.getLong("moderadores")));
  }

  public List<Permissao> permissoes(UUID usuarioId) {
    return jdbc.query(
        """
        SELECT DISTINCT p.codigo, p.descricao
        FROM papel_usuario pu
        JOIN papel_permissao pp ON pp.papel = pu.papel
        JOIN permissao p ON p.id = pp.permissao_id
        WHERE pu.usuario_id = :id
        ORDER BY p.descricao, p.codigo
        """,
        Map.of("id", usuarioId),
        (rs, row) -> new Permissao(rs.getString("codigo"), rs.getString("descricao")));
  }

  public List<Historico> historico(UUID usuarioId) {
    return jdbc.query(
        """
        SELECT a.id, a.acao, ator.nome AS ator_nome, a.criado_em, a.request_id
        FROM auditoria_evento a
        LEFT JOIN usuario ator ON ator.id = a.ator_usuario_id
        WHERE a.recurso_tipo = 'STAFF'
          AND a.recurso_id = :id
        ORDER BY a.criado_em DESC, a.id
        LIMIT 100
        """,
        Map.of("id", usuarioId),
        (rs, row) -> new Historico(
            rs.getObject("id", UUID.class),
            rs.getString("acao"),
            acaoRotulo(rs.getString("acao")),
            rs.getString("ator_nome"),
            rs.getObject("criado_em", OffsetDateTime.class),
            rs.getString("request_id")));
  }

  public List<UUID> bloquearAdministradoresAtivos() {
    return jdbc.query(
        """
        SELECT u.id
        FROM usuario u
        JOIN papel_usuario pu ON pu.usuario_id = u.id AND pu.papel = 'ADMIN'
        WHERE u.tipo_conta = 'STAFF'
          AND u.status = 'ATIVO'
        ORDER BY u.id
        FOR UPDATE OF u
        """,
        Map.of(),
        (rs, row) -> rs.getObject("id", UUID.class));
  }

  private Map<String, Object> parametros(String termo, String papel, Boolean ativo) {
    String normalizado = termo == null || termo.isBlank() ? null : termo.trim().toLowerCase(java.util.Locale.ROOT);
    Map<String, Object> parametros = new HashMap<>();
    parametros.put("termo", normalizado);
    parametros.put("termoLike", normalizado == null ? "" : "%" + normalizado + "%");
    parametros.put("papel", papel);
    parametros.put("ativo", ativo);
    return parametros;
  }

  private Resumo resumo(java.sql.ResultSet rs) throws java.sql.SQLException {
    String papel = rs.getString("papel");
    String status = rs.getString("status");
    return new Resumo(
        rs.getObject("id", UUID.class),
        rs.getString("nome"),
        rs.getString("email_normalizado"),
        papel,
        papelRotulo(papel),
        status,
        statusRotulo(status),
        "ATIVO".equals(status),
        rs.getBoolean("acesso_pendente"),
        rs.getObject("criado_em", OffsetDateTime.class),
        rs.getObject("atualizado_em", OffsetDateTime.class),
        rs.getInt("versao"));
  }

  private String papelRotulo(String papel) {
    return switch (papel) {
      case "ADMIN" -> "Administrador";
      case "MODERADOR" -> "Moderador";
      case "COMERCIAL" -> "Comercial histórico sem acesso";
      default -> "Sem acesso administrativo";
    };
  }

  private String statusRotulo(String status) {
    return switch (status) {
      case "ATIVO" -> "Ativo";
      case "DESATIVADO" -> "Inativo";
      case "SUSPENSO" -> "Suspenso";
      case "PENDENTE" -> "Pendente";
      default -> "Sem acesso";
    };
  }

  private String acaoRotulo(String acao) {
    return switch (acao) {
      case "STAFF_CRIAR" -> "Conta de staff criada";
      case "STAFF_ATUALIZAR" -> "Dados ou papel atualizados";
      case "STAFF_ATIVAR" -> "Conta ativada";
      case "STAFF_DESATIVAR" -> "Conta desativada";
      default -> "Alteração administrativa";
    };
  }
}
