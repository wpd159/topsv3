package br.com.topsdojob.v3.application.publico.dto;

public record SolicitarAnuncioValidationErrorDto(
        String campo,
        String codigo,
        String mensagem) {
}
