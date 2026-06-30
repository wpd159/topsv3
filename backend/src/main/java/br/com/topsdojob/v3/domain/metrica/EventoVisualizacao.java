package br.com.topsdojob.v3.domain.metrica;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventoVisualizacao(
    UUID id,
    UUID anuncioId,
    String visitanteHash,
    String ipHash,
    String userAgentHash,
    String refererHash,
    String origemPais,
    String origemUf,
    String origemCidade,
    MetricaTipos.Dispositivo dispositivo,
    String requestId,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "evento_visualizacao";
}
