package br.com.topsdojob.v3.domain.comercial;

public final class ComercialTipos {
  private ComercialTipos() {
  }

  public enum OrigemContato {
    ORGANICO,
    INDICACAO,
    CAMPANHA,
    ADMIN,
    IMPORTACAO,
    OUTRO
  }

  public enum TipoInteracao {
    NOTA,
    LIGACAO,
    MENSAGEM,
    EMAIL,
    REUNIAO,
    CORTESIA,
    OUTRO
  }
}
