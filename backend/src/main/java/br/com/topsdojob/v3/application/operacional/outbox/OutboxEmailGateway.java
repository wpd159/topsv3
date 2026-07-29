package br.com.topsdojob.v3.application.operacional.outbox;

public interface OutboxEmailGateway {
  void send(OutboxEmailMessage message);
}
