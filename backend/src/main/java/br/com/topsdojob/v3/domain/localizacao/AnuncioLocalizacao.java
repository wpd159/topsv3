package br.com.topsdojob.v3.domain.localizacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AnuncioLocalizacao(
    UUID anuncioId,
    UUID estadoId,
    UUID cidadeId,
    UUID bairroId,
    String enderecoResumido,
    BigDecimal latitude,
    BigDecimal longitude,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "anuncio_localizacao";
}
