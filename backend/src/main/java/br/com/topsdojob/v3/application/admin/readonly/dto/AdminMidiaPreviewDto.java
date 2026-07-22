package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;

public record AdminMidiaPreviewDto(
        String url,
        OffsetDateTime expiraEm,
        boolean publica,
        String mimeType) {
}
