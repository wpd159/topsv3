package br.com.topsdojob.v3.application.publico.compliance.dto;

import java.util.UUID;

public record VisitorChallengeRequestDto(
    String level,
    String scope,
    UUID anuncioId,
    UUID midiaId,
    String storyId,
    String route,
    String idempotencyKey) {
}
