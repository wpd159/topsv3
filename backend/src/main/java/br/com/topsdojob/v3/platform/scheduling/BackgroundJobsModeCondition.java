package br.com.topsdojob.v3.platform.scheduling;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class BackgroundJobsModeCondition implements Condition {

  private static final String PROPERTY = "app.background-jobs.mode";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String configured = context.getEnvironment().getProperty(PROPERTY);
    String mode = configured == null ? "LEGACY" : configured;
    return switch (mode) {
      case "LEGACY", "WORKER" -> true;
      case "WEB" -> false;
      default -> throw new IllegalStateException(
          "app.background-jobs.mode invalido. Valores aceitos: LEGACY, WEB, WORKER.");
    };
  }
}
