package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRevisaoAbertaDto(
        UUID id,
        String tipo,
        String status,
        OffsetDateTime criadoEm) {
}
