package br.com.topsdojob.v3.application.publico.compliance.dto;

import java.util.UUID;

public record VisitorVerifyRequestDto(
    UUID challengeId,
    String dataNascimento,
    String confirmacaoDataNascimento,
    String cpf,
    Boolean aceiteMaioridade,
    Boolean aceiteConteudoRestrito,
    Boolean aceitePrivacidade,
    Boolean confirmacaoExplicita,
    String idempotencyKey) {
}
