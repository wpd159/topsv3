package br.com.topsdojob.v3.application.publico.dto;

public record StoryPublicoDto(
        Integer ordem,
        String tipo,
        String finalidade,
        String visibilidadeMidia,
        String urlPublica,
        Integer largura,
        Integer altura,
        Integer duracaoMs,
        String mimeType,
        String pendenciaMidia) {
}
