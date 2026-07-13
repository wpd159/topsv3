package br.com.topsdojob.v3.application.admin.documento.dto;

import java.util.UUID;

public record AdminKycDocumentoDto(
    UUID id,
    String parte,
    String status,
    String mimeType,
    long tamanhoBytes) {
}
