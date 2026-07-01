package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.AnuncioCardPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnuncioPublicoMapper {

    public static final String PENDENTE_POLITICA_WHATSAPP =
            "PENDENTE_POLITICA_EXPOSICAO_WHATSAPP_PUBLICO";

    public AnuncioCardPublicoDto toCard(
            AnuncioEntity anuncio,
            LocalizacaoPublicaDto localizacao,
            List<MidiaPublicaDto> midias) {
        return new AnuncioCardPublicoDto(
                anuncio.getSlug(),
                anuncio.getTitulo(),
                resumo(anuncio.getDescricao()),
                anuncio.getPreco(),
                localizacao,
                List.copyOf(midias),
                false,
                false,
                !midias.isEmpty(),
                false,
                List.of(),
                anuncio.getPublicadoEm());
    }

    public AnuncioDetalhePublicoDto toDetalhe(
            AnuncioEntity anuncio,
            LocalizacaoPublicaDto localizacao,
            List<MidiaPublicaDto> midias,
            SeoRotaPublicaDto seo) {
        return new AnuncioDetalhePublicoDto(
                anuncio.getSlug(),
                anuncio.getTitulo(),
                anuncio.getDescricao(),
                anuncio.getPreco(),
                localizacao,
                List.copyOf(midias),
                false,
                false,
                !midias.isEmpty(),
                false,
                List.of(),
                null,
                PENDENTE_POLITICA_WHATSAPP,
                anuncio.getPublicadoEm(),
                seo);
    }

    private String resumo(String descricao) {
        if (descricao == null || descricao.length() <= 160) {
            return descricao;
        }
        return descricao.substring(0, 157).trim() + "...";
    }
}
