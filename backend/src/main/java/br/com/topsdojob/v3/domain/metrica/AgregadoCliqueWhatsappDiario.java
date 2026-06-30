package br.com.topsdojob.v3.domain.metrica;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgregadoCliqueWhatsappDiario(
    UUID id,
    UUID anuncioId,
    LocalDate dataReferencia,
    String origemUf,
    String origemCidade,
    String origemUfChave,
    String origemCidadeChave,
    Long totalCliques,
    Long visitantesEstimados,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "agregado_clique_whatsapp_diario";
}
