package br.com.topsdojob.v3.domain.auditoria;

public final class AuditoriaTipos {
  private AuditoriaTipos() {
  }

  public enum OrigemAuditoria {
    ADMIN,
    SISTEMA,
    IMPORTACAO,
    WEBHOOK,
    SUPORTE
  }

  public enum ResultadoAuditoria {
    SUCESSO,
    NEGADO,
    ERRO,
    PENDENTE
  }

  public enum StatusOutbox {
    PENDENTE,
    PROCESSANDO,
    PROCESSADO,
    ERRO,
    CANCELADO
  }
}
