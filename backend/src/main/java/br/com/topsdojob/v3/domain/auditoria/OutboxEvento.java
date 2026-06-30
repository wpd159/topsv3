package br.com.topsdojob.v3.domain.auditoria;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEvento(
    UUID id,
    String aggregateTipo,
    UUID aggregateId,
    String tipoEvento,
    String payloadJson,
    AuditoriaTipos.StatusOutbox status,
    String idempotencyKey,
    Integer tentativas,
    OffsetDateTime proximaTentativaEm,
    OffsetDateTime processadoEm,
    String erroResumido,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "outbox_evento";
}
