package br.com.topsdojob.v3.application.admin.outbox.template;

import java.util.List;
import java.util.UUID;

public record AdminOutboxPreviewRenderizadaDto(
        UUID id,
        String tipoEvento,
        String status,
        String assuntoSanitizado,
        String corpoSanitizado,
        String canalPrevisto,
        boolean envioExternoExecutado,
        boolean somentePreview,
        List<String> camposMascarados,
        List<String> pendencias) {
}
