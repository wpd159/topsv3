package br.com.topsdojob.v3.application.publico.anunciante.dto;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MeuAnuncioDto(
        UUID id,
        String slug,
        String titulo,
        String descricao,
        String categoria,
        BigDecimal preco,
        String whatsapp,
        List<String> locaisAtendimento,
        List<String> servicos,
        String status,
        String statusModeracao,
        MeuAnuncioLocalizacaoDto localizacao,
        MeuAnuncioCapaDto capa,
        List<MeuAnuncioMidiaDto> midias,
        OffsetDateTime atualizadoEm,
        MeuAnuncioAcoesDto acoesPermitidas,
        VisualizacoesCanonicasDto visualizacoes) {
}
