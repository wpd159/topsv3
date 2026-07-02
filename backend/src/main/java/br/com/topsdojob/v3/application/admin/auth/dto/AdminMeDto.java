package br.com.topsdojob.v3.application.admin.auth.dto;

import java.util.List;
import java.util.UUID;

public record AdminMeDto(
        boolean autenticado,
        UUID usuarioId,
        String nome,
        String email,
        List<String> papeis,
        List<String> permissoes) {
}
