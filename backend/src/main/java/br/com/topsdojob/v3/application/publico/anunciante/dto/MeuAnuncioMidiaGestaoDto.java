package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeuAnuncioMidiaGestaoDto(
        UUID id,
        String tipo,
        Integer ordem,
        String status,
        String visibilidadeMidia,
        String previewUrl,
        boolean restrita,
        boolean ocultaPorLimite,
        OffsetDateTime previewExpiraEm) {
}
