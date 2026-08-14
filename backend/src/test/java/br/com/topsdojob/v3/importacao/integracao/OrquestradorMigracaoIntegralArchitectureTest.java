package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.topsdojob.v3.importacao.anuncio.ImportadorAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.comercial.ImportadorConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.conteudoseo.ImportadorConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.financeiro.ImportadorFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

class OrquestradorMigracaoIntegralArchitectureTest {

  private static final String PROFILE = "migracao-integral";
  private static final String ENABLED = "app.migracao.integral.enabled";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          HealthContributorAutoConfiguration.class,
          HealthEndpointAutoConfiguration.class))
      .withBean(DataSource.class, () -> mock(DataSource.class))
      .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
      .withBean(ObjectMapper.class, ObjectMapper::new)
      .withBean(ImportadorAnunciosFaseUm.class, () -> mock(ImportadorAnunciosFaseUm.class))
      .withBean(
          ImportadorConteudoSeoFaseDois.class,
          () -> mock(ImportadorConteudoSeoFaseDois.class))
      .withBean(
          ImportadorConfiguracaoComercialFaseTres.class,
          () -> mock(ImportadorConfiguracaoComercialFaseTres.class))
      .withBean(
          ImportadorFinanceiroFaseQuatro.class,
          () -> mock(ImportadorFinanceiroFaseQuatro.class))
      .withUserConfiguration(ContextoMigracao.class);

  @Test
  void startupNormalNaoCriaGrafoDaMigracaoEHealthFicaUp() {
    contextRunner.run(this::assertMigracaoAusente);
  }

  @Test
  void enabledTrueSemProfileNaoCriaGrafoDaMigracao() {
    contextRunner
        .withPropertyValues(ENABLED + "=true")
        .run(this::assertMigracaoAusente);
  }

  @Test
  void profileAtivoComEnabledFalseNaoCriaGrafoDaMigracao() {
    contextRunner
        .withPropertyValues("spring.profiles.active=" + PROFILE, ENABLED + "=false")
        .run(this::assertMigracaoAusente);
  }

  @Test
  void profileAtivoSemEnabledNaoCriaGrafoDaMigracao() {
    contextRunner
        .withPropertyValues("spring.profiles.active=" + PROFILE)
        .run(this::assertMigracaoAusente);
  }

  @Test
  void profileAtivoComEnabledTrueCriaUmUnicoGrafoSemExecutarImportadores() {
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=" + PROFILE,
            ENABLED + "=true",
            "app.migracao.integral.operacao=EXECUTAR",
            "app.migracao.integral.storage.fonte.endpoint=http://127.0.0.1:19000",
            "app.migracao.integral.storage.fonte.access-key=local-test-access",
            "app.migracao.integral.storage.fonte.signing-value=local-test-signing",
            "app.migracao.integral.storage.fonte.public-media-bucket=public-source",
            "app.migracao.integral.storage.fonte.private-media-bucket=private-source",
            "app.migracao.integral.storage.fonte.document-bucket=document-source",
            "app.migracao.integral.storage.destino.endpoint=http://127.0.0.1:19001",
            "app.migracao.integral.storage.destino.access-key=local-test-access",
            "app.migracao.integral.storage.destino.signing-value=local-test-signing",
            "app.migracao.integral.storage.destino.public-media-bucket=public-target",
            "app.migracao.integral.storage.destino.private-media-bucket=private-target",
            "app.migracao.integral.storage.destino.document-bucket=document-target")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(OrquestradorMigracaoIntegral.class);
          assertThat(context).hasSingleBean(ExecutorImportadoresCanonicos.class);
          assertThat(context).hasSingleBean(MigracaoIntegralRunner.class);
          assertThat(context).hasSingleBean(ProdutorPacoteMigracaoIntegral.class);
          assertThat(context).hasSingleBean(MigracaoIntegralStorageConfiguration.class);
          assertThat(context).hasSingleBean(ArmazenamentosMigracaoIntegral.class);
          assertThat(context).hasSingleBean(MigracaoIntegralProperties.class);
          assertHealthUp(context);
          verifyNoInteractions(
              context.getBean(DataSource.class),
              context.getBean(ImportadorAnunciosFaseUm.class),
              context.getBean(ImportadorConteudoSeoFaseDois.class),
              context.getBean(ImportadorConfiguracaoComercialFaseTres.class),
              context.getBean(ImportadorFinanceiroFaseQuatro.class));
        });
  }

  @Test
  void operacaoProduzirPacoteNaoCriaClienteDeStorage() {
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=" + PROFILE,
            ENABLED + "=true",
            "app.migracao.integral.operacao=PRODUZIR_PACOTE")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(MigracaoIntegralRunner.class);
          assertThat(context).hasSingleBean(ProdutorPacoteMigracaoIntegral.class);
          assertThat(context).doesNotHaveBean(ArmazenamentosMigracaoIntegral.class);
          assertThat(context).doesNotHaveBean(ObjectStorage.class);
          verifyNoInteractions(
              context.getBean(DataSource.class),
              context.getBean(ImportadorAnunciosFaseUm.class),
              context.getBean(ImportadorConteudoSeoFaseDois.class),
              context.getBean(ImportadorConfiguracaoComercialFaseTres.class),
              context.getBean(ImportadorFinanceiroFaseQuatro.class));
        });
  }

  @Test
  void operacaoProduzirManifestoCriaSomenteFonteReadOnlyEBeansCanonicos() {
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=" + PROFILE,
            ENABLED + "=true",
            "app.migracao.integral.operacao=PRODUZIR_MANIFESTO",
            "app.migracao.integral.storage.fonte.endpoint=http://127.0.0.1:19000",
            "app.migracao.integral.storage.fonte.access-key=local-test-access",
            "app.migracao.integral.storage.fonte.signing-value=local-test-signing",
            "app.migracao.integral.storage.fonte.public-media-bucket=public-source",
            "app.migracao.integral.storage.fonte.private-media-bucket=private-source",
            "app.migracao.integral.storage.fonte.document-bucket=document-source",
            "app.migracao.integral.storage.fonte.public-base-url=https://media.example.test",
            "app.migracao.integral.storage.destino.public-media-bucket=public-target",
            "app.migracao.integral.storage.destino.private-media-bucket=private-target",
            "app.migracao.integral.storage.destino.document-bucket=document-target")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(ProdutorManifestoMidiaFaseCinco.class);
          assertThat(context).hasSingleBean(RepositorioCandidatosMidiaLegada.class);
          assertThat(context).hasSingleBean(FonteMidiaMigracao.class);
          assertThat(context).doesNotHaveBean(ArmazenamentosMigracaoIntegral.class);
          assertThat(context).doesNotHaveBean(ObjectStorage.class);
          verifyNoInteractions(context.getBean(DataSource.class));
        });
  }

  @Test
  void todosOsBeansExclusivosExigemMesmoProfileEEnabledTrue() {
    assertCondicoesFailClosed(OrquestradorMigracaoIntegral.class);
    assertCondicoesFailClosed(ExecutorImportadoresCanonicos.class);
    assertCondicoesFailClosed(MigracaoIntegralApplication.class);
    assertCondicoesFailClosed(MigracaoIntegralRunner.class);
    assertCondicoesFailClosed(ProdutorPacoteMigracaoIntegral.class);
    assertCondicoesFailClosed(ProdutorManifestoMidiaFaseCinco.class);
    assertCondicoesFailClosed(RepositorioCandidatosMidiaLegada.class);
    assertCondicoesFailClosed(MigracaoIntegralStorageConfiguration.class);
  }

  @Test
  void fixaOrdemCanonicaSemSubstituirImportadoresPorSqlLegado() throws Exception {
    assertThat(OrquestradorMigracaoIntegral.ordemCanonica()).containsExactly(
        FaseMigracaoIntegral.REFERENCIAS_LOCALIDADES,
        FaseMigracaoIntegral.USUARIOS_MAPEAMENTOS,
        FaseMigracaoIntegral.CREDENCIAIS,
        FaseMigracaoIntegral.KYC_PLANEJAMENTO,
        FaseMigracaoIntegral.CONTEUDO_SEO,
        FaseMigracaoIntegral.CATALOGO_COMERCIAL,
        FaseMigracaoIntegral.MANIFESTO_MIDIA,
        FaseMigracaoIntegral.KYC_PERSISTENCIA,
        FaseMigracaoIntegral.ANUNCIOS_E_VINCULOS,
        FaseMigracaoIntegral.SEO_ANUNCIOS,
        FaseMigracaoIntegral.FINANCEIRO,
        FaseMigracaoIntegral.FAVORITOS_METRICAS,
        FaseMigracaoIntegral.RECONCILIADORES,
        FaseMigracaoIntegral.FINGERPRINT);

    String executor = Files.readString(Path.of(
        "src/main/java/br/com/topsdojob/v3/importacao/integracao/ExecutorImportadoresCanonicos.java"));
    assertThat(executor)
        .contains("ImportadorAnunciosFaseUm")
        .contains("ImportadorConteudoSeoFaseDois")
        .contains("ImportadorConfiguracaoComercialFaseTres")
        .contains("ImportadorFinanceiroFaseQuatro")
        .contains("MotorCopiaMidiaFaseCinco")
        .doesNotContain("dryrun-producao-v3-saneado.sql");

    String comandoOperacional = Files.readString(Path.of(
        "..", "scripts", "local", "importacao", "executar-migracao-integral.ps1"));
    assertThat(comandoOperacional)
        .contains("spring-boot.run.main-class=br.com.topsdojob.v3.importacao.integracao."
            + "MigracaoIntegralApplication")
        .contains("spring-boot.run.profiles=migracao-integral")
        .contains("spring.task.scheduling.enabled=false")
        .contains("app.outbox.email.enabled=false")
        .contains("app.storage.r2.enabled=false")
        .contains("efi.pix.enabled=false")
        .contains("app.migracao.integral.enabled=true")
        .contains("app.migracao.integral.operacao=")
        .contains("app.migracao.integral.pacote=")
        .contains("app.migracao.integral.diretorio-manifestos=")
        .contains("aceita somente snapshot restaurado em PostgreSQL de loopback")
        .contains("spring.flyway.enabled=false")
        .contains("spring.jpa.hibernate.ddl-auto=none")
        .contains("APPLY exige -ConfirmarApply explicitamente")
        .doesNotContain("dryrun-producao-v3-saneado.sql")
        .doesNotContain("psql");
  }

  @Test
  void propriedadesExigemHabilitacaoOperacaoPacoteManifestosModoELoteEConfirmacao() {
    MigracaoIntegralProperties properties = new MigracaoIntegralProperties();
    assertThatThrownBy(properties::validar)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("habilitada");

    properties.setEnabled(true);
    properties.setOperacao(OperacaoMigracaoIntegral.EXECUTAR);
    properties.setPacote(Path.of("pacote.json"));
    properties.setDiretorioManifestos(Path.of("manifestos"));
    properties.setOrigemId("TOPSDOJOB_LEGADO");
    properties.setModo(ModoMigracaoIntegral.APPLY);
    properties.setTamanhoLote(500);
    assertThatThrownBy(properties::validar)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("confirmacao");

    properties.setConfirmarApply(true);
    properties.validar();
  }

  private void assertMigracaoAusente(AssertableApplicationContext context) {
    assertThat(context).hasNotFailed();
    assertThat(context).doesNotHaveBean(OrquestradorMigracaoIntegral.class);
    assertThat(context).doesNotHaveBean(ExecutorImportadoresCanonicos.class);
    assertThat(context).doesNotHaveBean(MigracaoIntegralRunner.class);
    assertThat(context).doesNotHaveBean(ProdutorPacoteMigracaoIntegral.class);
    assertThat(context).doesNotHaveBean(ProdutorManifestoMidiaFaseCinco.class);
    assertThat(context).doesNotHaveBean(RepositorioCandidatosMidiaLegada.class);
    assertThat(context).doesNotHaveBean(MigracaoIntegralStorageConfiguration.class);
    assertThat(context).doesNotHaveBean(ArmazenamentosMigracaoIntegral.class);
    assertThat(context).doesNotHaveBean(MigracaoIntegralProperties.class);
    assertHealthUp(context);
    verifyNoInteractions(
        context.getBean(DataSource.class),
        context.getBean(ImportadorAnunciosFaseUm.class),
        context.getBean(ImportadorConteudoSeoFaseDois.class),
        context.getBean(ImportadorConfiguracaoComercialFaseTres.class),
        context.getBean(ImportadorFinanceiroFaseQuatro.class));
  }

  private void assertHealthUp(AssertableApplicationContext context) {
    assertThat(context.getBean(HealthEndpoint.class).health().getStatus()).isEqualTo(Status.UP);
  }

  private void assertCondicoesFailClosed(Class<?> beanType) {
    Profile profile = beanType.getAnnotation(Profile.class);
    ConditionalOnProperty condition = beanType.getAnnotation(ConditionalOnProperty.class);
    assertThat(profile).isNotNull();
    assertThat(profile.value()).containsExactly(PROFILE);
    assertThat(condition).isNotNull();
    assertThat(condition.prefix()).isEqualTo("app.migracao.integral");
    assertThat(condition.name()).containsExactly("enabled");
    assertThat(condition.havingValue()).isEqualTo("true");
    assertThat(condition.matchIfMissing()).isFalse();
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
      OrquestradorMigracaoIntegral.class,
      ExecutorImportadoresCanonicos.class,
      MigracaoIntegralRunner.class,
      ProdutorPacoteMigracaoIntegral.class,
      ProdutorManifestoMidiaFaseCinco.class,
      RepositorioCandidatosMidiaLegada.class,
      MigracaoIntegralStorageConfiguration.class
  })
  static class ContextoMigracao {
  }
}
