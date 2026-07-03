package br.com.topsdojob.v3.application.admin.outbox.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminOutboxSimularProcessamentoResponseDto(
        UUID id,
        String statusAntes,
        String statusDepois,
        boolean statusAlterado,
        boolean envioExternoExecutado,
        boolean auditoriaRegistrada,
        String requestId,
        OffsetDateTime processadoEm,
        String mensagem) {
}
