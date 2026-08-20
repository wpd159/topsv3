package br.com.topsdojob.v3.application.publico.dto;

public record IndexacaoLocalidadePublicaDto(
        boolean indexavel,
        String motivo,
        long anunciosElegiveisUnicos,
        int minimoNecessario,
        boolean canonica) {
}
