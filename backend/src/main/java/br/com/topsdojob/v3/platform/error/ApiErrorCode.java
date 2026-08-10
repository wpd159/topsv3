package br.com.topsdojob.v3.platform.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Requisição inválida."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Autenticação necessária."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado."),
    GONE(HttpStatus.GONE, "Verificacao expirada. Inicie novamente."),
    CONFLICT(HttpStatus.CONFLICT, "Conflito de estado."),
    STORY_JA_ATIVO(HttpStatus.CONFLICT, "Este anuncio ja possui um Story ativo."),

    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "Dados inválidos."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente mais tarde."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo acima do limite permitido."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de arquivo não permitido."),
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "Não foi possível concluir a operação. Tente novamente."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Serviço temporariamente indisponível."),
    PIX_CRIACAO_INDISPONIVEL(
            HttpStatus.BAD_GATEWAY,
            "Não foi possível iniciar a cobrança Pix agora. Tente novamente."),
    PIX_CONSULTA_INDISPONIVEL(
            HttpStatus.BAD_GATEWAY,
            "Não foi possível consultar o pagamento agora. A cobrança foi preservada e pode ser retomada com segurança."),
    PIX_QR_CODE_INDISPONIVEL(
            HttpStatus.BAD_GATEWAY,
            "Não foi possível carregar o QR Code agora. Tente novamente."),
    PIX_CANCELAMENTO_INDISPONIVEL(
            HttpStatus.BAD_GATEWAY,
            "Não foi possível cancelar a cobrança agora. Tente novamente."),
    PIX_COBRANCA_PENDENTE(
            HttpStatus.CONFLICT,
            "Existe uma cobrança Pix pendente. Retome ou cancele essa cobrança antes de iniciar outra."),
    PIX_PAGAMENTO_CONFIRMADO(
            HttpStatus.CONFLICT,
            "Este pagamento já foi confirmado e não pode ser cancelado."),
    PIX_CANCELAMENTO_NAO_PERMITIDO(
            HttpStatus.CONFLICT,
            "Esta cobrança não pode ser cancelada."),
    PIX_IDEMPOTENCIA_CONFLITANTE(
            HttpStatus.CONFLICT,
            "A chave de repetição não corresponde a esta operação."),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Não foi possível concluir a operação. Tente novamente.");

    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
