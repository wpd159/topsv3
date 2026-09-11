package br.com.topsdojob.v3.persistence.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PreviewRestritoBackfillJdbcRepository {

  static final String UPDATE_PREVIEW_SQL = """
      UPDATE arquivo_midia
      SET preview_restrito_tipo = 'PREVIEW_RESTRITO',
          preview_restrito_chave = ?,
          preview_restrito_pipeline_versao = ?,
          preview_restrito_status = 'DISPONIVEL',
          preview_restrito_confirmado_em = ?
      WHERE id = ?
        AND preview_restrito_status = 'DESCONHECIDO'
        AND preview_restrito_tipo IS NULL
        AND preview_restrito_chave IS NULL
        AND preview_restrito_pipeline_versao IS NULL
        AND preview_restrito_confirmado_em IS NULL
      """;

  private final JdbcTemplate jdbcTemplate;

  public PreviewRestritoBackfillJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void limitarEsperaTransacional() {
    jdbcTemplate.execute("SET LOCAL lock_timeout = '1s'");
    jdbcTemplate.execute("SET LOCAL statement_timeout = '5s'");
  }

  /** Fingerprints stay in memory: neither original values nor storage identities are logged. */
  public Map<UUID, EstadoArquivo> capturarArquivos(Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    String sql = """
        SELECT ar.id,
          encode(sha256(convert_to(to_jsonb(ar)::text, 'UTF8')), 'hex') AS completo,
          encode(sha256(convert_to((to_jsonb(ar) - ARRAY[
            'preview_restrito_tipo', 'preview_restrito_chave',
            'preview_restrito_pipeline_versao', 'preview_restrito_status',
            'preview_restrito_confirmado_em'])::text, 'UTF8')), 'hex') AS nao_preview,
          ar.preview_restrito_status,
          (ar.preview_restrito_status = 'DESCONHECIDO'
            AND ar.preview_restrito_tipo IS NULL AND ar.preview_restrito_chave IS NULL
            AND ar.preview_restrito_pipeline_versao IS NULL
            AND ar.preview_restrito_confirmado_em IS NULL) AS metadados_ausentes
        FROM arquivo_midia ar WHERE ar.id IN (%s) ORDER BY ar.id
        """.formatted(String.join(",", Collections.nCopies(ids.size(), "?")));
    Map<UUID, EstadoArquivo> resultado = new LinkedHashMap<>();
    jdbcTemplate.query(sql, (rs, row) -> new EstadoArquivo(
        rs.getObject("id", UUID.class), rs.getString("completo"),
        rs.getString("nao_preview"), rs.getString("preview_restrito_status"),
        rs.getBoolean("metadados_ausentes")), ids.toArray())
        .forEach(estado -> resultado.put(estado.arquivoId(), estado));
    return Map.copyOf(resultado);
  }

  /** Include every link of the selected files, even links currently outside public eligibility. */
  public List<EstadoVinculo> capturarVinculosDosArquivos(Collection<UUID> ids, boolean bloquear) {
    if (ids.isEmpty()) return List.of();
    String sql = """
        SELECT am.id, am.arquivo_midia_id,
          encode(sha256(convert_to(to_jsonb(am)::text, 'UTF8')), 'hex') AS completo
        FROM anuncio_midia am
        WHERE am.arquivo_midia_id IN (%s)
        ORDER BY am.id
        """.formatted(String.join(",", Collections.nCopies(ids.size(), "?")))
        + (bloquear ? " FOR UPDATE OF am" : "");
    return jdbcTemplate.query(sql, (rs, row) -> new EstadoVinculo(
        rs.getObject("id", UUID.class), rs.getObject("arquivo_midia_id", UUID.class),
        rs.getString("completo")), ids.toArray());
  }

  public int marcarDisponiveis(List<Atualizacao> atualizacoes) {
    if (atualizacoes == null || atualizacoes.isEmpty()) {
      return 0;
    }
    int[] resultados = jdbcTemplate.batchUpdate(
        UPDATE_PREVIEW_SQL,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int index) throws SQLException {
            Atualizacao atualizacao = atualizacoes.get(index);
            statement.setString(1, atualizacao.chave());
            statement.setString(2, atualizacao.pipelineVersao());
            statement.setObject(3, atualizacao.confirmadoEm());
            statement.setObject(4, atualizacao.arquivoId());
          }

          @Override
          public int getBatchSize() {
            return atualizacoes.size();
          }
        });
    int atualizados = 0;
    for (int resultado : resultados) {
      if (resultado == 1) {
        atualizados++;
      } else if (resultado == Statement.SUCCESS_NO_INFO) {
        throw new IllegalStateException("Quantidade escrita nao comprovada durante o APPLY");
      }
    }
    return atualizados;
  }

  public record Atualizacao(
      UUID arquivoId,
      String chave,
      String pipelineVersao,
      OffsetDateTime confirmadoEm) {
  }

  public record EstadoArquivo(
      UUID arquivoId, String completo, String naoPreview, String statusPreview,
      boolean metadadosAusentes) {
  }

  public record EstadoVinculo(UUID vinculoId, UUID arquivoId, String completo) {
  }
}
