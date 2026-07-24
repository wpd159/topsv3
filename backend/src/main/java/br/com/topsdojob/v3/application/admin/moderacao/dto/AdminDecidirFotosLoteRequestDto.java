package br.com.topsdojob.v3.application.admin.moderacao.dto;

import java.util.List;

public record AdminDecidirFotosLoteRequestDto(
        List<AdminDecisaoFotoLoteItemRequestDto> fotos) {
}
