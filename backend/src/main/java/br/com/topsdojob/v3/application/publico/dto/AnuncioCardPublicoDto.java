package br.com.topsdojob.v3.application.publico.dto;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import com.fasterxml.jackson.annotation.JsonInclude;
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
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer idade,
        LocalizacaoPublicaDto localizacao,
        List<MidiaPublicaDto> midias,
        boolean destaque,
        boolean topo,
        boolean midiaExtra,
        boolean story,
        boolean contatoDisponivel,
        boolean whatsappCard,
        boolean comLocal,
        boolean fazAnal,
        List<String> beneficiosPublicos,
        OffsetDateTime anunciaDesde,
        OffsetDateTime publicadoEm,
        VisualizacoesCanonicasDto visualizacoes) {
}
