package br.com.topsdojob.v3.application.publico.dto;

public record RegistrarVisualizacaoPublicaResponseDto(
        boolean registrado,
        String slug,
        String eventoId,
        String status,
        String pendenciaStories) {
}
