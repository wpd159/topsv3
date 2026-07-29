package br.com.topsdojob.v3.persistence.repository.wizard;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WizardProgressJdbcRepository {

  private static final String BASE_FILTRADA = """
      WITH kyc_por_usuario AS (
        SELECT
          du.usuario_id,
          CASE
            WHEN bool_or(du.status = 'REJEITADO') THEN 'REJEITADO'
            WHEN bool_and(du.status = 'VALIDADO') THEN 'APROVADO'
            ELSE 'PENDENTE'
          END AS kyc_status
        FROM documento_usuario du
        WHERE du.status NOT IN ('REMOVIDO', 'EXPURGADO')
        GROUP BY du.usuario_id
      ),
      base_sem_status AS (
        SELECT
          wp.id,
          wp.usuario_id,
          wp.anuncio_id,
          wp.modo,
          wp.ultimo_step,
          wp.maior_step_ordem,
          wp.status AS status_registrado,
          wp.criado_em,
          wp.atualizado_em,
          wp.concluido_em,
          u.nome AS usuario_nome,
          u.email_normalizado AS usuario_email,
          coalesce(kyc.kyc_status, 'NAO_INICIADO') AS kyc_status,
          a.slug AS anuncio_slug,
          a.titulo AS anuncio_titulo,
          a.status AS anuncio_status,
          a.status_moderacao AS anuncio_status_moderacao,
          a.publicado_em,
          e.uf,
          c.slug AS cidade_slug
        FROM wizard_progresso wp
        JOIN usuario u ON u.id = wp.usuario_id
        LEFT JOIN kyc_por_usuario kyc ON kyc.usuario_id = wp.usuario_id
        LEFT JOIN anuncio a ON a.id = wp.anuncio_id
        LEFT JOIN anuncio_localizacao al ON al.anuncio_id = a.id
        LEFT JOIN estado e ON e.id = al.estado_id
        LEFT JOIN cidade c ON c.id = al.cidade_id
        WHERE wp.criado_em >= :inicio
          AND wp.criado_em < :fim_exclusivo
          AND (cast(:termo AS text) IS NULL
            OR lower(coalesce(u.nome, '')) LIKE :termo_padrao
            OR lower(coalesce(u.email_normalizado, '')) LIKE :termo_padrao
            OR lower(coalesce(u.telefone_normalizado, '')) LIKE :termo_padrao
            OR lower(coalesce(a.slug, '')) LIKE :termo_padrao
            OR lower(cast(u.id AS text)) LIKE :termo_padrao
            OR lower(coalesce(cast(a.id AS text), '')) LIKE :termo_padrao)
          AND (:modo = 'TODOS' OR wp.modo = :modo)
          AND (cast(:uf AS text) IS NULL OR e.uf = :uf)
          AND (cast(:cidade AS text) IS NULL OR c.slug = :cidade)
          AND (:kyc = 'TODOS' OR coalesce(kyc.kyc_status, 'NAO_INICIADO') = :kyc)
          AND (:anuncio_status = 'TODOS' OR coalesce(a.status, 'SEM_ANUNCIO') = :anuncio_status)
      ),
      base AS (
        SELECT
          base_sem_status.*,
          CASE
            WHEN publicado_em IS NOT NULL THEN 'PUBLICADO'
            WHEN anuncio_status = 'REJEITADO'
              OR anuncio_status_moderacao = 'REJEITADO' THEN 'REJEITADO'
            WHEN anuncio_status IN ('PENDENTE_REVISAO', 'APROVADO')
              THEN 'AGUARDANDO_MODERACAO'
            ELSE status_registrado
          END AS progresso_status
        FROM base_sem_status
      ),
      filtrada AS (
        SELECT *
        FROM base
        WHERE :status = 'TODOS' OR progresso_status = :status
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public WizardProgressJdbcRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public SyncRow sincronizar(
      UUID id,
      String sessaoId,
      UUID usuarioId,
      UUID anuncioId,
      String modo,
      String ultimoStep,
      int maiorStepOrdem,
      String status,
      OffsetDateTime agora) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("id", id)
        .addValue("sessao_id", sessaoId)
        .addValue("usuario_id", usuarioId)
        .addValue("anuncio_id", anuncioId)
        .addValue("modo", modo)
        .addValue("ultimo_step", ultimoStep)
        .addValue("maior_step_ordem", maiorStepOrdem)
        .addValue("status", status)
        .addValue("agora", agora);

    return jdbc.query("""
        WITH alterado AS (
          INSERT INTO wizard_progresso (
            id, sessao_id, usuario_id, anuncio_id, modo, ultimo_step,
            maior_step_ordem, status, criado_em, atualizado_em, concluido_em
          ) VALUES (
            :id, :sessao_id, :usuario_id, :anuncio_id, :modo, :ultimo_step,
            :maior_step_ordem, :status, :agora, :agora,
            CASE WHEN :status = 'EM_PREENCHIMENTO' THEN NULL ELSE :agora END
          )
          ON CONFLICT (usuario_id, sessao_id) DO UPDATE SET
            anuncio_id = coalesce(EXCLUDED.anuncio_id, wizard_progresso.anuncio_id),
            ultimo_step = CASE
              WHEN wizard_progresso.status = 'EM_PREENCHIMENTO'
                THEN EXCLUDED.ultimo_step
              ELSE wizard_progresso.ultimo_step
            END,
            maior_step_ordem = greatest(
              wizard_progresso.maior_step_ordem,
              EXCLUDED.maior_step_ordem
            ),
            status = CASE
              WHEN wizard_progresso.status = 'EM_PREENCHIMENTO'
                THEN EXCLUDED.status
              ELSE wizard_progresso.status
            END,
            atualizado_em = EXCLUDED.atualizado_em,
            concluido_em = CASE
              WHEN wizard_progresso.concluido_em IS NOT NULL
                THEN wizard_progresso.concluido_em
              WHEN EXCLUDED.status <> 'EM_PREENCHIMENTO'
                THEN EXCLUDED.atualizado_em
              ELSE NULL
            END
          WHERE wizard_progresso.modo = EXCLUDED.modo
            AND (
              coalesce(wizard_progresso.anuncio_id, UUID '00000000-0000-0000-0000-000000000000')
                <> coalesce(EXCLUDED.anuncio_id, UUID '00000000-0000-0000-0000-000000000000')
              OR wizard_progresso.maior_step_ordem < EXCLUDED.maior_step_ordem
              OR (
                wizard_progresso.status = 'EM_PREENCHIMENTO'
                AND (
                  wizard_progresso.ultimo_step <> EXCLUDED.ultimo_step
                  OR wizard_progresso.status <> EXCLUDED.status
                )
              )
            )
          RETURNING id, modo, status, ultimo_step, atualizado_em
        )
        SELECT id, modo, status, ultimo_step, atualizado_em
        FROM alterado
        UNION ALL
        SELECT id, modo, status, ultimo_step, atualizado_em
        FROM wizard_progresso
        WHERE usuario_id = :usuario_id
          AND sessao_id = :sessao_id
          AND NOT EXISTS (SELECT 1 FROM alterado)
        LIMIT 1
        """, params, (rs, rowNum) -> new SyncRow(
            rs.getObject("id", UUID.class),
            rs.getString("modo"),
            rs.getString("status"),
            rs.getString("ultimo_step"),
            rs.getObject("atualizado_em", OffsetDateTime.class)))
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("progresso do wizard nao foi persistido"));
  }

  public ResumoRow resumo(MapSqlParameterSource params) {
    return jdbc.query(BASE_FILTRADA + """
        SELECT
          count(*) AS sessoes_observadas,
          count(DISTINCT usuario_id) AS usuarios_observados,
          count(DISTINCT usuario_id) FILTER (WHERE kyc_status = 'NAO_INICIADO') AS kyc_nao_iniciado,
          count(DISTINCT usuario_id) FILTER (WHERE kyc_status IN ('PENDENTE', 'REJEITADO')) AS kyc_pendente,
          count(DISTINCT usuario_id) FILTER (WHERE kyc_status = 'APROVADO') AS kyc_aprovado,
          count(*) FILTER (WHERE progresso_status = 'EM_PREENCHIMENTO') AS em_preenchimento,
          count(*) FILTER (WHERE progresso_status = 'AGUARDANDO_MODERACAO') AS aguardando_moderacao,
          count(DISTINCT anuncio_id) FILTER (WHERE anuncio_status = 'RASCUNHO') AS anuncios_rascunho,
          count(DISTINCT anuncio_id) FILTER (WHERE progresso_status = 'REJEITADO') AS anuncios_rejeitados,
          count(DISTINCT anuncio_id) FILTER (WHERE progresso_status = 'PUBLICADO') AS anuncios_publicados,
          cast(round(
              avg(extract(epoch FROM (concluido_em - criado_em)) / 60.0)
                FILTER (WHERE concluido_em IS NOT NULL)
            ) AS bigint) AS tempo_medio_conclusao_minutos
        FROM filtrada
        """, params, (rs, rowNum) -> new ResumoRow(
            rs.getLong("sessoes_observadas"),
            rs.getLong("usuarios_observados"),
            rs.getLong("kyc_nao_iniciado"),
            rs.getLong("kyc_pendente"),
            rs.getLong("kyc_aprovado"),
            rs.getLong("em_preenchimento"),
            rs.getLong("aguardando_moderacao"),
            rs.getLong("anuncios_rascunho"),
            rs.getLong("anuncios_rejeitados"),
            rs.getLong("anuncios_publicados"),
            rs.getObject("tempo_medio_conclusao_minutos", Long.class)))
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public long[] funil(MapSqlParameterSource params) {
    return jdbc.query(BASE_FILTRADA + """
        SELECT
          count(*) FILTER (WHERE maior_step_ordem >= 0) AS perfil,
          count(*) FILTER (WHERE maior_step_ordem >= 1) AS localizacao,
          count(*) FILTER (WHERE maior_step_ordem >= 2) AS servicos,
          count(*) FILTER (WHERE maior_step_ordem >= 3) AS fotos,
          count(*) FILTER (WHERE maior_step_ordem >= 4) AS revisao,
          count(*) FILTER (WHERE maior_step_ordem >= 5) AS premium,
          count(*) FILTER (WHERE maior_step_ordem >= 6) AS kyc,
          count(*) FILTER (WHERE maior_step_ordem >= 7) AS concluido
        FROM filtrada
        """, params, (rs, rowNum) -> new long[] {
            rs.getLong("perfil"),
            rs.getLong("localizacao"),
            rs.getLong("servicos"),
            rs.getLong("fotos"),
            rs.getLong("revisao"),
            rs.getLong("premium"),
            rs.getLong("kyc"),
            rs.getLong("concluido")
        }).stream().findFirst().orElseGet(() -> new long[8]);
  }

  public Map<String, Long> etapasAtuais(MapSqlParameterSource params) {
    return jdbc.query(BASE_FILTRADA + """
        SELECT ultimo_step, count(*) AS quantidade
        FROM filtrada
        GROUP BY ultimo_step
        """, params, rs -> {
      Map<String, Long> result = new java.util.LinkedHashMap<>();
      while (rs.next()) {
        result.put(rs.getString("ultimo_step"), rs.getLong("quantidade"));
      }
      return result;
    });
  }

  public PaginaRow listar(MapSqlParameterSource params, int page, int size) {
    MapSqlParameterSource paginacao = new MapSqlParameterSource(params.getValues())
        .addValue("limite", size)
        .addValue("offset", (long) page * size);
    List<ItemRow> itens = jdbc.query(BASE_FILTRADA + """
        SELECT
          id,
          usuario_id,
          usuario_nome,
          usuario_email,
          modo,
          progresso_status,
          ultimo_step,
          kyc_status,
          anuncio_id,
          anuncio_slug,
          anuncio_titulo,
          anuncio_status,
          criado_em,
          atualizado_em,
          count(*) OVER () AS total_elementos
        FROM filtrada
        ORDER BY atualizado_em DESC, id
        LIMIT :limite OFFSET :offset
        """, paginacao, (rs, rowNum) -> new ItemRow(
            rs.getObject("id", UUID.class),
            rs.getObject("usuario_id", UUID.class),
            rs.getString("usuario_nome"),
            rs.getString("usuario_email"),
            rs.getString("modo"),
            rs.getString("progresso_status"),
            rs.getString("ultimo_step"),
            rs.getString("kyc_status"),
            rs.getObject("anuncio_id", UUID.class),
            rs.getString("anuncio_slug"),
            rs.getString("anuncio_titulo"),
            rs.getString("anuncio_status"),
            rs.getObject("criado_em", OffsetDateTime.class),
            rs.getObject("atualizado_em", OffsetDateTime.class),
            rs.getLong("total_elementos")));
    long total = itens.isEmpty() ? 0L : itens.get(0).totalElementos();
    return new PaginaRow(new ArrayList<>(itens), total);
  }

  public static MapSqlParameterSource filtros(
      OffsetDateTime inicio,
      OffsetDateTime fimExclusivo,
      String termo,
      String modo,
      String status,
      String uf,
      String cidade,
      String kyc,
      String anuncioStatus) {
    String termoSeguro = termo == null ? null : termo.toLowerCase(java.util.Locale.ROOT);
    return new MapSqlParameterSource()
        .addValue("inicio", inicio)
        .addValue("fim_exclusivo", fimExclusivo)
        .addValue("termo", termoSeguro)
        .addValue("termo_padrao", termoSeguro == null ? null : "%" + termoSeguro + "%")
        .addValue("modo", modo)
        .addValue("status", status)
        .addValue("uf", uf)
        .addValue("cidade", cidade)
        .addValue("kyc", kyc)
        .addValue("anuncio_status", anuncioStatus);
  }

  public record SyncRow(
      UUID id,
      String modo,
      String status,
      String ultimoStep,
      OffsetDateTime atualizadoEm) {
  }

  public record ResumoRow(
      long sessoesObservadas,
      long usuariosObservados,
      long kycNaoIniciado,
      long kycPendente,
      long kycAprovado,
      long emPreenchimento,
      long aguardandoModeracao,
      long anunciosRascunho,
      long anunciosRejeitados,
      long anunciosPublicados,
      Long tempoMedioConclusaoMinutos) {
  }

  public record ItemRow(
      UUID id,
      UUID usuarioId,
      String usuario,
      String email,
      String modo,
      String status,
      String ultimoStep,
      String kycStatus,
      UUID anuncioId,
      String anuncioSlug,
      String anuncioTitulo,
      String anuncioStatus,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      long totalElementos) {
  }

  public record PaginaRow(List<ItemRow> itens, long totalElementos) {
  }
}
