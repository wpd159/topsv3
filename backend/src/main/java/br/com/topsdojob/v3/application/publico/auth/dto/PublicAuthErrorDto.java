package br.com.topsdojob.v3.application.publico.auth.dto;

public record PublicAuthErrorDto(
        int status,
        String message) {
}
