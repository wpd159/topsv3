package br.com.topsdojob.v3.application.admin.moderacao.dto;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import java.util.UUID;

public record AdminDecisaoFotoLoteItemRequestDto(
        UUID mediaId,
        AdminDecisaoFotoLoteAcao decisao,
        VisibilidadeMidia classificacao,
        String observacao) {
}
