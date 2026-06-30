package br.com.topsdojob.v3.domain.anuncio;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnuncioStatusHistorico(
    UUID id,
    UUID anuncioId,
    AnuncioTipos.Status statusAnterior,
    AnuncioTipos.Status statusNovo,
    String motivo,
    UUID atorUsuarioId,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "anuncio_status_historico";
}
