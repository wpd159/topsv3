package br.com.topsdojob.v3.application.admin.usuario.dto;

public record AdminUsuarioIndicadoresDto(
    long totalUsuarios,
    long novosHoje,
    long comAnuncios,
    long semAnuncios) {
}
