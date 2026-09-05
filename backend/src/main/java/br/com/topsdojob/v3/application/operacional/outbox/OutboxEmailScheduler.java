package br.com.topsdojob.v3.application.operacional.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxEmailScheduler {
  private final OutboxEmailDispatchService dispatchService;
  private final OutboxEmailProperties properties;

  public OutboxEmailScheduler(
      OutboxEmailDispatchService dispatchService,
      OutboxEmailProperties properties) {
    this.dispatchService = dispatchService;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.outbox.email.poll-delay-ms:5000}")
  public void process() {
    if (!properties.enabled()) {
      return;
    }
    for (int index = 0; index < properties.batchSize(); index++) {
      if (!dispatchService.processNext()) {
        return;
      }
    }
  }
}
