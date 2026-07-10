package br.com.topsdojob.v3.application.admin.moderacao.dto;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;

public record AdminDecidirMidiaRequestDto(
        AdminDecisaoModeracaoAcao decisao,
        VisibilidadeMidia visibilidadeMidia,
        String motivo,
        String observacao,
        String requestIdCliente) {
}
