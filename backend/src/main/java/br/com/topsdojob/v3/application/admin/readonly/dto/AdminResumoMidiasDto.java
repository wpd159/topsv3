package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminResumoMidiasDto(
        long arquivosTotal,
        long arquivosPendentes,
        long arquivosValidados,
        long midiasPublicaveis,
        long midiasPendentes,
        long midiasRestritas18,
        long storiesPublicados,
        long storiesPendentes) {
}
