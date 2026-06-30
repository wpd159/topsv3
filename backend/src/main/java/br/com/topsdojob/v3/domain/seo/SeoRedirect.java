package br.com.topsdojob.v3.domain.seo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SeoRedirect(
    UUID id,
    String origemCaminho,
    String destinoCaminho,
    Integer statusCode,
    Boolean ativo,
    String motivo,
    UUID criadoPor,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "seo_redirect";
}
