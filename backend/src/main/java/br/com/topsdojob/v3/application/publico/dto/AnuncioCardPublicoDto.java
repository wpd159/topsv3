package br.com.topsdojob.v3.application.publico.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnuncioCardPublicoDto(
        String slug,
        String titulo,
        String descricaoResumo,
        BigDecimal preco,
        LocalizacaoPublicaDto localizacao,
        List<MidiaPublicaDto> midias,
        boolean destaque,
        boolean topo,
        boolean midiaExtra,
        boolean story,
        List<String> beneficiosPublicos,
        OffsetDateTime publicadoEm) {
}
