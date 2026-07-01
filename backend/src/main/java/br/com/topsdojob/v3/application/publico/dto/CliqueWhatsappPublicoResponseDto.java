package br.com.topsdojob.v3.application.publico.dto;

public record CliqueWhatsappPublicoResponseDto(
        boolean registrado,
        boolean disponivel,
        String whatsappUrl,
        String status,
        PoliticaContatoPublicoDto politica,
        String pendenciaStories) {
}
