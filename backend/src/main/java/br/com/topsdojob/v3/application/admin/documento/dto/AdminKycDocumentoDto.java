package br.com.topsdojob.v3.application.admin.documento.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminKycDocumentoDto(
    UUID id,
    String tipo,
    String parte,
    String status,
    String mimeType,
    long tamanhoBytes,
    OffsetDateTime criadoEm) {
}
