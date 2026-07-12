package br.com.topsdojob.v3.application.publico.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AnuncioCardPublicoDto(
        UUID id,
        String slug,
        String titulo,
        String descricaoResumo,
        BigDecimal preco,
        String categoria,
        LocalizacaoPublicaDto localizacao,
        List<MidiaPublicaDto> midias,
        boolean destaque,
        boolean topo,
        boolean midiaExtra,
        boolean story,
        boolean contatoDisponivel,
        boolean comLocal,
        boolean fazAnal,
        List<String> beneficiosPublicos,
        OffsetDateTime anunciaDesde,
        OffsetDateTime publicadoEm) {
}
