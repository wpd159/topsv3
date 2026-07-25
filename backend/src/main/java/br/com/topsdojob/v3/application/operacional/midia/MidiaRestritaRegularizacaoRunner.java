package br.com.topsdojob.v3.application.operacional.midia;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("homologacao")
@ConditionalOnProperty(
    name = "app.restricted-media-preview-regularization.enabled",
    havingValue = "true")
public class MidiaRestritaRegularizacaoRunner implements ApplicationRunner {

  private final MidiaRestritaRegularizacaoService service;
  private final ConfigurableApplicationContext applicationContext;

  public MidiaRestritaRegularizacaoRunner(
      MidiaRestritaRegularizacaoService service,
      ConfigurableApplicationContext applicationContext) {
    this.service = service;
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      MidiaRestritaRegularizacaoService.Resultado resultado = service.regularizar();
      System.out.println("RESTRICTED_MEDIA_PREVIEW_REGULARIZATION_RESULT="
          + resultado.vinculos() + ":"
          + resultado.elegiveis() + ":"
          + resultado.criadas() + ":"
          + resultado.preservadas());
    } finally {
      applicationContext.close();
    }
  }
}
