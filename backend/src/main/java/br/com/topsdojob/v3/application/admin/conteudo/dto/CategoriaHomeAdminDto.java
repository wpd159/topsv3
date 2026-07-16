package br.com.topsdojob.v3.application.admin.conteudo.dto;

import java.util.UUID;

public record CategoriaHomeAdminDto(
    UUID id,
    String categoriaCodigo,
    String categoriaNome,
    String nome,
    String descricao,
    String imagemUrl,
    int ordem,
    boolean ativo) {
}
