package br.com.topsdojob.v3.application.admin.creditos.dto;

public record AdminCreditoAjusteRequest(
        String direcao,
        Integer quantidade,
        String motivo) {
}
