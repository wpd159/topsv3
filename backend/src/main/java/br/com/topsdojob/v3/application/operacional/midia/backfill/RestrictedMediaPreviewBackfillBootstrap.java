package br.com.topsdojob.v3.application.operacional.midia.backfill;

import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public final class RestrictedMediaPreviewBackfillBootstrap {

  public static final String BOOTSTRAP_ARGUMENT =
      "--app.bootstrap=restricted-media-preview-backfill";

  private RestrictedMediaPreviewBackfillBootstrap() {
  }

  public static boolean requested(String[] args) {
    return args != null && Arrays.asList(args).contains(BOOTSTRAP_ARGUMENT);
  }

  public static ConfigurableApplicationContext run(String[] args) {
    return application().run(args);
  }

  static SpringApplication application() {
    SpringApplication application =
        new SpringApplication(RestrictedMediaPreviewBackfillConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setDefaultProperties(Map.of(
        "spring.main.banner-mode", "off",
        "spring.task.scheduling.enabled", "false",
        "spring.flyway.enabled", "false"));
    return application;
  }
}
