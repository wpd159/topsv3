package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(MigracaoIntegralProperties.class)
public class MigracaoIntegralRunner implements ApplicationRunner {

  private final OrquestradorMigracaoIntegral orquestrador;
  private final MigracaoIntegralProperties properties;
  private final ObjectMapper mapper;
  private final Environment environment;
  private final ConfigurableApplicationContext applicationContext;

  public MigracaoIntegralRunner(
      OrquestradorMigracaoIntegral orquestrador,
      MigracaoIntegralProperties properties,
      ObjectMapper mapper,
      Environment environment,
      ConfigurableApplicationContext applicationContext) {
    this.orquestrador = orquestrador;
    this.properties = properties;
    this.mapper = mapper;
    this.environment = environment;
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      properties.validar();
      validarExecucaoIsolada();
      PacoteMigracaoIntegral pacote = carregarPacote(properties.getPacote());
      OrquestradorMigracaoIntegral.Relatorio relatorio = orquestrador.executar(
          pacote,
          properties.getModo(),
          properties.getTamanhoLote());
      imprimirRelatorioSanitizado(relatorio);
    } finally {
      applicationContext.close();
    }
  }

  private PacoteMigracaoIntegral carregarPacote(Path pacote) {
    Path normalizado = pacote.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalizado, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("pacote da migracao integral nao e um arquivo regular");
    }
    try {
      return mapper.readValue(normalizado.toFile(), PacoteMigracaoIntegral.class);
    } catch (IOException exception) {
      throw new IllegalStateException("pacote da migracao integral nao pode ser lido", exception);
    }
  }

  private void validarExecucaoIsolada() {
    String tipoAplicacao = environment.getProperty("spring.main.web-application-type");
    if (!"none".equalsIgnoreCase(tipoAplicacao)) {
      throw new IllegalStateException("migracao integral exige aplicacao nao web");
    }
    exigirDesabilitado("efi.pix.enabled");
    exigirDesabilitado("efi.pix.reconciliation-enabled");
    exigirDesabilitado("efi.pix.webhook-registration-enabled");
    exigirDesabilitado("app.outbox.email.enabled");
    exigirDesabilitado("app.storage.r2.enabled");
  }

  private void exigirDesabilitado(String propriedade) {
    if (environment.getProperty(propriedade, Boolean.class, false)) {
      throw new IllegalStateException("integracao externa habilitada durante a migracao integral");
    }
  }

  private void imprimirRelatorioSanitizado(OrquestradorMigracaoIntegral.Relatorio relatorio) {
    System.out.println("MIGRACAO_INTEGRAL_STATUS=" + relatorio.status());
    System.out.println("MIGRACAO_INTEGRAL_MODO=" + relatorio.modo());
    System.out.println("MIGRACAO_INTEGRAL_RETOMADA=" + relatorio.retomada());
    System.out.println("MIGRACAO_INTEGRAL_FASES=" + relatorio.fasesConcluidas().size());
    System.out.println("MIGRACAO_INTEGRAL_PROCESSADOS=" + relatorio.metricas().processados());
    System.out.println("MIGRACAO_INTEGRAL_PENDENCIAS=" + relatorio.pendenciasAbertas());
    relatorio.metricas().fases().forEach((fase, resumo) -> System.out.println(
        "MIGRACAO_INTEGRAL_METRICA="
            + fase.name() + ":"
            + resumo.processados() + ":"
            + resumo.duracao().toMillis() + ":"
            + resumo.p50().toMillis() + ":"
            + resumo.p95().toMillis() + ":"
            + resumo.maximo().toMillis()));
  }
}
