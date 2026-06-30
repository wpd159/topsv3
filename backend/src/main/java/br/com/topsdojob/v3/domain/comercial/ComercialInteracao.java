package br.com.topsdojob.v3.domain.comercial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ComercialInteracao(
    UUID id,
    UUID contatoId,
    ComercialTipos.TipoInteracao tipo,
    String resultado,
    UUID responsavelUsuarioId,
    String resumo,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "comercial_interacao";
}
