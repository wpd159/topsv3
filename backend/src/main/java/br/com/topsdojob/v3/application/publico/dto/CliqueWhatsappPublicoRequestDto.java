package br.com.topsdojob.v3.application.publico.dto;

public record CliqueWhatsappPublicoRequestDto(
        String visitanteLocalId,
        String origemPais,
        String origemUf,
        String origemCidade,
        String dispositivo) {
}
