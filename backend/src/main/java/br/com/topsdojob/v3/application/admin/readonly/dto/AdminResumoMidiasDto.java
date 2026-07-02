package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminResumoMidiasDto(
        long arquivosTotal,
        long arquivosPendentes,
        long arquivosValidados,
        long midiasPublicaveis,
        long midiasPendentes,
        long midiasBloqueadas,
        long storiesPublicados,
        long storiesPendentes) {
}
