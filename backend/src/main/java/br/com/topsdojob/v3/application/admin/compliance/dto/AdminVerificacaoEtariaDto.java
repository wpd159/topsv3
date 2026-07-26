package br.com.topsdojob.v3.application.admin.compliance.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminVerificacaoEtariaDto(
    UUID id,
    String resultado,
    String metodo,
    String requestId,
    OffsetDateTime criadoEm) {
}
