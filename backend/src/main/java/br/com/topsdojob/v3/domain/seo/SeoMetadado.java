package br.com.topsdojob.v3.domain.seo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SeoMetadado(
    UUID id,
    UUID seoUrlId,
    String titulo,
    String descricao,
    SeoTipos.Robots robots,
    String ogTitulo,
    String ogDescricao,
    String schemaJson,
    SeoTipos.OrigemMetadado origem,
    UUID criadoPor,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "seo_metadado";
}
