package br.com.topsdojob.v3.application.admin.compliance.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminComplianceRiscoDto(
    UUID id,
    String referenciaSessao,
    int score,
    String decisao,
    int falhasConsecutivas,
    int acessosRestritos,
    int acessosExplicitos,
    boolean revisaoSinalizada,
    OffsetDateTime bloqueadoAte,
    String motivoSanitizado,
    OffsetDateTime atualizadoEm) {
}
