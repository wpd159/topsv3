package br.com.topsdojob.v3.application.publico.auth;

import org.springframework.http.HttpStatus;

public class PublicAuthException extends RuntimeException {

    private final HttpStatus status;

    public PublicAuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
