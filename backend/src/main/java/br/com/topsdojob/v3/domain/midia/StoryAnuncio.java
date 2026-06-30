package br.com.topsdojob.v3.domain.midia;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoryAnuncio(
    UUID id,
    UUID anuncioMidiaId,
    MidiaTipos.StatusStory status,
    OffsetDateTime inicioEm,
    OffsetDateTime fimEm,
    Integer ordem,
    UUID criadoPor,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "story_anuncio";
}
