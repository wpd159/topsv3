package br.com.topsdojob.v3.application.admin.anuncio.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAnuncioOperacaoJuridicaDto(
    UUID anuncioId,
    String statusAnuncio,
    UUID usuarioId,
    String statusUsuario,
    String acao,
    int anunciosPausados,
    int storiesSuspensos,
    boolean storyAdministrativoSuspenso,
    OffsetDateTime executadoEm) {
}
