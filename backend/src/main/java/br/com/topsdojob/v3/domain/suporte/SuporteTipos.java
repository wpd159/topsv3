package br.com.topsdojob.v3.domain.suporte;

public final class SuporteTipos {
  private SuporteTipos() {
  }

  public enum StatusTicket {
    ABERTO,
    EM_ATENDIMENTO,
    AGUARDANDO_USUARIO,
    RESOLVIDO,
    ENCERRADO
  }

  public enum PrioridadeTicket {
    BAIXA,
    MEDIA,
    ALTA,
    CRITICA
  }

  public enum OrigemMensagem {
    USUARIO,
    STAFF,
    SISTEMA,
    IMPORTACAO
  }
}
