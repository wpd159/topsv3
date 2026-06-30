package br.com.topsdojob.v3.domain.metrica;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventoVerificacaoEtaria(
    UUID id,
    UUID usuarioId,
    UUID anuncioId,
    MetricaTipos.ResultadoVerificacaoEtaria resultado,
    MetricaTipos.MetodoVerificacaoEtaria metodo,
    String ipHash,
    String userAgentHash,
    String requestId,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "evento_verificacao_etaria";
}
