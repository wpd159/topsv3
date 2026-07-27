package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUsuarioAnuncioDto(
        UUID id,
        String slug,
        String titulo,
        String status,
        String statusModeracao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {
}
