package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.util.UUID;

public record AdminCreditoUsuarioDto(
        UUID id,
        String nome,
        String email,
        int saldo) {
}
