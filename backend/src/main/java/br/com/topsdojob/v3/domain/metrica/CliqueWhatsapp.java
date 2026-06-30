package br.com.topsdojob.v3.domain.metrica;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CliqueWhatsapp(
    UUID id,
    UUID anuncioId,
    String visitanteHash,
    String ipHash,
    String userAgentHash,
    String origemPais,
    String origemUf,
    String origemCidade,
    MetricaTipos.Dispositivo dispositivo,
    Boolean permitido,
    String motivoBloqueio,
    String requestId,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "clique_whatsapp";
}
