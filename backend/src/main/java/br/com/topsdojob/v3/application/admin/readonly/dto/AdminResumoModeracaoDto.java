package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminResumoModeracaoDto(
        long revisoesAbertas,
        long revisoesEmAnalise,
        long anunciosPendentesModeracao,
        long anunciosRejeitados,
        long documentosPendentes) {
}
