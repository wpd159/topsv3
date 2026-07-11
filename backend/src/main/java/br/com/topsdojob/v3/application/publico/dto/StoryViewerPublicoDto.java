package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;

public record StoryViewerPublicoDto(
        String storyId,
        String anuncioId,
        String anuncioSlug,
        String usuarioUsername,
        String displayUsername,
        boolean profileNavigable,
        String viewerState,
        String midiaUrl,
        String tipo,
        OffsetDateTime expiraEm,
        String blockedReason) {
}
