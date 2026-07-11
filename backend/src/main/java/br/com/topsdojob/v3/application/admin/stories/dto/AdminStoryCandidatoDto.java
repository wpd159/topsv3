package br.com.topsdojob.v3.application.admin.stories.dto;

import java.util.UUID;

public record AdminStoryCandidatoDto(
        UUID anuncioId,
        String slug,
        String titulo,
        long fotosAprovadas,
        long videosAprovados,
        boolean selecionado) {
}
