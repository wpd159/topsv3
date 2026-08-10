package br.com.topsdojob.v3.application.admin.creditos;

import org.springframework.http.HttpStatus;

public final class AdminPlanoCreditoException extends RuntimeException {

    private final HttpStatus status;

    public AdminPlanoCreditoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
