package br.com.topsdojob.v3.persistence.repository.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDashboardAnalyticsJdbcRepository {

    private static final String DESEMPENHO_PUBLICADO = """
            WITH eventos_canonicos AS (
              SELECT e.anuncio_id, count(*) AS total_eventos
              FROM evento_visualizacao e
              LEFT JOIN agregado_visualizacao_inicial i ON i.anuncio_id = e.anuncio_id
              WHERE e.request_id LIKE 'import:anuncio_view_log:%'
                 OR i.id IS NULL
                 OR e.criado_em > i.snapshot_corte_em
              GROUP BY e.anuncio_id
            ),
            cliques_permitidos AS (
              SELECT c.anuncio_id, count(*) AS total_cliques
              FROM clique_whatsapp c
              WHERE c.permitido = true
              GROUP BY c.anuncio_id
            ),
            anuncios_legados AS (
              SELECT DISTINCT m.entidade_v3_id AS anuncio_id
              FROM importacao_mapeamento m
              WHERE m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
                AND m.tabela_origem = 'anuncios'
                AND m.entidade_tipo = 'ANUNCIO'
                AND m.status = 'MAPEADO'
            ),
            premium_vigente AS (
              SELECT ab.anuncio_id, count(*) AS total_beneficios
              FROM ativacao_beneficio ab
              JOIN anuncio pa ON pa.id = ab.anuncio_id
              JOIN beneficio_premium bp ON bp.id = ab.beneficio_id
              JOIN grupo_ativacao_beneficio gb ON gb.id = ab.grupo_ativacao_id
              WHERE pa.removido_em IS NULL
                AND ab.usuario_id = pa.usuario_id
                AND gb.usuario_id = pa.usuario_id
                AND gb.anuncio_id = pa.id
                AND gb.origem = ab.origem
                AND bp.escopo = 'ANUNCIO'
                AND bp.ativo = true
                AND ab.status = 'ATIVA'
                AND ab.revogada_em IS NULL
                AND ab.inicio_em <= :agora
                AND ab.fim_em > :agora
                AND ab.inicio_em >= gb.validade_inicio_em
                AND ab.fim_em <= gb.validade_fim_em
                AND gb.status = 'ATIVO'
                AND gb.validade_inicio_em <= :agora
                AND gb.validade_fim_em > :agora
              GROUP BY ab.anuncio_id
            )
            SELECT
              a.id,
              a.titulo,
              a.slug,
              c.nome AS cidade,
              c.slug AS cidade_slug,
              e.uf,
              CASE
                WHEN bool_or(
                  am.status = 'PUBLICAVEL'
                  AND am.tipo <> 'STORY'
                  AND am.visibilidade_midia = 'RESTRITA_18'
                ) THEN 'RESTRITA_18'
                ELSE 'LIVRE'
              END AS classificacao,
              CASE
                WHEN legado.anuncio_id IS NOT NULL AND inicial.id IS NULL THEN NULL
                ELSE coalesce(inicial.total_visualizacoes, 0) + coalesce(eventos.total_eventos, 0)
              END AS visualizacoes,
              coalesce(cliques.total_cliques, 0) AS cliques,
              coalesce(premium.total_beneficios, 0) AS beneficios_premium
            FROM anuncio a
            LEFT JOIN agregado_visualizacao_inicial inicial ON inicial.anuncio_id = a.id
            LEFT JOIN eventos_canonicos eventos ON eventos.anuncio_id = a.id
            LEFT JOIN cliques_permitidos cliques ON cliques.anuncio_id = a.id
            LEFT JOIN anuncios_legados legado ON legado.anuncio_id = a.id
            LEFT JOIN premium_vigente premium ON premium.anuncio_id = a.id
            LEFT JOIN anuncio_localizacao al ON al.anuncio_id = a.id
            LEFT JOIN cidade c ON c.id = al.cidade_id
            LEFT JOIN estado e ON e.id = al.estado_id
            LEFT JOIN anuncio_midia am ON am.anuncio_id = a.id
              AND am.status <> 'REMOVIDA'
              AND am.tipo <> 'STORY'
            WHERE a.status = 'PUBLICADO'
              AND a.status_moderacao = 'APROVADO'
              AND a.publicado_em IS NOT NULL
              AND a.removido_em IS NULL
            GROUP BY
              a.id, a.titulo, a.slug, c.nome, c.slug, e.uf,
              legado.anuncio_id, inicial.id, inicial.total_visualizacoes,
              eventos.total_eventos, cliques.total_cliques, premium.total_beneficios
            ORDER BY a.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminDashboardAnalyticsJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SerieDiariaRow> serieDiaria(LocalDate inicio, LocalDate fim) {
        return jdbc.query("""
                SELECT
                  metricas.data_referencia,
                  sum(metricas.visualizacoes) AS visualizacoes,
                  sum(metricas.cliques) AS cliques
                FROM (
                  SELECT
                    av.data_referencia,
                    sum(av.total_visualizacoes) AS visualizacoes,
                    0::bigint AS cliques
                  FROM agregado_visualizacao_diaria av
                  WHERE av.data_referencia BETWEEN :inicio AND :fim
                  GROUP BY av.data_referencia
                  UNION ALL
                  SELECT
                    ac.data_referencia,
                    0::bigint AS visualizacoes,
                    sum(ac.total_cliques) AS cliques
                  FROM agregado_clique_whatsapp_diario ac
                  WHERE ac.data_referencia BETWEEN :inicio AND :fim
                  GROUP BY ac.data_referencia
                ) metricas
                GROUP BY metricas.data_referencia
                ORDER BY metricas.data_referencia
                """,
                Map.of("inicio", inicio, "fim", fim),
                (rs, row) -> new SerieDiariaRow(
                        rs.getObject("data_referencia", LocalDate.class),
                        rs.getLong("visualizacoes"),
                        rs.getLong("cliques")));
    }

    public List<TopWhatsappRow> topWhatsappHoje(LocalDate hoje, int limiteConsulta) {
        return jdbc.query("""
                SELECT
                  a.id,
                  a.titulo,
                  a.slug,
                  a.status,
                  a.status_moderacao,
                  c.nome AS cidade,
                  e.uf,
                  sum(ac.total_cliques) AS cliques,
                  capa.midia_id
                FROM agregado_clique_whatsapp_diario ac
                JOIN anuncio a ON a.id = ac.anuncio_id
                LEFT JOIN anuncio_localizacao al ON al.anuncio_id = a.id
                LEFT JOIN cidade c ON c.id = al.cidade_id
                LEFT JOIN estado e ON e.id = al.estado_id
                LEFT JOIN LATERAL (
                  SELECT am.id AS midia_id
                  FROM anuncio_midia am
                  WHERE am.anuncio_id = a.id
                    AND am.tipo = 'FOTO'
                    AND am.status = 'PUBLICAVEL'
                    AND am.visibilidade_midia IS NOT NULL
                  ORDER BY
                    CASE WHEN am.finalidade = 'CAPA' THEN 0 ELSE 1 END,
                    am.ordem,
                    am.id
                  LIMIT 1
                ) capa ON true
                WHERE ac.data_referencia = :hoje
                  AND ac.total_cliques > 0
                  AND a.removido_em IS NULL
                GROUP BY
                  a.id, a.titulo, a.slug, a.status, a.status_moderacao,
                  c.nome, e.uf, capa.midia_id
                HAVING sum(ac.total_cliques) > 0
                ORDER BY sum(ac.total_cliques) DESC, a.id
                LIMIT :limite
                """,
                Map.of("hoje", hoje, "limite", limiteConsulta),
                (rs, row) -> new TopWhatsappRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("titulo"),
                        rs.getString("slug"),
                        rs.getString("cidade"),
                        rs.getString("uf"),
                        rs.getLong("cliques"),
                        rs.getObject("midia_id", UUID.class),
                        "PUBLICADO".equals(rs.getString("status"))
                                && "APROVADO".equals(rs.getString("status_moderacao"))));
    }

    public List<AnuncioDesempenhoRow> desempenhoPublicados(OffsetDateTime agora) {
        return jdbc.query(
                DESEMPENHO_PUBLICADO,
                Map.of("agora", agora),
                (rs, row) -> new AnuncioDesempenhoRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("titulo"),
                        rs.getString("slug"),
                        rs.getString("cidade"),
                        rs.getString("cidade_slug"),
                        rs.getString("uf"),
                        rs.getString("classificacao"),
                        rs.getObject("visualizacoes", Long.class),
                        rs.getLong("cliques"),
                        rs.getLong("beneficios_premium")));
    }

    public long countBeneficiosVencendo(
            OffsetDateTime agora,
            OffsetDateTime limite) {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM ativacao_beneficio ab
                JOIN anuncio a ON a.id = ab.anuncio_id
                JOIN beneficio_premium bp ON bp.id = ab.beneficio_id
                JOIN grupo_ativacao_beneficio gb ON gb.id = ab.grupo_ativacao_id
                WHERE a.removido_em IS NULL
                  AND ab.usuario_id = a.usuario_id
                  AND gb.usuario_id = a.usuario_id
                  AND gb.anuncio_id = a.id
                  AND gb.origem = ab.origem
                  AND bp.escopo = 'ANUNCIO'
                  AND bp.ativo = true
                  AND ab.status = 'ATIVA'
                  AND ab.revogada_em IS NULL
                  AND ab.inicio_em <= :agora
                  AND ab.fim_em > :agora
                  AND ab.fim_em <= :limite
                  AND ab.inicio_em >= gb.validade_inicio_em
                  AND ab.fim_em <= gb.validade_fim_em
                  AND gb.status = 'ATIVO'
                  AND gb.validade_inicio_em <= :agora
                  AND gb.validade_fim_em > :agora
                """,
                Map.of("agora", agora, "limite", limite),
                Long.class);
        return total == null ? 0L : total;
    }

    public record SerieDiariaRow(
            LocalDate data,
            long visualizacoes,
            long cliques) {
    }

    public record TopWhatsappRow(
            UUID anuncioId,
            String titulo,
            String slug,
            String cidade,
            String uf,
            long cliques,
            UUID midiaId,
            boolean publicado) {
    }

    public record AnuncioDesempenhoRow(
            UUID anuncioId,
            String titulo,
            String slug,
            String cidade,
            String cidadeSlug,
            String uf,
            String classificacao,
            Long visualizacoes,
            long cliques,
            long beneficiosPremium) {
    }
}
