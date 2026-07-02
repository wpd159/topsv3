package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminOutboxListaItemDto(
        UUID id,
        String tipoEvento,
        String entidadeTipo,
        UUID entidadeId,
        String status,
        OffsetDateTime criadoEm,
        Integer tentativas,
        OffsetDateTime proximaTentativaEm,
        String resumoSanitizado,
        AdminOutboxPreviewDto previa,
        boolean envioExternoExecutado) {
}
