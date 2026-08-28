package br.com.topsdojob.v3.persistence.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
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
        AND COALESCE(preview_restrito_status, 'DESCONHECIDO')
            IN ('DESCONHECIDO', 'PENDENTE')
        AND (preview_restrito_tipo IS NULL
             OR preview_restrito_tipo = 'PREVIEW_RESTRITO')
        AND (preview_restrito_chave IS NULL
             OR preview_restrito_chave = ?)
        AND (preview_restrito_pipeline_versao IS NULL
             OR preview_restrito_pipeline_versao = ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  public PreviewRestritoBackfillJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
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
            statement.setString(5, atualizacao.chave());
            statement.setString(6, atualizacao.pipelineVersao());
          }

          @Override
          public int getBatchSize() {
            return atualizacoes.size();
          }
        });
    int atualizados = 0;
    for (int resultado : resultados) {
      if (resultado == 1 || resultado == Statement.SUCCESS_NO_INFO) {
        atualizados++;
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
}
