package br.com.topsdojob.v3.application.admin.documento.dto;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;

public record AdminKycDecisaoRequestDto(
    AdminDecisaoModeracaoAcao decisao,
    String motivo) {
}
