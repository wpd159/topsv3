package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBloqueioJuridicoDto(
    UUID id,
    UUID anuncioId,
    UUID usuarioId,
    String escopo,
    String categoria,
    String motivo,
    String observacaoInterna,
    UUID bloqueadoPorId,
    String bloqueadoPorNome,
    OffsetDateTime bloqueadoEm,
    boolean anuncioBloqueado,
    boolean usuarioBloqueado) {
}
