package br.com.topsdojob.v3.application.admin.moderacao.dto;

public record AdminRemeterRevisaoRequestDto(
        String motivo,
        String observacao,
        String requestIdCliente) {
}
