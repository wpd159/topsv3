package br.com.topsdojob.v3.domain.moderacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnuncioMidiaRevisao(
    UUID id,
    UUID revisaoAnuncioId,
    UUID anuncioMidiaId,
    UUID arquivoMidiaId,
    ModeracaoTipos.AcaoMidiaRevisao acao,
    ModeracaoTipos.StatusMidiaRevisao status,
    Integer ordem,
    String motivo,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "anuncio_midia_revisao";
}
