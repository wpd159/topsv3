package br.com.topsdojob.v3.application.admin.moderacao.dto;

public record AdminDecidirRevisaoRequestDto(
        AdminDecisaoModeracaoAcao decisao,
        String motivo,
        String observacao,
        String requestIdCliente) {
}
