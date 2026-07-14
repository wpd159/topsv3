package br.com.topsdojob.v3.application.publico.favorito.dto;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FavoritoPublicoDto(
        UUID id,
        String slug,
        String titulo,
        String descricaoResumo,
        BigDecimal preco,
        LocalizacaoPublicaDto localizacao,
        List<MidiaPublicaDto> midias,
        boolean contatoDisponivel,
        boolean comLocal,
        boolean fazAnal,
        OffsetDateTime anunciaDesde,
        OffsetDateTime adicionadoEm) {
}
