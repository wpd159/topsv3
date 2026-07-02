package br.com.topsdojob.v3.application.admin.auth.dto;

public record AdminLoginRequestDto(
        String login,
        String senha) {
}
