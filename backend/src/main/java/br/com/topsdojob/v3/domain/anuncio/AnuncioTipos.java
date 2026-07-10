package br.com.topsdojob.v3.domain.anuncio;

public final class AnuncioTipos {
  private AnuncioTipos() {
  }

  public enum Status {
    RASCUNHO,
    PENDENTE_REVISAO,
    APROVADO,
    PUBLICADO,
    PAUSADO,
    REJEITADO,
    REMOVIDO
  }

  public enum StatusModeracao {
    NAO_ENVIADO,
    PENDENTE,
    APROVADO,
    REJEITADO
  }

  public enum StatusPublicacaoBusca {
    NAO_PUBLICAVEL,
    PUBLICAVEL,
    NOINDEX,
    REMOVIDO
  }
}
