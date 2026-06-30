package br.com.topsdojob.v3.domain.localizacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Bairro(
    UUID id,
    UUID cidadeId,
    String nome,
    String nomeNormalizado,
    String slug,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "bairro";
}
