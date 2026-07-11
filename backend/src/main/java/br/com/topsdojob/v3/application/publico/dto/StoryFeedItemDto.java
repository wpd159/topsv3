package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;

public record StoryFeedItemDto(
        String storyId,
        String anuncioId,
        String anuncioSlug,
        String usuarioUsername,
        String displayUsername,
        boolean profileNavigable,
        String previewState,
        String previewUrl,
        String tipo,
        OffsetDateTime expiraEm) {
}
