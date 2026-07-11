package br.com.topsdojob.v3.application.publico.auth.dto;

public record PublicAuthStatusDto(
        boolean autenticado,
        String status) {
}
