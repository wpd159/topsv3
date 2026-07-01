package br.com.topsdojob.v3.application.publico.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnuncioDetalhePublicoDto(
        String slug,
        String titulo,
        String descricao,
        BigDecimal preco,
        LocalizacaoPublicaDto localizacao,
        List<MidiaPublicaDto> midias,
        boolean destaque,
        boolean topo,
        boolean midiaExtra,
        boolean story,
        List<String> beneficiosPublicos,
        String contatoPublico,
        String pendenciaContatoPublico,
        OffsetDateTime publicadoEm,
        SeoRotaPublicaDto seo) {
}
