package br.com.topsdojob.v3.application.operacional.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    when(service.aplicar(result, 200)).thenReturn(application);
    when(service.validarPersistencia(result, 200)).thenReturn(validation);
    MidiaRestritaRegularizacaoRunner runner = new MidiaRestritaRegularizacaoRunner(
        service, context, "APPLY", true, 200, tempDir.resolve("apply.tsv").toString());

    runner.run(mock(ApplicationArguments.class));

    verify(service, times(2)).planejar();
    verify(service).aplicar(result, 200);
    verify(service).validarPersistencia(result, 200);
    verify(context).close();
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
        0, 0, 0, 0, 0, 0, 1, List.of());
  }
}
