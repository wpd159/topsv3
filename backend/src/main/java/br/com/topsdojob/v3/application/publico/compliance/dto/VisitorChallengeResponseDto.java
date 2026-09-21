package br.com.topsdojob.v3.application.publico.compliance.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VisitorChallengeResponseDto(
    UUID challengeId,
    String state,
    String effectiveLevel,
    String scope,
    OffsetDateTime expiresAt,
    boolean requiresExplicitAcknowledgement,
    boolean documentRequired,
    int maxAttempts,
    String reasonPublic,
    String documentStatus) {
}
