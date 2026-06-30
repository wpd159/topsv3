package br.com.topsdojob.v3.domain.midia;

public final class MidiaTipos {
  private MidiaTipos() {
  }

  public enum StatusArquivo {
    PENDENTE,
    VALIDADO,
    REJEITADO,
    REMOVIDO
  }

  public enum TipoAnuncioMidia {
    FOTO,
    VIDEO,
    STORY
  }

  public enum FinalidadeAnuncioMidia {
    CAPA,
    GALERIA,
    STORY
  }

  public enum StatusAnuncioMidia {
    PENDENTE,
    PUBLICAVEL,
    REJEITADA,
    REMOVIDA
  }

  public enum StatusStory {
    RASCUNHO,
    PENDENTE,
    PUBLICADO,
    EXPIRADO,
    REMOVIDO
  }
}
