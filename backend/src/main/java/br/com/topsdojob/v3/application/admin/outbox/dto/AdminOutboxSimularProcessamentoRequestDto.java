package br.com.topsdojob.v3.application.admin.outbox.dto;

public record AdminOutboxSimularProcessamentoRequestDto(
        String observacao,
        String requestIdCliente) {
}
