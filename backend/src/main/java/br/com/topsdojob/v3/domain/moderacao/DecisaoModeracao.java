package br.com.topsdojob.v3.domain.moderacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DecisaoModeracao(
    UUID id,
    UUID revisaoAnuncioId,
    ModeracaoTipos.Decisao decisao,
    String motivo,
    UUID atorUsuarioId,
    String ipHash,
    String userAgentHash,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "decisao_moderacao";
}
