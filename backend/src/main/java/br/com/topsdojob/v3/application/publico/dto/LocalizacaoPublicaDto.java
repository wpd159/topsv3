package br.com.topsdojob.v3.application.publico.dto;

public record LocalizacaoPublicaDto(
        String uf,
        String cidade,
        String cidadeSlug,
        String bairro,
        String bairroSlug,
        String enderecoResumido) {
}
