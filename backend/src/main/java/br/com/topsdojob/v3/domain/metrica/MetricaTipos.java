package br.com.topsdojob.v3.domain.metrica;

public final class MetricaTipos {
  private MetricaTipos() {
  }

  public enum Dispositivo {
    DESKTOP,
    MOBILE,
    TABLET,
    BOT,
    DESCONHECIDO
  }

  public enum ResultadoVerificacaoEtaria {
    PERMITIDO,
    NEGADO,
    INDETERMINADO
  }

  public enum MetodoVerificacaoEtaria {
    DECLARACAO,
    DOCUMENTO,
    STAFF,
    IMPORTACAO
  }
}
