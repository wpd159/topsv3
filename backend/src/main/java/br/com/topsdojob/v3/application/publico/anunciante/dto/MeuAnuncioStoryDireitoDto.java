package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeuAnuncioStoryDireitoDto(
    UUID ativacaoId,
    String status,
    Integer custoCreditosSnapshot,
    OffsetDateTime inicioEm,
    OffsetDateTime fimEm) {
}
