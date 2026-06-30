package br.com.topsdojob.v3.domain.localizacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Estado(
    UUID id,
    String uf,
    String nome,
    String nomeNormalizado,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "estado";
}
