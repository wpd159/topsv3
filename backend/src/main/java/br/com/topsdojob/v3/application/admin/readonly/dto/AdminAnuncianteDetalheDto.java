package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.util.UUID;

public record AdminAnuncianteDetalheDto(
        UUID id,
        String nome,
        String nomeCivil,
        String email,
        String cpf,
        String whatsapp,
        String status) {
}
