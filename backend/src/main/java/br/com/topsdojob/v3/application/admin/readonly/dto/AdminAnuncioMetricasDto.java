package br.com.topsdojob.v3.application.admin.readonly.dto;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import java.math.BigDecimal;
import java.util.List;

public record AdminAnuncioMetricasDto(
        VisualizacoesCanonicasDto visualizacoes,
        long cliquesWhatsapp,
        BigDecimal ctr,
        List<String> beneficiosPremiumVigentes,
        AdminModeracaoHistoricoItemDto ultimaAcaoAdministrativa) {
}
