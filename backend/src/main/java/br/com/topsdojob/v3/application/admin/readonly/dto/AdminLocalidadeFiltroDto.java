package br.com.topsdojob.v3.application.admin.readonly.dto;

public record AdminLocalidadeFiltroDto(
        String uf,
        String estado,
        String cidade,
        String cidadeSlug,
        String bairro,
        String bairroSlug) {
}
