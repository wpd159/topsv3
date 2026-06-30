package br.com.topsdojob.v3.domain.banner;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BannerVersao(
    UUID id,
    UUID bannerId,
    String snapshotJson,
    UUID criadoPor,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "banner_versao";
}
