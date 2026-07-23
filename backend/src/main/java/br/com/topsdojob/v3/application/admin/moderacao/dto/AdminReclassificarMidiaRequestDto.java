package br.com.topsdojob.v3.application.admin.moderacao.dto;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;

public record AdminReclassificarMidiaRequestDto(
        VisibilidadeMidia visibilidadeMidia,
        String motivo) {
}
