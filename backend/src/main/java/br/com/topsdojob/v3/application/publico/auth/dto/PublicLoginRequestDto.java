package br.com.topsdojob.v3.application.publico.auth.dto;

public record PublicLoginRequestDto(
        String email,
        String senha) {
}
