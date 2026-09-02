package br.com.topsdojob.v3.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.topsdojob.v3.application.operacional.outbox.OutboxEmailDispatchService;
import br.com.topsdojob.v3.application.operacional.outbox.OutboxEmailProperties;
import br.com.topsdojob.v3.application.operacional.outbox.OutboxEmailScheduler;
import br.com.topsdojob.v3.application.publico.pagamento.EfiPagamentoConciliacaoService;
import br.com.topsdojob.v3.application.publico.pagamento.EfiPagamentoReconciliacaoScheduler;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixProperties;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

class BackgroundJobsModeConditionTest {

  private static final String PROPERTY = "app.background-jobs.mode=";
  private static final String INVALID_MESSAGE =
      "app.background-jobs.mode invalido. Valores aceitos: LEGACY, WEB, WORKER.";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withBean(OutboxEmailDispatchService.class, () -> mock(OutboxEmailDispatchService.class))
      .withBean(OutboxEmailProperties.class, () -> mock(OutboxEmailProperties.class))
      .withBean(EfiPixProperties.class, () -> mock(EfiPixProperties.class))
      .withBean(PagamentoRepository.class, () -> mock(PagamentoRepository.class))
      .withBean(EfiPagamentoConciliacaoService.class,
          () -> mock(EfiPagamentoConciliacaoService.class))
      .withBean(EfiPixGateway.class, () -> mock(EfiPixGateway.class))
      .withUserConfiguration(SchedulerConfiguration.class);

  @Test
  void propriedadeAusentePreservaOsDoisSchedulers() {
    contextRunner.run(context -> assertSchedulers(context, true));
  }

  @Test
  void legacyRegistraOsDoisSchedulers() {
    contextRunner.withPropertyValues(PROPERTY + "LEGACY")
        .run(context -> assertSchedulers(context, true));
  }

  @Test
  void webNaoRegistraSchedulers() {
    contextRunner.withPropertyValues(PROPERTY + "WEB")
        .run(context -> assertSchedulers(context, false));
  }

  @Test
  void workerRegistraOsDoisSchedulers() {
    contextRunner.withPropertyValues(PROPERTY + "WORKER")
        .run(context -> assertSchedulers(context, true));
  }

  @Test
  void valorVazioFalhaOContexto() {
    assertInvalidRaw("");
  }

  @Test
  void somenteEspacosFalhaOContexto() {
    assertInvalidRaw("   ");
  }

  @Test
  void minusculasFalhamOContexto() {
    assertInvalidProperty("legacy");
  }

  @Test
  void espacoFinalFalhaOContexto() {
    assertInvalidRaw("WEB ");
  }

  @Test
  void valorDesconhecidoFalhaOContexto() {
    assertInvalidProperty("INVALIDO");
  }

  private void assertSchedulers(
      org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
      boolean expected) {
    assertThat(context).hasNotFailed();
    if (expected) {
      assertThat(context).hasSingleBean(OutboxEmailScheduler.class);
      assertThat(context).hasSingleBean(EfiPagamentoReconciliacaoScheduler.class);
    } else {
      assertThat(context).doesNotHaveBean(OutboxEmailScheduler.class);
      assertThat(context).doesNotHaveBean(EfiPagamentoReconciliacaoScheduler.class);
    }
  }

  private void assertInvalidProperty(String mode) {
    assertInvalid(contextRunner.withPropertyValues(PROPERTY + mode));
  }

  private void assertInvalidRaw(String mode) {
    assertInvalid(contextRunner.withInitializer(rawMode(mode)));
  }

  private ApplicationContextInitializer<ConfigurableApplicationContext> rawMode(String mode) {
    return context -> context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource(
            "background-jobs-mode-raw",
            Map.of("app.background-jobs.mode", mode)));
  }

  private void assertInvalid(ApplicationContextRunner runner) {
    runner.run(context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure()).rootCause()
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(INVALID_MESSAGE);
    });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({OutboxEmailScheduler.class, EfiPagamentoReconciliacaoScheduler.class})
  static class SchedulerConfiguration {
  }
}
