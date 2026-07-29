package br.com.topsdojob.v3.application.wizard;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class WizardProgressDtos {

  private WizardProgressDtos() {
  }

  public record SyncRequest(
      String sessionId,
      String mode,
      String ultimoStep,
      String status,
      String anuncioId) {
  }

  public record SyncResponse(
      UUID id,
      String status,
      String ultimoStep,
      OffsetDateTime atualizadoEm) {
  }
}
