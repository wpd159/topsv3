package br.com.topsdojob.v3.application.publico.kyc.dto;

import java.util.UUID;

public record KycDocumentoDto(
    UUID id,
    String parte,
    String status,
    String mimeType,
    long tamanhoBytes) {
}
