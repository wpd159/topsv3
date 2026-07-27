package br.com.topsdojob.v3.application.admin.compliance.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminComplianceDocumentoDto(
    UUID id,
    UUID challengeId,
    UUID anuncioId,
    String status,
    String mimeType,
    long tamanhoBytes,
    String motivoPublico,
    OffsetDateTime criadoEm,
    OffsetDateTime revisadoEm) {
}
