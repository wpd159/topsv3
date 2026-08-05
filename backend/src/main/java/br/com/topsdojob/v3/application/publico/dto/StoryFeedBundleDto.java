package br.com.topsdojob.v3.application.publico.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record StoryFeedBundleDto(
        String bundleKey,
        String usuarioUsername,
        String displayUsername,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer idade,
        boolean profileNavigable,
        String avatarUrl,
        boolean visto,
        List<StoryFeedItemDto> itens) {
}
