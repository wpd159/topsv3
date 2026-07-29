package br.com.topsdojob.v3.application.operacional.outbox;

import java.util.UUID;

public record OutboxEmailMessage(
    UUID outboxId,
    String idempotencyKey,
    String recipient,
    String subject,
    String textBody,
    String htmlBody) {
}
