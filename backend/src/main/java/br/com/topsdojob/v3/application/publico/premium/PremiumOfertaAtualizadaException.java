package br.com.topsdojob.v3.application.publico.premium;

public final class PremiumOfertaAtualizadaException extends RuntimeException {

  public PremiumOfertaAtualizadaException() {
    super("As condicoes desta opcao foram atualizadas. Confira o novo valor antes de continuar.");
  }
}
