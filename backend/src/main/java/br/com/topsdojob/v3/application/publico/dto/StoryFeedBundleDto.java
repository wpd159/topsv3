package br.com.topsdojob.v3.application.publico.dto;

import java.util.List;

public record StoryFeedBundleDto(
        String usuarioId,
        String usuarioUsername,
        String displayUsername,
        boolean profileNavigable,
        String avatarUrl,
        boolean visto,
        List<StoryFeedItemDto> itens) {
}
