package br.com.topsdojob.v3.application.admin.documento.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminKycDecisaoResponseDto(
    UUID envioId,
    String status,
    String requestId,
    OffsetDateTime revisadoEm) {
}
