package br.com.topsdojob.v3.domain.banner;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Banner(
    UUID id,
    UUID bannerEspacoId,
    String titulo,
    String subtitulo,
    String textoBotao,
    String urlDestino,
    String altTextDesktop,
    String altTextMobile,
    UUID arquivoDesktopId,
    UUID arquivoMobileId,
    BannerTipos.Status status,
    OffsetDateTime inicioEm,
    OffsetDateTime fimEm,
    Integer ordem,
    Integer versao,
    UUID criadoPor,
    UUID atualizadoPor,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "banner";
}
