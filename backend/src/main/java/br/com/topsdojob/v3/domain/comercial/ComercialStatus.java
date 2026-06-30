package br.com.topsdojob.v3.domain.comercial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ComercialStatus(
    UUID id,
    String codigo,
    String nome,
    Integer ordem,
    Boolean ativo,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "comercial_status";
}
