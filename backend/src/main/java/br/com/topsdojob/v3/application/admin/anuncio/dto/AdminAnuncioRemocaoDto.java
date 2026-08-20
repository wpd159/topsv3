package br.com.topsdojob.v3.application.admin.anuncio.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAnuncioRemocaoDto(
    UUID anuncioId,
    String statusAnuncio,
    String statusModeracao,
    String acao,
    int midiasRemovidas,
    int objetosR2Excluidos,
    int objetosR2JaAusentes,
    int objetosCompartilhadosPreservados,
    int objetosCleanupAgendados,
    int storiesEncerrados,
    boolean storyAdministrativoEncerrado,
    OffsetDateTime removidoEm,
    OffsetDateTime executadoEm) {
}
