package br.com.topsdojob.v3.persistence.repository.admin;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUsuarioConsultaJdbcRepository {

    private static final String CTES = """
            WITH ultimo_envio AS (
              SELECT DISTINCT ON (d.usuario_id)
                d.usuario_id,
                d.envio_id
              FROM documento_usuario d
              WHERE d.removido_em IS NULL
                AND d.expurgado_em IS NULL
              ORDER BY d.usuario_id, d.criado_em DESC, d.id DESC
            ),
            kyc AS (
              SELECT
                ue.usuario_id,
                CASE
                  WHEN bool_or(d.status = 'AJUSTE_SOLICITADO') THEN 'AJUSTE_SOLICITADO'
                  WHEN bool_or(d.status = 'REJEITADO') THEN 'REJEITADO'
                  WHEN bool_or(d.status = 'EM_ANALISE') THEN 'EM_ANALISE'
                  WHEN bool_and(d.status = 'VALIDADO') THEN 'APROVADO'
                  ELSE 'PENDENTE'
                END AS status
              FROM ultimo_envio ue
              JOIN documento_usuario d
                ON d.usuario_id = ue.usuario_id
               AND d.envio_id = ue.envio_id
               AND d.removido_em IS NULL
               AND d.expurgado_em IS NULL
              GROUP BY ue.usuario_id
            ),
            anuncios AS (
              SELECT a.usuario_id, count(*) AS total
              FROM anuncio a
              GROUP BY a.usuario_id
            ),
            bloqueios AS (
              SELECT DISTINCT b.usuario_id
              FROM anuncio_bloqueio_juridico b
              WHERE b.escopo = 'ANUNCIO_E_USUARIO'
                AND b.usuario_desbloqueado_em IS NULL
            )
            """;

    private static final String WHERE = """
            WHERE (
              CAST(:termo AS text) IS NULL
              OR unaccent(lower(coalesce(u.nome, ''))) LIKE unaccent(:termoLike)
              OR unaccent(lower(coalesce(u.nome_civil, ''))) LIKE unaccent(:termoLike)
              OR lower(coalesce(u.email_normalizado, '')) LIKE :termoLike
              OR CAST(u.id AS text) LIKE :termoLike
              OR (
                CAST(:cpfSufixo AS text) IS NOT NULL
                AND right(regexp_replace(coalesce(u.cpf_normalizado, ''), '[^0-9]', '', 'g'), 2) = :cpfSufixo
              )
              OR (
                CAST(:digitos AS text) IS NOT NULL
                AND (
                  regexp_replace(coalesce(u.cpf_normalizado, ''), '[^0-9]', '', 'g') LIKE :digitosLike
                  OR regexp_replace(coalesce(u.telefone_normalizado, ''), '[^0-9]', '', 'g') LIKE :digitosLike
                )
              )
            )
              AND (CAST(:status AS text) IS NULL OR u.status = :status)
              AND (CAST(:kycStatus AS text) IS NULL OR coalesce(k.status, 'SEM_ENVIO') = :kycStatus)
              AND (
                CAST(:grupo AS text) IS NULL
                OR (:grupo = 'ATIVOS' AND u.status = 'ATIVO')
                OR (:grupo = 'INATIVOS' AND u.status <> 'ATIVO')
                OR (:grupo = 'COM_ANUNCIOS' AND coalesce(a.total, 0) > 0)
                OR (:grupo = 'SEM_ANUNCIOS' AND coalesce(a.total, 0) = 0)
              )
              AND (
                CAST(:uf AS text) IS NULL
                OR EXISTS (
                  SELECT 1
                  FROM anuncio filtro_anuncio
                  JOIN anuncio_localizacao filtro_localizacao
                    ON filtro_localizacao.anuncio_id = filtro_anuncio.id
                  JOIN estado filtro_estado
                    ON filtro_estado.id = filtro_localizacao.estado_id
                  JOIN cidade filtro_cidade
                    ON filtro_cidade.id = filtro_localizacao.cidade_id
                  WHERE filtro_anuncio.usuario_id = u.id
                    AND filtro_estado.uf = :uf
                    AND (CAST(:cidade AS text) IS NULL OR filtro_cidade.slug = :cidade)
                )
              )
            """;

    private static final String SELECT = """
            SELECT
              u.id,
              u.nome,
              u.nome_civil,
              u.email_normalizado,
              u.telefone_normalizado,
              u.cpf_normalizado,
              u.status,
              u.tipo_conta,
              u.criado_em,
              u.atualizado_em,
              coalesce(k.status, 'SEM_ENVIO') AS kyc_status,
              coalesce(a.total, 0) AS total_anuncios,
              (b.usuario_id IS NOT NULL) AS bloqueado,
              local_principal.uf AS uf_principal,
              local_principal.cidade AS cidade_principal
            FROM usuario u
            LEFT JOIN kyc k ON k.usuario_id = u.id
            LEFT JOIN anuncios a ON a.usuario_id = u.id
            LEFT JOIN bloqueios b ON b.usuario_id = u.id
            LEFT JOIN LATERAL (
              SELECT e.uf, c.nome AS cidade
              FROM anuncio a_local
              JOIN anuncio_localizacao al ON al.anuncio_id = a_local.id
              JOIN estado e ON e.id = al.estado_id
              JOIN cidade c ON c.id = al.cidade_id
              WHERE a_local.usuario_id = u.id
              ORDER BY a_local.criado_em DESC, a_local.id
              LIMIT 1
            ) local_principal ON true
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminUsuarioConsultaJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<UsuarioRow> listar(
            String termo,
            String digitos,
            String cpfSufixo,
            String status,
            String kycStatus,
            String grupo,
            String uf,
            String cidade,
            String ordenacao,
            Pageable pageable) {
        Map<String, Object> parametros = parametros(
                termo,
                digitos,
                cpfSufixo,
                status,
                kycStatus,
                grupo,
                uf,
                cidade);
        String orderBy = "ANTIGOS".equals(ordenacao)
                ? " ORDER BY u.criado_em ASC, u.id ASC "
                : " ORDER BY u.criado_em DESC, u.id ASC ";
        String sql = CTES + SELECT + WHERE + orderBy + " LIMIT :limite OFFSET :offset ";
        Map<String, Object> paginados = new HashMap<>(parametros);
        paginados.put("limite", pageable.getPageSize());
        paginados.put("offset", pageable.getOffset());

        List<UsuarioRow> itens = jdbc.query(sql, paginados, (resultSet, rowNumber) -> mapear(resultSet));
        Long total = jdbc.queryForObject(
                CTES + """
                        SELECT count(*)
                        FROM usuario u
                        LEFT JOIN kyc k ON k.usuario_id = u.id
                        LEFT JOIN anuncios a ON a.usuario_id = u.id
                        """ + WHERE,
                parametros,
                Long.class);
        return new PageImpl<>(itens, pageable, total == null ? 0L : total);
    }

    public IndicadoresRow indicadores() {
        return jdbc.queryForObject(
                """
                WITH anuncios AS (
                  SELECT usuario_id, count(*) AS total
                  FROM anuncio
                  GROUP BY usuario_id
                )
                SELECT
                  count(*) AS total_usuarios,
                  count(*) FILTER (
                    WHERE u.criado_em >= (
                      date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')
                      AT TIME ZONE 'America/Sao_Paulo'
                    )
                  ) AS novos_hoje,
                  count(*) FILTER (WHERE coalesce(a.total, 0) > 0) AS com_anuncios,
                  count(*) FILTER (WHERE coalesce(a.total, 0) = 0) AS sem_anuncios
                FROM usuario u
                LEFT JOIN anuncios a ON a.usuario_id = u.id
                """,
                Map.of(),
                (resultSet, rowNumber) -> new IndicadoresRow(
                        resultSet.getLong("total_usuarios"),
                        resultSet.getLong("novos_hoje"),
                        resultSet.getLong("com_anuncios"),
                        resultSet.getLong("sem_anuncios")));
    }

    private Map<String, Object> parametros(
            String termo,
            String digitos,
            String cpfSufixo,
            String status,
            String kycStatus,
            String grupo,
            String uf,
            String cidade) {
        String termoLike = termo == null ? "" : "%" + termo.toLowerCase(java.util.Locale.ROOT) + "%";
        String digitosLike = digitos == null ? "" : "%" + digitos + "%";
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("termo", termo);
        parametros.put("termoLike", termoLike);
        parametros.put("digitos", digitos);
        parametros.put("digitosLike", digitosLike);
        parametros.put("cpfSufixo", cpfSufixo);
        parametros.put("status", status);
        parametros.put("kycStatus", kycStatus);
        parametros.put("grupo", grupo);
        parametros.put("uf", uf);
        parametros.put("cidade", cidade);
        return parametros;
    }

    private UsuarioRow mapear(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new UsuarioRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("nome"),
                resultSet.getString("nome_civil"),
                resultSet.getString("email_normalizado"),
                resultSet.getString("telefone_normalizado"),
                resultSet.getString("cpf_normalizado"),
                resultSet.getString("status"),
                resultSet.getString("tipo_conta"),
                resultSet.getObject("criado_em", OffsetDateTime.class),
                resultSet.getObject("atualizado_em", OffsetDateTime.class),
                resultSet.getString("kyc_status"),
                resultSet.getLong("total_anuncios"),
                resultSet.getBoolean("bloqueado"),
                resultSet.getString("uf_principal"),
                resultSet.getString("cidade_principal"));
    }

    public record UsuarioRow(
            UUID id,
            String nome,
            String nomeCivil,
            String email,
            String telefone,
            String cpf,
            String status,
            String tipoConta,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            String kycStatus,
            long totalAnuncios,
            boolean bloqueado,
            String ufPrincipal,
            String cidadePrincipal) {
    }

    public record IndicadoresRow(
            long totalUsuarios,
            long novosHoje,
            long comAnuncios,
            long semAnuncios) {
    }
}
