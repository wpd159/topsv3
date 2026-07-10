package br.com.topsdojob.v3.domain.midia;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AnuncioMidia(
    UUID id,
    UUID anuncioId,
    UUID arquivoMidiaId,
    MidiaTipos.TipoAnuncioMidia tipo,
    MidiaTipos.FinalidadeAnuncioMidia finalidade,
    Integer ordem,
    MidiaTipos.StatusAnuncioMidia status,
    VisibilidadeMidia visibilidadeMidia,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "anuncio_midia";
}
