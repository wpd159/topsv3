package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.List;

public record MeuAnuncioMidiasResponseDto(
        List<MeuAnuncioMidiaGestaoDto> midias,
        MeuAnuncioMidiaLimitesDto limites,
        MeuAnuncioCicloVidaDto anuncio,
        long fotosValidasAtivasTotal) {
}
