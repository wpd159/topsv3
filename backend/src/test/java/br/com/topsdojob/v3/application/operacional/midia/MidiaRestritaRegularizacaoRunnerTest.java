package br.com.topsdojob.v3.application.operacional.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService.EstadoCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;

class MidiaRestritaRegularizacaoRunnerTest {

  @TempDir
  Path tempDir;

  @Test
  void ficaDesabilitadoPorPadrao() {
    ConditionalOnProperty condition = MidiaRestritaRegularizacaoRunner.class
        .getAnnotation(ConditionalOnProperty.class);

    assertThat(condition).isNotNull();
    assertThat(condition.name()).containsExactly(
        "app.restricted-media-preview-reconciliation.enabled");
    assertThat(condition.havingValue()).isEqualTo("true");
    assertThat(condition.matchIfMissing()).isFalse();
  }

  @Test
  void planGeraSomenteRelatorioSanitizadoForaDoGit() throws Exception {
    MidiaRestritaRegularizacaoService service = mock(MidiaRestritaRegularizacaoService.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    Path report = tempDir.resolve("preview-plan.tsv");
    var result = resultadoVazio();
    when(service.planejar()).thenReturn(result);
    MidiaRestritaRegularizacaoRunner runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "PLAN", false, 200, report.toString());

    runner.run(mock(ApplicationArguments.class));

    assertThat(Files.readString(report)).isEqualTo(
        "arquivo_hash\tchave_hash\tclassificacao\n");
    verify(service, never()).aplicar(result, 200);
    verify(service, never()).validarPersistencia(result, 200);
    verify(context).close();
  }

  @Test
  void applySemConfirmacaoFalhaAntesDeListarOuGravar() {
    MidiaRestritaRegularizacaoService service = mock(MidiaRestritaRegularizacaoService.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    MidiaRestritaRegularizacaoRunner runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", false, 200, tempDir.resolve("plan.tsv").toString());

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("confirmacao explicita");

    verify(service, never()).planejar();
    verify(context).close();
  }

  @Test
  void applyExecutaLotesEValidaNovoInventarioAntesDeConcluir() {
    MidiaRestritaRegularizacaoService service = mock(MidiaRestritaRegularizacaoService.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    var result = resultadoVazio();
    var application = new MidiaRestritaRegularizacaoService.Aplicacao(0, 0, 0);
    var validation = new MidiaRestritaRegularizacaoService.Validacao(0, 0, 0, 0, 0);
    when(service.planejar()).thenReturn(result, result);
    doAnswer(invocation -> {
      invocation.<Consumer<EstadoCommit>>getArgument(2).accept(EstadoCommit.COMMITTED);
      return application;
    }).when(service).aplicar(eq(result), eq(200), any());
    when(service.validarPersistencia(result, 200)).thenReturn(validation);
    MidiaRestritaRegularizacaoRunner runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, tempDir.resolve("apply.tsv").toString());

    runner.run(mock(ApplicationArguments.class));

    verify(service, times(2)).planejar();
    verify(service).aplicar(eq(result), eq(200), any());
    verify(service).validarPersistencia(result, 200);
    verify(context).close();
  }

  @Test
  void falhaDepoisDoCommitPreservaMarcadorEErroOriginalSemRepetirApply() throws Exception {
    var service = mock(MidiaRestritaRegularizacaoService.class);
    var context = mock(ConfigurableApplicationContext.class);
    var result = resultadoVazio();
    RuntimeException erroOriginal = new IllegalStateException("falha apos commit");
    when(service.planejar()).thenReturn(result).thenThrow(erroOriginal);
    doAnswer(invocation -> {
      invocation.<Consumer<EstadoCommit>>getArgument(2).accept(EstadoCommit.COMMITTED);
      return new MidiaRestritaRegularizacaoService.Aplicacao(2, 1, 2);
    }).when(service).aplicar(eq(result), eq(200), any());
    doThrow(new IllegalStateException("falha ao fechar")).when(context).close();
    Path report = tempDir.resolve("post-commit.tsv");
    var runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, report.toString());

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class))).isSameAs(erroOriginal);
    String journal = Files.readString(Path.of(report + ".state.jsonl"));
    assertThat(journal).contains("\"stage\":\"APPLY_STARTED\",\"commit\":\"UNKNOWN\"",
        "\"stage\":\"APPLY_FINISHED\",\"commit\":\"COMMITTED\"",
        "\"stage\":\"FAILED\",\"commit\":\"COMMITTED\"");
    assertThat(journal).doesNotContain("SUCCEEDED", "falha apos commit");
    assertThat(erroOriginal.getSuppressed()).hasSize(1);
    verify(service).aplicar(eq(result), eq(200), any());
  }

  @Test
  void erroSemConfirmacaoDeCommitFicaAmbiguoSemReplay() throws Exception {
    var service = mock(MidiaRestritaRegularizacaoService.class);
    var context = mock(ConfigurableApplicationContext.class);
    var result = resultadoVazio();
    when(service.planejar()).thenReturn(result);
    when(service.aplicar(eq(result), eq(200), any())).thenThrow(
        new IllegalStateException("resposta do commit perdida"));
    Path report = tempDir.resolve("ambiguous.tsv");
    var runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, report.toString());

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Files.readString(Path.of(report + ".state.jsonl")))
        .contains("\"stage\":\"FAILED\",\"commit\":\"UNKNOWN\"")
        .doesNotContain("COMMITTED", "ROLLED_BACK", "SUCCEEDED");
    verify(service).aplicar(eq(result), eq(200), any());
    verify(service).planejar();
  }

  @Test
  void erroPreCommitRegistraRollbackConfirmado() throws Exception {
    var service = mock(MidiaRestritaRegularizacaoService.class);
    var context = mock(ConfigurableApplicationContext.class);
    var result = resultadoVazio();
    when(service.planejar()).thenReturn(result);
    doAnswer(invocation -> {
      invocation.<Consumer<EstadoCommit>>getArgument(2).accept(EstadoCommit.ROLLED_BACK);
      throw new IllegalStateException("lote revertido");
    }).when(service).aplicar(eq(result), eq(200), any());
    Path report = tempDir.resolve("rollback.tsv");
    var runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, report.toString());

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Files.readString(Path.of(report + ".state.jsonl")))
        .contains("\"stage\":\"FAILED\",\"commit\":\"ROLLED_BACK\"")
        .doesNotContain("\"commit\":\"COMMITTED\"", "SUCCEEDED");
  }

  @Test
  void evidenciaExistenteNaoEhSobrescritaNemDisparaBanco() throws Exception {
    var service = mock(MidiaRestritaRegularizacaoService.class);
    var context = mock(ConfigurableApplicationContext.class);
    Path report = tempDir.resolve("preserved.tsv");
    Files.writeString(report, "evidencia anterior\n");
    var runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, report.toString());
    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Evidencia anterior");
    assertThat(Files.readString(report)).isEqualTo("evidencia anterior\n");
    verify(service, never()).planejar();
  }

  @Test
  void capturaPrivadaPreservaCincoValoresEIdsSemExportarIdentidadeOriginal() throws Exception {
    var fileId = java.util.UUID.randomUUID();
    var linkId = java.util.UUID.randomUUID();
    var confirmed = java.time.OffsetDateTime.parse("2026-09-01T12:00:00Z");
    var arquivo = br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity.criarUploadPendente(
        fileId, "R2", "do-not-export-bucket", "do-not-export-original-key",
        "do-not-export-name", "image/jpeg", 100L, 10, 10, null, "a".repeat(64), confirmed);
    arquivo.aplicarDecisao(br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia.VALIDADO);
    String preview = "publicas/restritas-borradas/v1/synthetic-preview.jpg";
    arquivo.marcarPreviewRestritoDisponivel(preview, "v1", confirmed);
    var estado = new br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.EstadoArquivo(
        fileId, "b".repeat(64), "c".repeat(64), "DISPONIVEL", false);
    var vinculo = new br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.EstadoVinculo(
        linkId, fileId, "d".repeat(64));
    var plano = new MidiaRestritaRegularizacaoService.Resultado(1, 1, 1, 0, 0, 0, 1,
        List.of(new MidiaRestritaRegularizacaoService.Item(arquivo, preview,
            MidiaRestritaRegularizacaoService.Classificacao.DISPONIVEL)))
        .comSnapshot(new MidiaRestritaRegularizacaoService.Snapshot(java.util.Map.of(fileId, estado), List.of(vinculo)));
    var service = mock(MidiaRestritaRegularizacaoService.class);
    var context = mock(ConfigurableApplicationContext.class);
    when(service.planejar()).thenReturn(plano);
    when(service.validarPersistencia(plano, 200)).thenReturn(
        new MidiaRestritaRegularizacaoService.Validacao(1, 1, 0, 0, 0));
    doAnswer(invocation -> {
      invocation.<Consumer<EstadoCommit>>getArgument(2).accept(EstadoCommit.COMMITTED);
      return new MidiaRestritaRegularizacaoService.Aplicacao(0, 1, 1);
    }).when(service).aplicar(eq(plano), eq(200), any());
    Path report = tempDir.resolve("private-values.tsv");

    new MidiaRestritaRegularizacaoRunner(service, context, "APPLY", true, 200, report.toString())
        .run(mock(ApplicationArguments.class));

    Path before = Path.of(report + ".before.json");
    byte[] bytes = Files.readAllBytes(before);
    var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
    var row = data.path("arquivos").get(0);
    assertThat(row.path("arquivo_id").asText()).isEqualTo(fileId.toString());
    assertThat(row.path("preview_restrito_tipo").asText()).isEqualTo("PREVIEW_RESTRITO");
    assertThat(row.path("preview_restrito_chave").asText()).isEqualTo(preview);
    assertThat(row.path("preview_restrito_pipeline_versao").asText()).isEqualTo("v1");
    assertThat(row.path("preview_restrito_status").asText()).isEqualTo("DISPONIVEL");
    assertThat(row.path("preview_restrito_confirmado_em").asText()).isEqualTo(confirmed.toString());
    assertThat(row.path("estado_completo_sha256").asText()).isEqualTo(estado.completo());
    assertThat(data.path("vinculos").get(0).path("anuncio_midia_id").asText()).isEqualTo(linkId.toString());
    assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("do-not-export");
    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    String journal = Files.readString(Path.of(report + ".state.jsonl"));
    assertThat(journal).contains("\"stage\":\"BEFORE_CAPTURED\",\"commit\":\"NOT_STARTED\"", hash)
        .doesNotContain(preview, fileId.toString(), linkId.toString());
    assertThat(Files.readString(report)).doesNotContain(preview, fileId.toString(), linkId.toString());
    if (before.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      assertThat(Files.getPosixFilePermissions(before))
          .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
  }

  @Test
  void falhaAoCriarCapturaPrivadaDepoisDoPlanNaoIniciaApply() throws Exception {
    var service = mock(MidiaRestritaRegularizacaoService.class);
    var context = mock(ConfigurableApplicationContext.class);
    Path report = tempDir.resolve("capture-failure.tsv");
    when(service.planejar()).thenAnswer(invocation -> {
      Files.createDirectory(Path.of(report + ".before.json"));
      return resultadoVazio();
    });
    var runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, report.toString());
    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPLY nao iniciado");
    assertThat(Files.readString(Path.of(report + ".state.jsonl")))
        .contains("\"stage\":\"FAILED\",\"commit\":\"NOT_STARTED\"")
        .doesNotContain("APPLY_STARTED", "before_sha256");
    verify(service, never()).aplicar(any(), eq(200), any());
  }

  @Test
  void validateFalhaFechadoComPendenteOuInconsistente() {
    MidiaRestritaRegularizacaoService service = mock(MidiaRestritaRegularizacaoService.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    var result = resultadoVazio();
    var validation = new MidiaRestritaRegularizacaoService.Validacao(1, 0, 0, 1, 0);
    when(service.planejar()).thenReturn(result);
    when(service.validarPersistencia(result, 200)).thenReturn(validation);
    MidiaRestritaRegularizacaoRunner runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "VALIDATE", false, 200,
        tempDir.resolve("validate.tsv").toString());

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reprovada");

    verify(service, never()).aplicar(result, 200);
    verify(context).close();
  }

  @Test
  void recusaRelatorioDentroDaWorktreeAntesDeListar() {
    MidiaRestritaRegularizacaoService service = mock(MidiaRestritaRegularizacaoService.class);
    ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
    Path insideRepository = Path.of("target", "preview-plan.tsv").toAbsolutePath();
    MidiaRestritaRegularizacaoRunner runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "PLAN", false, 200, insideRepository.toString());

    assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fora do Git");

    verify(service, never()).planejar();
    verify(context).close();
  }

  private MidiaRestritaRegularizacaoService.Resultado resultadoVazio() {
    return new MidiaRestritaRegularizacaoService.Resultado(
        0, 0, 0, 0, 0, 0, 1, List.of())
        .comSnapshot(new MidiaRestritaRegularizacaoService.Snapshot(java.util.Map.of(), List.of()));
  }
}
