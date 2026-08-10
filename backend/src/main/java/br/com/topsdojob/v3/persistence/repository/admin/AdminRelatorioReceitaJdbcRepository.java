package br.com.topsdojob.v3.persistence.repository.admin;

import br.com.topsdojob.v3.application.admin.pagamentos.RelatorioReceitaFiltro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRelatorioReceitaJdbcRepository {

    private static final String DATA_REFERENCIA = """
            CASE
              WHEN p.status_interno = 'APROVADO' THEN p.aprovado_em
              WHEN p.status_interno IN ('CANCELADO', 'EXPIRADO') THEN coalesce(p.cancelado_em, p.atualizado_em)
              WHEN p.status_interno IN ('ESTORNADO', 'ERRO') THEN p.atualizado_em
              ELSE p.criado_em
            END
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminRelatorioReceitaJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MetricasRow metricas(RelatorioReceitaFiltro filtro) {
        Consulta consulta = consulta(filtro);
        return jdbc.queryForObject("""
                        SELECT
                          coalesce(sum(p.valor) FILTER (WHERE p.status_interno = 'APROVADO'), 0) AS receita_confirmada,
                          count(*) FILTER (WHERE p.status_interno = 'APROVADO') AS pagamentos_confirmados,
                          coalesce(sum(p.quantidade_creditos) FILTER (WHERE p.status_interno = 'APROVADO'), 0) AS creditos_vendidos,
                          count(*) FILTER (WHERE p.status_interno IN ('CRIADO', 'AGUARDANDO_PAGAMENTO')) AS pagamentos_pendentes,
                          count(*) FILTER (WHERE p.status_interno = 'ERRO') AS pagamentos_falhos,
                          count(*) FILTER (WHERE p.status_interno IN ('CANCELADO', 'EXPIRADO')) AS pagamentos_cancelados,
                          count(*) FILTER (WHERE p.status_interno = 'ESTORNADO') AS pagamentos_estornados
                        FROM pagamento p
                        JOIN usuario u ON u.id = p.usuario_id
                        LEFT JOIN plano_credito pc ON pc.id = p.plano_credito_id
                        """
                                + consulta.where(),
                        consulta.parametros(),
                        (rs, row) -> new MetricasRow(
                                rs.getBigDecimal("receita_confirmada"),
                                rs.getLong("pagamentos_confirmados"),
                                rs.getLong("creditos_vendidos"),
                                rs.getLong("pagamentos_pendentes"),
                                rs.getLong("pagamentos_falhos"),
                                rs.getLong("pagamentos_cancelados"),
                                rs.getLong("pagamentos_estornados")));
    }

    public List<PontoDiarioRow> evolucaoDiaria(RelatorioReceitaFiltro filtro) {
        Consulta consulta = consulta(filtro);
        return jdbc.query("""
                        SELECT
                          (p.aprovado_em AT TIME ZONE 'America/Sao_Paulo')::date AS data_referencia,
                          sum(p.valor) AS receita_confirmada,
                          count(*) AS pagamentos_confirmados
                        FROM pagamento p
                        JOIN usuario u ON u.id = p.usuario_id
                        LEFT JOIN plano_credito pc ON pc.id = p.plano_credito_id
                        """
                                + consulta.where()
                                + " AND p.status_interno = 'APROVADO'"
                                + " GROUP BY data_referencia ORDER BY data_referencia",
                        consulta.parametros(),
                        (rs, row) -> new PontoDiarioRow(
                                rs.getObject("data_referencia", LocalDate.class),
                                rs.getBigDecimal("receita_confirmada"),
                                rs.getLong("pagamentos_confirmados")));
    }

    public List<ProdutoRow> distribuicaoPorProduto(RelatorioReceitaFiltro filtro) {
        Consulta consulta = consulta(filtro);
        return jdbc.query("""
                        SELECT
                          coalesce(pc.codigo, 'SEM_PACOTE') AS codigo,
                          coalesce(pc.nome, 'Pagamento sem pacote vinculado') AS nome,
                          sum(p.valor) AS receita_confirmada,
                          count(*) AS pagamentos_confirmados,
                          sum(p.quantidade_creditos) AS creditos_vendidos
                        FROM pagamento p
                        JOIN usuario u ON u.id = p.usuario_id
                        LEFT JOIN plano_credito pc ON pc.id = p.plano_credito_id
                        """
                                + consulta.where()
                                + " AND p.status_interno = 'APROVADO'"
                                + " GROUP BY pc.codigo, pc.nome"
                                + " ORDER BY receita_confirmada DESC, codigo",
                        consulta.parametros(),
                        (rs, row) -> new ProdutoRow(
                                rs.getString("codigo"),
                                rs.getString("nome"),
                                rs.getBigDecimal("receita_confirmada"),
                                rs.getLong("pagamentos_confirmados"),
                                rs.getLong("creditos_vendidos")));
    }

    public ConciliacaoRow conciliacao(RelatorioReceitaFiltro filtro) {
        Consulta consulta = consulta(filtro);
        return jdbc.queryForObject("""
                        SELECT
                          count(*) FILTER (
                            WHERE p.status_interno = 'APROVADO'
                              AND (c.id IS NULL OR c.status <> 'CONCILIADO')
                          ) AS sem_conciliacao,
                          count(*) FILTER (
                            WHERE p.status_interno = 'APROVADO'
                              AND c.id IS NOT NULL
                              AND (
                                c.status = 'DIVERGENTE'
                                OR c.valor_confirmado <> p.valor
                                OR c.creditos_confirmados <> p.quantidade_creditos
                              )
                          ) AS divergentes,
                          count(*) FILTER (
                            WHERE p.status_interno = 'APROVADO'
                              AND c.status = 'CONCILIADO'
                              AND c.movimento_credito_id IS NULL
                          ) AS sem_movimento
                        FROM pagamento p
                        JOIN usuario u ON u.id = p.usuario_id
                        LEFT JOIN plano_credito pc ON pc.id = p.plano_credito_id
                        LEFT JOIN pagamento_conciliacao c ON c.pagamento_id = p.id
                        """
                                + consulta.where(),
                        consulta.parametros(),
                        (rs, row) -> new ConciliacaoRow(
                                rs.getLong("sem_conciliacao"),
                                rs.getLong("divergentes"),
                                rs.getLong("sem_movimento")));
    }

    public PaginaRow transacoes(
            RelatorioReceitaFiltro filtro,
            int pagina,
            int tamanho,
            String ordenacao) {
        Consulta consulta = consulta(filtro);
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM pagamento p"
                        + " JOIN usuario u ON u.id = p.usuario_id"
                        + " LEFT JOIN plano_credito pc ON pc.id = p.plano_credito_id "
                        + consulta.where(),
                consulta.parametros(),
                Long.class);
        MapSqlParameterSource parametros = copiar(consulta.parametros())
                .addValue("limite", tamanho)
                .addValue("offset", (long) pagina * tamanho);
        String ordem = switch (ordenacao) {
            case "MAIS_ANTIGOS" -> "data_referencia ASC, p.id";
            case "MAIOR_VALOR" -> "p.valor DESC, data_referencia DESC, p.id";
            case "MENOR_VALOR" -> "p.valor ASC, data_referencia DESC, p.id";
            default -> "data_referencia DESC, p.id";
        };
        List<TransacaoRow> itens = jdbc.query("""
                        SELECT
                          p.id,
                          u.id AS usuario_id,
                          """
                                + DATA_REFERENCIA
                                + """
                           AS data_referencia,
                          u.nome AS usuario_nome,
                          u.email_normalizado AS usuario_email,
                          u.telefone_normalizado AS usuario_whatsapp,
                          coalesce(pc.codigo, 'SEM_PACOTE') AS produto_codigo,
                          coalesce(pc.nome, 'Pagamento sem pacote vinculado') AS produto_nome,
                          p.valor,
                          p.moeda,
                          p.quantidade_creditos,
                          p.status_interno,
                          p.metodo,
                          coalesce(p.txid, p.identificador_provedor) AS identificador_externo
                        FROM pagamento p
                        JOIN usuario u ON u.id = p.usuario_id
                        LEFT JOIN plano_credito pc ON pc.id = p.plano_credito_id
                        """
                                + consulta.where()
                                + " ORDER BY " + ordem
                                + " LIMIT :limite OFFSET :offset",
                        parametros,
                        (rs, row) -> new TransacaoRow(
                                rs.getObject("id", UUID.class),
                                rs.getObject("usuario_id", UUID.class),
                                rs.getObject("data_referencia", OffsetDateTime.class),
                                rs.getString("usuario_nome"),
                                rs.getString("usuario_email"),
                                rs.getString("usuario_whatsapp"),
                                rs.getString("produto_codigo"),
                                rs.getString("produto_nome"),
                                rs.getBigDecimal("valor"),
                                rs.getString("moeda"),
                                rs.getObject("quantidade_creditos", Integer.class),
                                rs.getString("status_interno"),
                                rs.getString("metodo"),
                                rs.getString("identificador_externo")));
        MetricasRow metricas = metricas(filtro);
        return new PaginaRow(
                itens,
                total,
                metricas.receitaConfirmada(),
                metricas.pagamentosConfirmados());
    }

    private Consulta consulta(RelatorioReceitaFiltro filtro) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("inicio", filtro.instanteInicio())
                .addValue("fim", filtro.instanteFimExclusivo());
        List<String> condicoes = new ArrayList<>();
        condicoes.add("(" + DATA_REFERENCIA + ") >= :inicio");
        condicoes.add("(" + DATA_REFERENCIA + ") < :fim");
        switch (filtro.status()) {
            case CONFIRMADO -> condicoes.add("p.status_interno = 'APROVADO'");
            case PENDENTE -> condicoes.add("p.status_interno IN ('CRIADO', 'AGUARDANDO_PAGAMENTO')");
            case FALHO -> condicoes.add("p.status_interno = 'ERRO'");
            case CANCELADO -> condicoes.add("p.status_interno = 'CANCELADO'");
            case EXPIRADO -> condicoes.add("p.status_interno = 'EXPIRADO'");
            case ESTORNADO -> condicoes.add("p.status_interno = 'ESTORNADO'");
            case LEGADO -> condicoes.add("p.status_interno = 'LEGADO'");
            case TODOS -> {
            }
        }
        if (filtro.metodo() != RelatorioReceitaFiltro.Metodo.TODOS) {
            condicoes.add("p.metodo = :metodo");
            parametros.addValue("metodo", filtro.metodo().name());
        }
        if (filtro.usuario() != null) {
            condicoes.add("""
                    (
                      lower(coalesce(u.nome, '')) LIKE :usuarioBusca
                      OR cast(u.id AS text) = :usuarioExato
                    )
                    """);
            parametros
                    .addValue("usuarioBusca", "%" + filtro.usuario().toLowerCase() + "%")
                    .addValue("usuarioExato", filtro.usuario().toLowerCase());
        }
        if (filtro.produto() != null) {
            condicoes.add("""
                    (
                      lower(coalesce(pc.codigo, '')) LIKE :produtoBusca
                      OR lower(coalesce(pc.nome, '')) LIKE :produtoBusca
                      OR cast(pc.id AS text) = :produtoExato
                    )
                    """);
            parametros
                    .addValue("produtoBusca", "%" + filtro.produto().toLowerCase() + "%")
                    .addValue("produtoExato", filtro.produto().toLowerCase());
        }
        return new Consulta(" WHERE " + String.join(" AND ", condicoes), parametros);
    }

    private static MapSqlParameterSource copiar(MapSqlParameterSource origem) {
        MapSqlParameterSource copia = new MapSqlParameterSource();
        for (String nome : origem.getParameterNames()) {
            copia.addValue(nome, origem.getValue(nome));
        }
        return copia;
    }

    private record Consulta(String where, MapSqlParameterSource parametros) {
    }

    public record MetricasRow(
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados,
            long creditosVendidos,
            long pagamentosPendentes,
            long pagamentosFalhos,
            long pagamentosCancelados,
            long pagamentosEstornados) {
    }

    public record PontoDiarioRow(
            LocalDate data,
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados) {
    }

    public record ProdutoRow(
            String codigo,
            String nome,
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados,
            long creditosVendidos) {
    }

    public record ConciliacaoRow(
            long semConciliacao,
            long divergentes,
            long semMovimento) {
    }

    public record TransacaoRow(
            UUID id,
            UUID usuarioId,
            OffsetDateTime data,
            String usuarioNome,
            String usuarioEmail,
            String usuarioWhatsapp,
            String produtoCodigo,
            String produtoNome,
            BigDecimal valor,
            String moeda,
            Integer creditos,
            String status,
            String metodo,
            String identificadorExterno) {
    }

    public record PaginaRow(
            List<TransacaoRow> itens,
            long total,
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados) {
    }
}
