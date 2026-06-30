package br.com.topsdojob.v3.domain.auditoria;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditoriaEvento(
    UUID id,
    UUID atorUsuarioId,
    String acao,
    String recursoTipo,
    UUID recursoId,
    String antesJson,
    String depoisJson,
    String antesHash,
    String depoisHash,
    String requestId,
    String ipHash,
    String userAgentHash,
    AuditoriaTipos.OrigemAuditoria origem,
    AuditoriaTipos.ResultadoAuditoria resultado,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "auditoria_evento";
}
