package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.CidadeLocalidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.IndexacaoLocalidadePublicaDto;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class LocalidadeSeoIndexabilidadePolicy {

    static final int MIN_ANUNCIOS_CIDADE = 5;
    static final int MIN_ANUNCIOS_BAIRRO = 3;

    public IndexacaoLocalidadePublicaDto estado(
            long anunciosElegiveisUnicos,
            Collection<CidadeLocalidadePublicaDto> cidades) {
        boolean possuiCidadeIndexavel = cidades != null && cidades.stream()
                .anyMatch(cidade -> cidade.indexacao() != null && cidade.indexacao().indexavel());
        return decisao(
                possuiCidadeIndexavel,
                possuiCidadeIndexavel ? "CIDADE_INDEXAVEL" : "SEM_CIDADE_INDEXAVEL",
                anunciosElegiveisUnicos,
                MIN_ANUNCIOS_CIDADE);
    }

    public IndexacaoLocalidadePublicaDto cidade(long anunciosElegiveisUnicos) {
        return porInventario(anunciosElegiveisUnicos, MIN_ANUNCIOS_CIDADE);
    }

    public IndexacaoLocalidadePublicaDto bairro(long anunciosElegiveisUnicos) {
        return porInventario(anunciosElegiveisUnicos, MIN_ANUNCIOS_BAIRRO);
    }

    private IndexacaoLocalidadePublicaDto porInventario(long anunciosElegiveisUnicos, int minimoNecessario) {
        boolean indexavel = anunciosElegiveisUnicos >= minimoNecessario;
        return decisao(
                indexavel,
                indexavel ? "INVENTARIO_SUFICIENTE" : "INVENTARIO_INSUFICIENTE",
                anunciosElegiveisUnicos,
                minimoNecessario);
    }

    private IndexacaoLocalidadePublicaDto decisao(
            boolean indexavel,
            String motivo,
            long anunciosElegiveisUnicos,
            int minimoNecessario) {
        return new IndexacaoLocalidadePublicaDto(
                indexavel,
                motivo,
                Math.max(0, anunciosElegiveisUnicos),
                minimoNecessario,
                true);
    }
}
