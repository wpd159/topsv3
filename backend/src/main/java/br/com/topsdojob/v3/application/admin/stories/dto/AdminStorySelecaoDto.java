package br.com.topsdojob.v3.application.admin.stories.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminStorySelecaoDto(
        boolean ativa,
        UUID anuncioId,
        String anuncioSlug,
        String anuncioTitulo,
        long fotosAprovadas,
        long videosAprovados,
        OffsetDateTime ativadoEm,
        OffsetDateTime expiraEm,
        String classificacao,
        UUID ativadoPorId,
        String ativadoPorEmail) {
}
