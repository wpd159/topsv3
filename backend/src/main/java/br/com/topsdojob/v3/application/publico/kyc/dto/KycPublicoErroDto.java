package br.com.topsdojob.v3.application.publico.kyc.dto;

public record KycPublicoErroDto(
    String codigo,
    String mensagem,
    String requestId) {
}
