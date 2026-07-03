package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnuncioPublicoConsultaService {

    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final AnuncioPublicoMapper anuncioMapper;
    private final MidiaPublicaMapper midiaMapper;
    private final SeoPublicoConsultaService seoService;
    private final IdadePublicaService idadeService;
    private final PremiumPublicoMapper premiumMapper;

    public AnuncioPublicoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            AnuncioPublicoMapper anuncioMapper,
            MidiaPublicaMapper midiaMapper,
            SeoPublicoConsultaService seoService,
            IdadePublicaService idadeService,
            PremiumPublicoMapper premiumMapper) {
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.anuncioMapper = anuncioMapper;
        this.midiaMapper = midiaMapper;
        this.seoService = seoService;
        this.idadeService = idadeService;
        this.premiumMapper = premiumMapper;
    }

    @Transactional(readOnly = true)
    public AnuncioDetalhePublicoDto buscarPorSlug(String slug) {
        return buscarPorSlug(slug, false);
    }

    @Transactional(readOnly = true)
    public AnuncioDetalhePublicoDto buscarPorSlug(String slug, HttpServletRequest request) {
        return buscarPorSlug(slug, idadeService.idadeConfirmada(request));
    }

    private AnuncioDetalhePublicoDto buscarPorSlug(String slug, boolean idadeConfirmada) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        AnuncioEntity anuncio = anuncioRepository
                .findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        slugSeguro,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO)
                .filter(found -> classificacaoPublicavel(found.getClassificacaoConteudo(), idadeConfirmada))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

        return anuncioMapper.toDetalhe(
                anuncio,
                localizacao(anuncio.getId()),
                midias(anuncio.getId(), idadeConfirmada),
                seoService.paraAnuncio(slugSeguro),
                premiumMapper.flags(anuncio));
    }

    LocalizacaoPublicaDto localizacao(UUID anuncioId) {
        return localizacaoRepository.findByAnuncioId(anuncioId)
                .map(this::toLocalizacaoPlaceholder)
                .orElseGet(() -> new LocalizacaoPublicaDto(null, null, null, null, null, null));
    }

    List<MidiaPublicaDto> midias(UUID anuncioId) {
        return midias(anuncioId, false);
    }

    List<MidiaPublicaDto> midias(UUID anuncioId, boolean idadeConfirmada) {
        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioId(anuncioId);
        List<UUID> arquivoIds = vinculos.stream()
                .map(AnuncioMidiaEntity::getArquivoMidiaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        return midiaMapper.publicas(vinculos, arquivos, idadeConfirmada);
    }

    private boolean classificacaoPublicavel(ClassificacaoConteudo classificacao, boolean idadeConfirmada) {
        return classificacao == ClassificacaoConteudo.LIVRE
                || (idadeConfirmada && classificacao == ClassificacaoConteudo.BLOQUEADO);
    }

    private LocalizacaoPublicaDto toLocalizacaoPlaceholder(AnuncioLocalizacaoEntity localizacao) {
        return new LocalizacaoPublicaDto(
                null,
                null,
                null,
                null,
                null,
                localizacao.getEnderecoResumido());
    }
}
