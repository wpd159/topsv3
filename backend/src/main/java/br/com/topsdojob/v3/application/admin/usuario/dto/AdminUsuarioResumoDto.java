package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUsuarioResumoDto(
        UUID id,
        String nome,
        String email,
        String telefone,
        String cpfMascarado,
        String status,
        String kycStatus,
        long totalAnuncios,
        boolean bloqueado,
        OffsetDateTime criadoEm) {
}
