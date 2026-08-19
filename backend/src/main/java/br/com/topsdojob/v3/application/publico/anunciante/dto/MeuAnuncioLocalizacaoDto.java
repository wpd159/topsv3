package br.com.topsdojob.v3.application.publico.anunciante.dto;

public record MeuAnuncioLocalizacaoDto(
        String uf,
        String cidade,
        String cidadeSlug,
        String bairro,
        String bairroSlug,
        String enderecoResumido) {
}
