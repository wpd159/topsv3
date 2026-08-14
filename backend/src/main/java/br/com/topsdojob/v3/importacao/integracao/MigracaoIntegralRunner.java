package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.DestinoProperties;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
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

  private final ObjectProvider<OrquestradorMigracaoIntegral> orquestrador;
  private final ProdutorPacoteMigracaoIntegral produtor;
  private final ObjectProvider<ProdutorManifestoMidiaFaseCinco> produtorManifesto;
  private final MigracaoIntegralProperties properties;
  private final ValidadorPacoteMigracaoIntegral validador;
  private final RepositorioPacoteMigracaoIntegral repositorio;
  private final Environment environment;
  private final ConfigurableApplicationContext applicationContext;

  public MigracaoIntegralRunner(
      ObjectProvider<OrquestradorMigracaoIntegral> orquestrador,
      ProdutorPacoteMigracaoIntegral produtor,
      ObjectProvider<ProdutorManifestoMidiaFaseCinco> produtorManifesto,
      MigracaoIntegralProperties properties,
      ObjectMapper mapper,
      DestinoProperties destinoProperties,
      Environment environment,
      ConfigurableApplicationContext applicationContext) {
    this.orquestrador = orquestrador;
    this.produtor = produtor;
    this.produtorManifesto = produtorManifesto;
    this.properties = properties;
    this.validador = new ValidadorPacoteMigracaoIntegral(mapper, destinoProperties.r2());
    this.repositorio = new RepositorioPacoteMigracaoIntegral(mapper);
    this.environment = environment;
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      properties.validar();
      validarExecucaoIsolada();
      switch (properties.getOperacao()) {
        case PRODUZIR_MANIFESTO -> produzirManifesto();
        case VALIDAR_MANIFESTO -> validarManifesto();
        case PRODUZIR_PACOTE -> produzirPacote();
        case VALIDAR_PACOTE -> validarPacote();
        case EXECUTAR -> executar();
      }
    } finally {
      applicationContext.close();
    }
  }

  private void produzirManifesto() {
    ProdutorManifestoMidiaFaseCinco produtorDisponivel = produtorManifesto.getIfAvailable();
    if (produtorDisponivel == null) {
      throw new IllegalStateException("produtor canonico do manifesto nao esta disponivel");
    }
    var resultado = produtorDisponivel.produzir(new ProdutorManifestoMidiaFaseCinco.Parametros(
        properties.getOrigemId(),
        properties.getSnapshotSha256(),
        properties.getExecucaoId(),
        properties.getCapturadoEm(),
        properties.getManifestoFaseCinco()));
    System.out.println("MIGRACAO_INTEGRAL_MANIFESTO_STATUS=" + resultado.estado());
    System.out.println("MIGRACAO_INTEGRAL_MANIFESTO_FINGERPRINT=" + resultado.fingerprint());
    imprimirContagens("MIGRACAO_INTEGRAL_MANIFESTO_CONTAGEM", resultado.contagens());
    imprimirContagens("MIGRACAO_INTEGRAL_R2_READ_ONLY", resultado.storage());
  }

  private ArquivoManifestoMidiaFaseCinco validarManifesto() {
    RepositorioManifestoMidiaFaseCinco repositorioManifesto =
        new RepositorioManifestoMidiaFaseCinco(
            applicationContext.getBean(ObjectMapper.class));
    ArquivoManifestoMidiaFaseCinco arquivo = repositorioManifesto.carregar(
        properties.getManifestoFaseCinco());
    new ValidadorManifestoMidiaFaseCinco(
        applicationContext.getBean(ObjectMapper.class),
        applicationContext.getBean(DestinoProperties.class).r2())
        .validar(arquivo, properties.getOrigemId(), properties.getSnapshotSha256());
    System.out.println("MIGRACAO_INTEGRAL_MANIFESTO_VALIDO=true");
    System.out.println("MIGRACAO_INTEGRAL_MANIFESTO_FINGERPRINT=" + arquivo.fingerprint());
    return arquivo;
  }

  private void produzirPacote() {
    var resultado = produtor.produzir(new ProdutorPacoteMigracaoIntegral.Parametros(
        properties.getExecucaoId(),
        properties.getOrigemId(),
        properties.getSnapshotSha256(),
        properties.getCapturadoEm(),
        properties.getDiretorioManifestos(),
        properties.getManifestoFaseCinco(),
        properties.getPacote()));
    System.out.println("MIGRACAO_INTEGRAL_PACOTE_STATUS=" + resultado.estado());
    System.out.println("MIGRACAO_INTEGRAL_PACOTE_FINGERPRINT=" + resultado.fingerprint());
    imprimirContagens("MIGRACAO_INTEGRAL_PACOTE_CONTAGEM", resultado.contagens());
    imprimirContagens("MIGRACAO_INTEGRAL_STAGING", resultado.usuariosStaging());
  }

  private ArquivoPacoteMigracaoIntegral validarPacote() {
    ArquivoPacoteMigracaoIntegral arquivo = repositorio.carregar(properties.getPacote());
    validador.validar(
        arquivo,
        properties.getOrigemId(),
        properties.getDiretorioManifestos());
    System.out.println("MIGRACAO_INTEGRAL_PACOTE_VALIDO=true");
    System.out.println("MIGRACAO_INTEGRAL_PACOTE_FINGERPRINT=" + arquivo.fingerprint());
    return arquivo;
  }

  private void executar() {
    ArquivoPacoteMigracaoIntegral arquivo = validarPacote();
    if (properties.getExecucaoId() != null
        && !properties.getExecucaoId().equals(arquivo.pacote().pacoteId())) {
      throw new IllegalStateException("identificador de execucao diverge do pacote");
    }
    OrquestradorMigracaoIntegral orquestradorDisponivel = orquestrador.getIfAvailable();
    if (orquestradorDisponivel == null) {
      throw new IllegalStateException("orquestrador canonico nao esta disponivel");
    }
    OrquestradorMigracaoIntegral.Relatorio relatorio = orquestradorDisponivel.executar(
        arquivo.pacote(),
        properties.getModo(),
        properties.getTamanhoLote(),
        properties.isRetomar());
    imprimirRelatorioSanitizado(relatorio);
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

  private void imprimirContagens(String prefixo, Map<String, Long> contagens) {
    contagens.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(item -> System.out.println(prefixo + "=" + item.getKey() + ":" + item.getValue()));
  }
}
