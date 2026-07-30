package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.util.List;

public record AdminUsuarioExclusaoElegibilidadeDto(
        boolean podeExcluir,
        List<String> bloqueios) {
}
