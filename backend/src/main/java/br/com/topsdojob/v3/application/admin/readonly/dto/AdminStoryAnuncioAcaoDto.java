package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminStoryAnuncioAcaoDto(
    String estado,
    UUID storyId,
    OffsetDateTime expiraEm,
    String motivo) {
}
