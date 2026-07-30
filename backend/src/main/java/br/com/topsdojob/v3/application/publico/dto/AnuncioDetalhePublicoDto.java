package br.com.topsdojob.v3.application.publico.dto;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AnuncioDetalhePublicoDto(
        UUID id,
        String slug,
        String titulo,
        String descricao,
        String linkConteudo,
        BigDecimal preco,
        String categoria,
        String username,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer idade,
        boolean idadeOculta,
        LocalizacaoPublicaDto localizacao,
        List<MidiaPublicaDto> midias,
        boolean destaque,
        boolean topo,
        boolean midiaExtra,
        boolean story,
        boolean contatoDisponivel,
        boolean comLocal,
        boolean fazAnal,
        List<String> locaisAtendimento,
        List<String> servicos,
        List<String> beneficiosPublicos,
        String contatoPublico,
        String pendenciaContatoPublico,
        OffsetDateTime anunciaDesde,
        OffsetDateTime publicadoEm,
        SeoRotaPublicaDto seo,
        VisualizacoesCanonicasDto visualizacoes) {
}
