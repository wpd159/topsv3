package br.com.topsdojob.v3.domain.suporte;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MensagemSuporte(
    UUID id,
    UUID ticketId,
    UUID autorUsuarioId,
    SuporteTipos.OrigemMensagem origem,
    String corpoResumido,
    Boolean privadoStaff,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "mensagem_suporte";
}
