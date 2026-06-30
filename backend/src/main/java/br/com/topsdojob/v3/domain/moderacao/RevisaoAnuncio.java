package br.com.topsdojob.v3.domain.moderacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RevisaoAnuncio(
    UUID id,
    UUID anuncioId,
    ModeracaoTipos.TipoRevisao tipo,
    ModeracaoTipos.StatusRevisao status,
    String payloadSolicitado,
    UUID criadoPor,
    OffsetDateTime criadoEm,
    OffsetDateTime finalizadoEm) {
  public static final String TABELA = "revisao_anuncio";
}
