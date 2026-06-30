package br.com.topsdojob.v3.domain.importacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportacaoPendencia(
    UUID id,
    UUID execucaoId,
    String codigo,
    ImportacaoDominioTipos.SeveridadePendencia severidade,
    ImportacaoDominioTipos.StatusPendencia status,
    String entidadeTipo,
    String idOrigem,
    String detalheResumido,
    OffsetDateTime criadoEm,
    OffsetDateTime resolvidoEm) {
  public static final String TABELA = "importacao_pendencia";
}
