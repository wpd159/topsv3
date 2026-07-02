package br.com.topsdojob.v3.application.publico.dto;

public record MidiaPublicaDto(
        String tipo,
        String finalidade,
        Integer ordem,
        String urlPublica,
        String pendenciaMidia,
        Integer largura,
        Integer altura,
        String mimeType) {
}
