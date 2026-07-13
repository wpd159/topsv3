package br.com.topsdojob.v3.application.admin.documento.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminKycEnvioDto(
    UUID envioId,
    UUID usuarioId,
    String nomeCivil,
    String cpfMascarado,
    String dataNascimento,
    String status,
    String motivo,
    OffsetDateTime enviadoEm,
    OffsetDateTime revisadoEm,
    List<AdminKycDocumentoDto> documentos) {
}
