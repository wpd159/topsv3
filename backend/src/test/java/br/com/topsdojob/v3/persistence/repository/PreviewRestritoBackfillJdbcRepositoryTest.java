package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

class PreviewRestritoBackfillJdbcRepositoryTest {

  @Test
  void updateEmLoteAlteraSomenteAsCincoColunasDoPreview() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
        .thenReturn(new int[] {1});
    PreviewRestritoBackfillJdbcRepository repository =
        new PreviewRestritoBackfillJdbcRepository(jdbc);

    int atualizados = repository.marcarDisponiveis(List.of(
        new PreviewRestritoBackfillJdbcRepository.Atualizacao(
            UUID.randomUUID(),
            "publicas/restritas-borradas/v1/preview.jpg",
            "v1",
            OffsetDateTime.now())));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).batchUpdate(sql.capture(), any(BatchPreparedStatementSetter.class));
    String update = sql.getValue();
    String set = update.substring(update.indexOf("SET "), update.indexOf("WHERE "));
    assertThat(set).contains(
        "preview_restrito_tipo",
        "preview_restrito_chave",
        "preview_restrito_pipeline_versao",
        "preview_restrito_status",
        "preview_restrito_confirmado_em");
    assertThat(set).doesNotContain(
        "status_arquivo",
        "bucket",
        "chave_objeto",
        "mime_type",
        "sha256");
    String where = update.substring(update.indexOf("WHERE "));
    assertThat(where).contains("preview_restrito_status = 'DESCONHECIDO'",
        "preview_restrito_tipo IS NULL", "preview_restrito_chave IS NULL",
        "preview_restrito_pipeline_versao IS NULL", "preview_restrito_confirmado_em IS NULL");
    assertThat(where).doesNotContain("COALESCE", "'PENDENTE'", " OR ");
    assertThat(atualizados).isEqualTo(1);
  }

  @Test
  void recusaContagemDeEscritaNaoComprovada() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
        .thenReturn(new int[] {java.sql.Statement.SUCCESS_NO_INFO});
    var repository = new PreviewRestritoBackfillJdbcRepository(jdbc);
    assertThatThrownBy(() -> repository.marcarDisponiveis(List.of(
        new PreviewRestritoBackfillJdbcRepository.Atualizacao(UUID.randomUUID(),
            "preview.jpg", "v1", OffsetDateTime.now()))))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("nao comprovada");
  }

  @Test
  void loteVazioNaoAcessaBanco() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PreviewRestritoBackfillJdbcRepository repository =
        new PreviewRestritoBackfillJdbcRepository(jdbc);

    assertThat(repository.marcarDisponiveis(List.of())).isZero();
    verifyNoInteractions(jdbc);
  }
}
