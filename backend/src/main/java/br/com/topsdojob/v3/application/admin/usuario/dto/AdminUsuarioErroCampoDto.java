package br.com.topsdojob.v3.application.admin.usuario.dto;

public record AdminUsuarioErroCampoDto(
    String campo,
    String codigo,
    String mensagem) {
}
