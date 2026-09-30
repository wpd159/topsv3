package br.com.topsdojob.v3.application.admin.moderacao.dto;

import java.util.UUID;

public record AdminDecidirRevisaoRequestDto(
        AdminDecisaoModeracaoAcao decisao,
        String motivo,
        String observacao,
        String requestIdCliente,
        UUID operacaoIdCliente,
        Integer versaoAnuncioEsperada) {

    public AdminDecidirRevisaoRequestDto(
            AdminDecisaoModeracaoAcao decisao,
            String motivo,
            String observacao,
            String requestIdCliente) {
        this(decisao, motivo, observacao, requestIdCliente, null, null);
    }
}
