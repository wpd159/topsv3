package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUsuarioHistoricoDto(
        UUID id,
        String acao,
        String resultado,
        String requestId,
        OffsetDateTime criadoEm) {
}
