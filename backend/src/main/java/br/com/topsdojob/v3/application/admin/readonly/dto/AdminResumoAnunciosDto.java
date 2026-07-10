package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.util.List;

public record AdminResumoAnunciosDto(
        long totalAtivos,
        long publicados,
        long pendentesRevisao,
        long pausados,
        long comContatoConfigurado,
        List<AdminContadorDto> porStatus) {
}
