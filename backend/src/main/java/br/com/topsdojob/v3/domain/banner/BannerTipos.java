package br.com.topsdojob.v3.domain.banner;

public final class BannerTipos {
  private BannerTipos() {
  }

  public enum Status {
    RASCUNHO,
    AGENDADO,
    PUBLICADO,
    INATIVO,
    ENCERRADO
  }
}
