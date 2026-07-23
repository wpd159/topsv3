package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.util.UUID;

public record AdminAnuncianteResumoDto(
        UUID id,
        String nome,
        String nomeCivil,
        String email,
        String whatsapp,
        String status) {
}
