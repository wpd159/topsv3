package br.com.topsdojob.v3.application.publico.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

public record StoryFeedItemDto(
        String storyId,
        String anuncioId,
        String anuncioSlug,
        String usuarioUsername,
        String displayUsername,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer idade,
        boolean profileNavigable,
        String previewState,
        String previewUrl,
        String modoConteudo,
        String tipo,
        OffsetDateTime expiraEm) {

    public StoryFeedItemDto(
            String storyId,
            String anuncioId,
            String anuncioSlug,
            String usuarioUsername,
            String displayUsername,
            Integer idade,
            boolean profileNavigable,
            String previewState,
            String previewUrl,
            String tipo,
            OffsetDateTime expiraEm) {
        this(
                storyId, anuncioId, anuncioSlug, usuarioUsername, displayUsername,
                idade, profileNavigable, previewState, previewUrl, null, tipo, expiraEm);
    }
}
