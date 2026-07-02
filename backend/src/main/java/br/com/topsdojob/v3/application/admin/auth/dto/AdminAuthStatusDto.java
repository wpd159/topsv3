package br.com.topsdojob.v3.application.admin.auth.dto;

public record AdminAuthStatusDto(
        boolean autenticado,
        String status) {
}
