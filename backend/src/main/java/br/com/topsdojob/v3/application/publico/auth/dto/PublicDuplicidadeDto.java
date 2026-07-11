package br.com.topsdojob.v3.application.publico.auth.dto;

public record PublicDuplicidadeDto(
        boolean emailExistente,
        boolean usernameExistente,
        boolean telefoneExistente) {
}
