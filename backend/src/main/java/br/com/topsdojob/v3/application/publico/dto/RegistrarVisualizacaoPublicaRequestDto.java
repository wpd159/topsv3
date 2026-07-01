package br.com.topsdojob.v3.application.publico.dto;

public record RegistrarVisualizacaoPublicaRequestDto(
        String visitanteLocalId,
        String origemPais,
        String origemUf,
        String origemCidade,
        String dispositivo) {
}
