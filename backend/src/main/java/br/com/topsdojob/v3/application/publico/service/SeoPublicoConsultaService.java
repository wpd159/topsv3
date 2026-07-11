package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.SeoPublicoMapper;
import br.com.topsdojob.v3.persistence.repository.SeoUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeoPublicoConsultaService {

    private final SeoUrlRepository seoUrlRepository;
    private final SeoPublicoMapper mapper;

    public SeoPublicoConsultaService(SeoUrlRepository seoUrlRepository, SeoPublicoMapper mapper) {
        this.seoUrlRepository = seoUrlRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public SeoRotaPublicaDto buscarPorCaminho(String caminho) {
        String caminhoSeguro = RotaPublicaGuard.caminhoPublico(caminho);
        return seoUrlRepository.findByCaminhoPublico(caminhoSeguro)
                .map(mapper::fromEntity)
                .orElseGet(() -> mapper.fallback(caminhoSeguro));
    }

    public SeoRotaPublicaDto paraAnuncio(String slug) {
        return mapper.fallback("/anuncios/" + RotaPublicaGuard.slug(slug, "slug"));
    }

    public SeoRotaPublicaDto paraAnuncio(String slug, boolean indexavel) {
        return mapper.fallback("/anuncios/" + RotaPublicaGuard.slug(slug, "slug"), indexavel);
    }

    public SeoRotaPublicaDto paraCidade(String uf, String cidade) {
        return mapper.fallback("/acompanhantes/"
                + RotaPublicaGuard.uf(uf).toLowerCase()
                + "/"
                + RotaPublicaGuard.slug(cidade, "cidade"));
    }

    public SeoRotaPublicaDto paraBairro(String uf, String cidade, String bairro) {
        return mapper.fallback("/acompanhantes/"
                + RotaPublicaGuard.uf(uf).toLowerCase()
                + "/"
                + RotaPublicaGuard.slug(cidade, "cidade")
                + "/"
                + RotaPublicaGuard.slug(bairro, "bairro"));
    }
}
