package br.com.topsdojob.v3.application.admin.conteudo.dto;

public record CategoriaHomeAdminRequest(
    String categoriaCodigo,
    String nome,
    String descricao,
    Integer ordem,
    Boolean ativo) {
}
