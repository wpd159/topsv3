package br.com.topsdojob.v3.application.publico.dto;

import java.util.List;

public record SolicitarAnuncioValidationErrorResponseDto(
        boolean criado,
        String mensagem,
        List<SolicitarAnuncioValidationErrorDto> erros,
        boolean publicacaoAutomaticaExecutada,
        boolean uploadRealExecutado,
        boolean pagamentoCriado,
        boolean creditoCriado,
        boolean premiumObrigatorio,
        boolean emailRealEnviado,
        boolean whatsappRealEnviado) {

    public static SolicitarAnuncioValidationErrorResponseDto from(
            List<SolicitarAnuncioValidationErrorDto> erros) {
        return new SolicitarAnuncioValidationErrorResponseDto(
                false,
                "solicitacao local invalida",
                List.copyOf(erros),
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }
}
