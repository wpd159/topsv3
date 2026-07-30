package br.com.topsdojob.v3.application.operacional.outbox;

public class OutboxRecipientNotAllowedException extends RuntimeException {
  public OutboxRecipientNotAllowedException() {
    super("destinatario nao autorizado para entrega externa");
  }
}
