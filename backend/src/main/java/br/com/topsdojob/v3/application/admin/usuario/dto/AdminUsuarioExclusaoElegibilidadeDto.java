package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.util.List;

public record AdminUsuarioExclusaoElegibilidadeDto(
        boolean podeExcluir,
        String tipoExclusao,
        boolean anonimizado,
        long vinculosPreservados,
        List<String> consequencias,
        List<String> bloqueios) {
}
