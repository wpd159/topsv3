package br.com.topsdojob.v3.platform.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Requisição inválida."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Autenticação necessária."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado."),
    CONFLICT(HttpStatus.CONFLICT, "Conflito de estado."),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "Dados inválidos."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente mais tarde."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo acima do limite permitido."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de arquivo não permitido."),
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "Provedor externo indisponivel."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Servico temporariamente indisponivel."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado.");

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
