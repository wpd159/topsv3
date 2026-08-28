package br.com.topsdojob.v3.application.operacional.midia;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    name = "app.restricted-media-preview-reconciliation.enabled",
    havingValue = "true")
public class MidiaRestritaRegularizacaoRunner implements ApplicationRunner {

  private final MidiaRestritaRegularizacaoService service;
  private final ConfigurableApplicationContext applicationContext;
  private final String mode;
  private final boolean applyConfirmed;
  private final int batchSize;
  private final Path reportPath;

  public MidiaRestritaRegularizacaoRunner(
      MidiaRestritaRegularizacaoService service,
      ConfigurableApplicationContext applicationContext,
      @Value("${app.restricted-media-preview-reconciliation.mode:PLAN}") String mode,
      @Value("${app.restricted-media-preview-reconciliation.apply-confirmed:false}")
          boolean applyConfirmed,
      @Value("${app.restricted-media-preview-reconciliation.batch-size:200}") int batchSize,
      @Value("${app.restricted-media-preview-reconciliation.report-path:}") String reportPath) {
    this.service = service;
    this.applicationContext = applicationContext;
    this.mode = mode == null ? "PLAN" : mode.trim().toUpperCase(Locale.ROOT);
    this.applyConfirmed = applyConfirmed;
    this.batchSize = batchSize;
    this.reportPath = reportPath == null || reportPath.isBlank() ? null : Path.of(reportPath);
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      validarExecucao();
      MidiaRestritaRegularizacaoService.Resultado plano = service.planejar();
      escreverRelatorio(plano);
      MidiaRestritaRegularizacaoService.Aplicacao aplicacao =
          new MidiaRestritaRegularizacaoService.Aplicacao(0, 0, 0);
      MidiaRestritaRegularizacaoService.Validacao validacao =
          new MidiaRestritaRegularizacaoService.Validacao(0, -1, -1, -1, -1);
      MidiaRestritaRegularizacaoService.Resultado inventarioFinal = plano;

      if ("APPLY".equals(mode)) {
        aplicacao = service.aplicar(plano, batchSize);
        inventarioFinal = service.planejar();
        validacao = service.validarPersistencia(inventarioFinal, batchSize);
        exigirValidacao(validacao);
      } else if ("VALIDATE".equals(mode)) {
        validacao = service.validarPersistencia(plano, batchSize);
        exigirValidacao(validacao);
      }

      imprimirResultado(inventarioFinal, aplicacao, validacao);
    } finally {
      applicationContext.close();
    }
  }

  private void validarExecucao() {
    if (!"PLAN".equals(mode) && !"APPLY".equals(mode) && !"VALIDATE".equals(mode)) {
      throw new IllegalArgumentException("Modo de reconciliacao invalido");
    }
    if ("APPLY".equals(mode) && !applyConfirmed) {
      throw new IllegalStateException("APPLY exige confirmacao explicita");
    }
    if (batchSize < 1 || batchSize > 1_000) {
      throw new IllegalArgumentException("Tamanho de lote invalido");
    }
    validarCaminhoRelatorio();
  }

  private void exigirValidacao(MidiaRestritaRegularizacaoService.Validacao validacao) {
    if (!validacao.aprovada()) {
      throw new IllegalStateException("Validacao persistida dos previews reprovada");
    }
  }

  private void imprimirResultado(
      MidiaRestritaRegularizacaoService.Resultado plano,
      MidiaRestritaRegularizacaoService.Aplicacao aplicacao,
      MidiaRestritaRegularizacaoService.Validacao validacao) {
    System.out.printf(Locale.ROOT, "%s%n", String.join(" ",
        "RESTRICTED_MEDIA_PREVIEW_RECONCILIATION_RESULT",
        "mode=" + mode,
        "links=" + plano.vinculos(),
        "eligible=" + plano.arquivos(),
        "r2_available=" + plano.disponiveis(),
        "missing=" + plano.ausentes(),
        "inconsistent=" + plano.inconsistentes(),
        "unproven=" + plano.naoComprovados(),
        "r2_pages=" + plano.paginasR2(),
        "db_updated=" + aplicacao.atualizados(),
        "db_unchanged=" + aplicacao.inalterados(),
        "batches=" + aplicacao.lotes(),
        "db_available=" + validacao.disponiveis(),
        "db_unknown=" + validacao.desconhecidos(),
        "db_pending=" + validacao.pendentes(),
        "db_inconsistent=" + validacao.inconsistentes()));
  }

  private void escreverRelatorio(MidiaRestritaRegularizacaoService.Resultado plano) {
    Path absolute = reportPath.toAbsolutePath().normalize();
    StringBuilder report = new StringBuilder("arquivo_hash\tchave_hash\tclassificacao\n");
    for (MidiaRestritaRegularizacaoService.Item item : plano.itens()) {
      report.append(hash(item.arquivo().getId().toString())).append('\t')
          .append(hash(item.chaveEsperada())).append('\t')
          .append(item.classificacao()).append('\n');
    }
    try {
      if (absolute.getParent() != null) {
        Files.createDirectories(absolute.getParent());
      }
      Files.writeString(absolute, report, StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao escrever relatorio sanitizado", exception);
    }
  }

  private void validarCaminhoRelatorio() {
    if (reportPath == null) {
      throw new IllegalStateException("Caminho externo do relatorio obrigatorio");
    }
    Path absolute = reportPath.toAbsolutePath().normalize();
    localizarRaizGit().ifPresent(repository -> {
      if (absolute.startsWith(repository)) {
        throw new IllegalArgumentException("Relatorio deve permanecer fora do Git");
      }
    });
  }

  private Optional<Path> localizarRaizGit() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.exists(current.resolve(".git"))) {
        return Optional.of(current);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  private String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
