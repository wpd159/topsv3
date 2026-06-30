package br.com.topsdojob.v3.domain.suporte;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketSuporte(
    UUID id,
    UUID usuarioId,
    UUID anuncioId,
    UUID pagamentoId,
    UUID revisaoAnuncioId,
    String assunto,
    SuporteTipos.StatusTicket status,
    SuporteTipos.PrioridadeTicket prioridade,
    UUID responsavelUsuarioId,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    OffsetDateTime encerradoEm,
    Integer versao) {
  public static final String TABELA = "ticket_suporte";
}
