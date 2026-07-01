package br.com.topsdojob.v3.application.publico.dto;

import java.util.List;

public record ListaStoriesPublicosDto(
        String slug,
        boolean idadeConfirmada,
        boolean autorizado,
        List<StoryPublicoDto> stories,
        PoliticaStoryPublicoDto politica) {
}
