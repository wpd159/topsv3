package br.com.topsdojob.v3.domain.anuncio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoBuscaAnuncio(
    UUID anuncioId,
    String textoBusca,
    UUID estadoId,
    UUID cidadeId,
    UUID bairroId,
    String categoria,
    BigDecimal preco,
    AnuncioTipos.StatusPublicacaoBusca statusPublicacao,
    Boolean temMidiaValida,
    String beneficiosRankingJson,
    BigDecimal rankingBase,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "documento_busca_anuncio";
}
