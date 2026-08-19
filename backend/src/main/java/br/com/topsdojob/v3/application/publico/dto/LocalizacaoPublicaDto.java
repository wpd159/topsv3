package br.com.topsdojob.v3.application.publico.dto;

import br.com.topsdojob.v3.application.publico.EnderecoResumidoPublicoPolicy;

public record LocalizacaoPublicaDto(
        String uf,
        String estado,
        String cidade,
        String cidadeSlug,
        String bairro,
        String bairroSlug,
        String enderecoResumido) {
    public LocalizacaoPublicaDto {
        enderecoResumido = EnderecoResumidoPublicoPolicy.projetar(enderecoResumido);
    }
}
