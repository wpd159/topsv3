package br.com.topsdojob.v3.domain.credito;

public final class CreditoTipos {
  private CreditoTipos() {
  }

  public enum TipoMovimento {
    ENTRADA,
    SAIDA,
    AJUSTE,
    ESTORNO
  }

  public enum Direcao {
    CREDITO,
    DEBITO
  }

  public enum OrigemMovimento {
    PAGAMENTO,
    BENEFICIO,
    AJUSTE_ADMIN,
    ESTORNO,
    IMPORTACAO,
    CAMPANHA
  }
}
