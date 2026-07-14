package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminStatusSistemaDto(
        String app,
        String ambiente,
        boolean local,
        boolean efiPixEnabled,
        String politicaApi,
        String pendenciaCsrf) {
}
