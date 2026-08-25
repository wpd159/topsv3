package br.com.topsdojob.v3.application.publico.auth;

import org.springframework.http.HttpStatus;

public class PublicAuthException extends RuntimeException {

    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public PublicAuthException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public PublicAuthException(HttpStatus status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus status() {
        return status;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
