package br.com.topsdojob.v3.application.operacional.outbox;

public class OutboxPermanentDeliveryException extends RuntimeException {
  public OutboxPermanentDeliveryException(String message) {
    super(message);
  }

  public OutboxPermanentDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
