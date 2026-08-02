package br.com.topsdojob.v3.application.publico.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

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
        String modoConteudo,
        String tipo,
        OffsetDateTime expiraEm,
        String blockedReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String cidade,
        @JsonInclude(JsonInclude.Include.NON_NULL) String uf,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal preco,
        @JsonInclude(JsonInclude.Include.NON_NULL) String resumo) {

    public StoryViewerPublicoDto(
            String storyId,
            String anuncioId,
            String anuncioSlug,
            String usuarioUsername,
            String displayUsername,
            Integer idade,
            boolean profileNavigable,
            String viewerState,
            String midiaUrl,
            String tipo,
            OffsetDateTime expiraEm,
            String blockedReason) {
        this(
                storyId, anuncioId, anuncioSlug, usuarioUsername, displayUsername,
                idade, profileNavigable, viewerState, midiaUrl, null, tipo, expiraEm,
                blockedReason, null, null, null, null);
    }
}
