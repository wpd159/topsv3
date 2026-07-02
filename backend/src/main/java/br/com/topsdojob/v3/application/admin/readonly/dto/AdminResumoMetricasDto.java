package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminResumoMetricasDto(
        long visualizacoesTotal,
        long cliquesWhatsappTotal,
        long cliquesWhatsappPermitidos,
        long cliquesWhatsappBloqueados) {
}
