package br.com.topsdojob.v3.application.admin.compliance.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminComplianceDocumentoDecisaoResponseDto(
    UUID id,
    String status,
    String requestId,
    OffsetDateTime decidedAt) {
}
