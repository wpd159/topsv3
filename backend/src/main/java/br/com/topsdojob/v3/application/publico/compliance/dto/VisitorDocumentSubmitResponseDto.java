package br.com.topsdojob.v3.application.publico.compliance.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VisitorDocumentSubmitResponseDto(
    UUID submissionId,
    UUID challengeId,
    String state,
    String status,
    OffsetDateTime createdAt,
    String reasonPublic) {
}
