package br.com.topsdojob.v3.application.publico.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

public record StoryViewerPublicoDto(
        String storyId,
        String anuncioId,
        String anuncioSlug,
        String usuarioUsername,
        String displayUsername,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer idade,
        boolean profileNavigable,
        String viewerState,
        String midiaUrl,
        String tipo,
        OffsetDateTime expiraEm,
        String blockedReason) {
}
