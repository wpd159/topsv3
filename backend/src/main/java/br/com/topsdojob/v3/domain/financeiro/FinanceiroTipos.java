package br.com.topsdojob.v3.domain.financeiro;

public final class FinanceiroTipos {
  private FinanceiroTipos() {
  }

  public enum ProvedorPagamento {
    EFI,
    MERCADO_PAGO_LEGADO,
    OUTRO_LEGADO,
    DESCONHECIDO
  }

  public enum MetodoPagamento {
    PIX,
    LEGADO,
    DESCONHECIDO
  }

  public enum AmbientePagamento {
    SANDBOX,
    PRODUCAO
  }

  public enum StatusInternoPagamento {
    CRIADO,
    AGUARDANDO_PAGAMENTO,
    APROVADO,
    CANCELADO,
    EXPIRADO,
    ESTORNADO,
    ERRO,
    LEGADO
  }

  public enum ValidacaoWebhook {
    PENDENTE,
    VALIDO,
    INVALIDO,
    IGNORADO
  }

  public enum OrigemConciliacao {
    WEBHOOK,
    CONSULTA_PROVEDOR,
    IMPORTACAO,
    AJUSTE_ADMIN
  }

  public enum StatusConciliacao {
    PENDENTE,
    CONCILIADO,
    DIVERGENTE,
    CANCELADO
  }
}
