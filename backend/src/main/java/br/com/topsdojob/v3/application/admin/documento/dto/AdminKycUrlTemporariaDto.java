package br.com.topsdojob.v3.application.admin.documento.dto;

import java.time.OffsetDateTime;

public record AdminKycUrlTemporariaDto(
    String url,
    OffsetDateTime expiraEm) {
}
