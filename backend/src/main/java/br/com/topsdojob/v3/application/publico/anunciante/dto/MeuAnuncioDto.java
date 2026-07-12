package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeuAnuncioDto(
        UUID id,
        String slug,
        String titulo,
        String status,
        String statusModeracao,
        MeuAnuncioLocalizacaoDto localizacao,
        MeuAnuncioCapaDto capa,
        OffsetDateTime atualizadoEm) {
}
