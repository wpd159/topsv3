package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.util.List;

public record AdminVisaoGeralDto(
        List<AdminContadorDto> contadores,
        AdminResumoAnunciosDto anuncios,
        AdminResumoModeracaoDto moderacao,
        AdminResumoMidiasDto midias,
        AdminResumoMetricasDto metricas,
        AdminStatusSistemaDto sistema) {
}
