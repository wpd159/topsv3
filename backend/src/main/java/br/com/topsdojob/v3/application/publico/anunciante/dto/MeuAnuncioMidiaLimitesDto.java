package br.com.topsdojob.v3.application.publico.anunciante.dto;

public record MeuAnuncioMidiaLimitesDto(
        int maxFotos,
        int fotosAtivas,
        int fotosDisponiveis,
        int maxVideos,
        int videosAtivos,
        int videosDisponiveis,
        boolean fotosExtrasAtivo,
        long maxFotoBytes,
        long maxVideoBytes) {
}
