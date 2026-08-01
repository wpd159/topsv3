package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.List;
import java.util.UUID;

public record PainelAnuncianteRecomendacoesDto(
        boolean habilitado,
        List<RecomendacaoDto> itens) {

    public record RecomendacaoDto(
            String id,
            UUID anuncioId,
            String anuncioSlug,
            String anuncioTitulo,
            String tipo,
            String titulo,
            String descricao,
            String acaoRotulo,
            String acaoHref) {
    }
}
