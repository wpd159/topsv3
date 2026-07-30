package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.util.List;

public record AdminUsuarioExclusaoErroDto(
        String codigo,
        String mensagem,
        List<String> bloqueios,
        String requestId) {
}
