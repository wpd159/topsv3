package br.com.topsdojob.v3.application.publico.dto;

public record PoliticaStoryPublicoDto(
        boolean autorizado,
        String motivoPublico,
        String pendencia) {
}
