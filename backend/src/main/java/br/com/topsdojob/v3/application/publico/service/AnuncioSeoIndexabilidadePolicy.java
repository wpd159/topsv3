package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AnuncioSeoIndexabilidadePolicy {

    private static final int MIN_DESCRICAO = 120;
    private static final int MIN_FOTOS_PUBLICAS_REAIS = 1;
    private static final Set<String> TITULOS_GENERICOS = Set.of(
            "acompanhante",
            "acompanhante 1",
            "acompanhante 2",
            "anuncio",
            "anuncio 1",
            "perfil",
            "teste",
            "nova na cidade",
            "novinha chegando na cidade");
    private static final Set<String> SLUGS_GENERICOS = Set.of(
            "acompanhante-1",
            "acompanhante-2",
            "sem-acompanhante",
            "nova-na-cidade",
            "novinha-chegando-na-cidade",
            "teste");

    public boolean indexavel(
            AnuncioEntity anuncio,
            LocalizacaoPublicaDto localizacao,
            List<MidiaPublicaDto> midiasPublicas) {
        return indexavel(
                anuncio,
                localizacao,
                fotosPublicas(midiasPublicas) >= MIN_FOTOS_PUBLICAS_REAIS);
    }

    public boolean indexavel(
            AnuncioEntity anuncio,
            LocalizacaoPublicaDto localizacao,
            boolean possuiFotoPublica) {
        return anuncioPublico(anuncio)
                && localizacaoValida(localizacao)
                && tituloUtil(anuncio)
                && descricaoUtil(anuncio)
                && possuiFotoPublica;
    }

    private boolean anuncioPublico(AnuncioEntity anuncio) {
        return anuncio != null
                && anuncio.getStatus() == StatusAnuncio.PUBLICADO
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO
                && anuncio.getRemovidoEm() == null
                && texto(anuncio.getSlug()).length() > 0;
    }

    private boolean localizacaoValida(LocalizacaoPublicaDto localizacao) {
        return localizacao != null
                && texto(localizacao.uf()).length() == 2
                && texto(localizacao.cidade()).length() > 0
                && texto(localizacao.cidadeSlug()).length() > 0;
    }

    private boolean tituloUtil(AnuncioEntity anuncio) {
        String titulo = normalizar(anuncio.getTitulo());
        String slug = normalizar(anuncio.getSlug()).replace(' ', '-');
        return titulo.length() >= 8
                && !TITULOS_GENERICOS.contains(titulo)
                && !SLUGS_GENERICOS.contains(slug)
                && !titulo.matches("^(acompanhante|anuncio|perfil|teste)\\s*\\d*$");
    }

    private boolean descricaoUtil(AnuncioEntity anuncio) {
        return texto(anuncio.getDescricao()).replaceAll("\\s+", " ").length() >= MIN_DESCRICAO;
    }

    private long fotosPublicas(List<MidiaPublicaDto> midias) {
        if (midias == null) {
            return 0;
        }
        return midias.stream()
                .filter(midia -> "FOTO".equals(midia.tipo()))
                .filter(midia -> "LIVRE".equals(midia.visibilidadeMidia()))
                .filter(midia -> midia.autorizada() && texto(midia.urlPublica()).length() > 0)
                .count();
    }

    private String normalizar(String value) {
        return Normalizer.normalize(texto(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String texto(String value) {
        return value == null ? "" : value.trim();
    }
}
