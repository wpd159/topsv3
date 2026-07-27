package br.com.topsdojob.v3.application.publico.compliance.dto;

import java.time.OffsetDateTime;

public record VisitorAccessStatusDto(
    boolean globalAccepted,
    boolean verified,
    String level,
    OffsetDateTime expiresAt,
    boolean explicitVerified,
    String explicitLevel,
    OffsetDateTime explicitExpiresAt,
    String state,
    Integer riskScore,
    String decision,
    String documentStatus,
    String reasonPublic) {
}
