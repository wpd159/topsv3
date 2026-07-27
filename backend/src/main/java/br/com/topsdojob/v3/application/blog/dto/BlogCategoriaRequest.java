package br.com.topsdojob.v3.application.blog.dto;

public record BlogCategoriaRequest(
    String nome,
    String slug,
    Integer ordem,
    Boolean ativa,
    Long versao) {
}
