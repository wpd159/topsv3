package br.com.topsdojob.v3.application.admin.moderacao.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAcaoModeracaoResponseDto(
        UUID id,
        String recursoTipo,
        UUID recursoId,
        String decisao,
        String status,
        String visibilidadeMidia,
        boolean auditoriaRegistrada,
        boolean emailRealEnviado,
        boolean hardDeleteExecutado,
        String requestId,
        OffsetDateTime decididoEm,
        String mensagem,
        UUID operacaoIdCliente,
        Integer versaoAnuncioAntes) {

    public AdminAcaoModeracaoResponseDto(
            UUID id, String recursoTipo, UUID recursoId, String decisao, String status,
            String visibilidadeMidia, boolean auditoriaRegistrada, boolean emailRealEnviado,
            boolean hardDeleteExecutado, String requestId, OffsetDateTime decididoEm,
            String mensagem) {
        this(id, recursoTipo, recursoId, decisao, status, visibilidadeMidia,
                auditoriaRegistrada, emailRealEnviado, hardDeleteExecutado,
                requestId, decididoEm, mensagem, null, null);
    }
}
