package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.util.UUID;

public record AdminUsuarioExclusaoResultadoDto(
        UUID id,
        boolean excluido) {
}
