package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;

public record MeuAnuncioCapaDto(
        String urlPublica,
        boolean restrita,
        String previewUrl,
        OffsetDateTime previewExpiraEm) {

    public MeuAnuncioCapaDto(String urlPublica, boolean restrita) {
        this(urlPublica, restrita, null, null);
    }
}
