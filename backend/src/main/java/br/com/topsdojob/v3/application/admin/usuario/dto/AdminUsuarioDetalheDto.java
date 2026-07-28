package br.com.topsdojob.v3.application.admin.usuario.dto;

import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUsuarioDetalheDto(
        UUID id,
        String nome,
        String nomeCivil,
        String email,
        String telefone,
        String cpf,
        boolean cpfMascarado,
        LocalDate dataNascimento,
        String status,
        String tipoConta,
        String kycStatus,
        boolean bloqueado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        UUID anuncioAncoraBloqueioId,
        boolean podeBloquear,
        boolean podeDesbloquear,
        Integer versao,
        List<AdminUsuarioAnuncioDto> anuncios,
        List<AdminKycEnvioDto> kycEnvios,
        List<AdminUsuarioHistoricoDto> historico) {
}
