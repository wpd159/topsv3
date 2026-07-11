package br.com.topsdojob.v3.application.publico.auth.dto;

import java.util.UUID;

public record PublicUserDto(
        boolean autenticado,
        UUID id,
        String username,
        String nomeCompleto,
        String email,
        String telefone,
        String status,
        String cargo) {
}
