package br.com.topsdojob.v3.application.publico.compliance.dto;

import java.time.OffsetDateTime;

public record AgeGateGlobalStatusDto(
    boolean accepted,
    String state,
    OffsetDateTime expiresAt) {
}
