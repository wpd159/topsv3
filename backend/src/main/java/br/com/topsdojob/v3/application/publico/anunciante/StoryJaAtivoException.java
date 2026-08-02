package br.com.topsdojob.v3.application.publico.anunciante;

public final class StoryJaAtivoException extends RuntimeException {

  public StoryJaAtivoException() {
    super("Este anuncio ja possui um Story ativo.");
  }
}
