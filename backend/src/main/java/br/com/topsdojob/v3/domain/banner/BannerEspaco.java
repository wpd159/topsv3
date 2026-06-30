package br.com.topsdojob.v3.domain.banner;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BannerEspaco(
    UUID id,
    String codigo,
    String nome,
    Integer larguraDesktop,
    Integer alturaDesktop,
    Integer larguraMobile,
    Integer alturaMobile,
    Boolean ativo,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "banner_espaco";
}
