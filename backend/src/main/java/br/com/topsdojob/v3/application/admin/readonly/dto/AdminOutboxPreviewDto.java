package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminOutboxPreviewDto(
        String canalLogico,
        String destinoLogicoSanitizado,
        String assuntoSanitizado,
        String corpoSanitizado,
        boolean envioExternoExecutado) {
}
