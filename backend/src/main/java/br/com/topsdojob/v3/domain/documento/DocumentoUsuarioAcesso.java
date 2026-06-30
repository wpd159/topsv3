package br.com.topsdojob.v3.domain.documento;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoUsuarioAcesso(
    UUID id,
    UUID documentoUsuarioId,
    UUID atorUsuarioId,
    DocumentoTipos.FinalidadeAcesso finalidade,
    DocumentoTipos.ResultadoAcesso resultado,
    String requestId,
    String ipHash,
    String userAgentHash,
    OffsetDateTime acessadoEm,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "documento_usuario_acesso";
}
