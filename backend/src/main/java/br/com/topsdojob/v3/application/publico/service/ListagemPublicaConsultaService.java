package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.AnuncioCardPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.PaginacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListagemPublicaConsultaService {

    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioPublicoMapper anuncioMapper;
    private final AnuncioPublicoConsultaService anuncioConsultaService;
    private final SeoPublicoConsultaService seoService;
    private final PremiumPublicoMapper premiumMapper;

    public ListagemPublicaConsultaService(
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioRepository anuncioRepository,
            AnuncioPublicoMapper anuncioMapper,
            AnuncioPublicoConsultaService anuncioConsultaService,
            SeoPublicoConsultaService seoService,
            PremiumPublicoMapper premiumMapper) {
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioRepository = anuncioRepository;
        this.anuncioMapper = anuncioMapper;
        this.anuncioConsultaService = anuncioConsultaService;
        this.seoService = seoService;
        this.premiumMapper = premiumMapper;
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porCidade(String uf, String cidadeSlug, int pagina, int tamanho) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        RotaPublicaGuard.page(pagina, tamanho);
        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "publicadoEm")
                .and(Sort.by("id")));

        return estadoRepository.findByUfIgnoreCase(ufSeguro)
                .flatMap(estado -> cidadeRepository.findByEstadoIdAndSlug(estado.getId(), cidadeSegura)
                        .map(cidade -> listarCidade(estado, cidade, null, pageable)))
                .orElseGet(() -> vazia(pageable, seoService.paraCidade(ufSeguro, cidadeSegura)));
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porBairro(String uf, String cidadeSlug, String bairroSlug, int pagina, int tamanho) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        String bairroSeguro = RotaPublicaGuard.slug(bairroSlug, "bairro");
        RotaPublicaGuard.page(pagina, tamanho);
        Pageable pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "publicadoEm")
                .and(Sort.by("id")));

        return estadoRepository.findByUfIgnoreCase(ufSeguro)
                .flatMap(estado -> cidadeRepository.findByEstadoIdAndSlug(estado.getId(), cidadeSegura)
                        .flatMap(cidade -> bairroRepository.findByCidadeIdAndSlug(cidade.getId(), bairroSeguro)
                                .map(bairro -> listarCidade(estado, cidade, bairro, pageable))))
                .orElseGet(() -> vazia(pageable, seoService.paraBairro(ufSeguro, cidadeSegura, bairroSeguro)));
    }

    private ListaAnunciosPublicaDto listarCidade(
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro,
            Pageable pageable) {
        List<AnuncioLocalizacaoEntity> localizacoes = bairro == null
                ? localizacaoRepository.findByCidadeId(cidade.getId())
                : localizacaoRepository.findByCidadeIdAndBairroId(cidade.getId(), bairro.getId());

        List<UUID> anuncioIds = localizacoes.stream()
                .map(AnuncioLocalizacaoEntity::getAnuncioId)
                .toList();

        Page<AnuncioEntity> anuncios = anuncioIds.isEmpty()
                ? Page.empty(pageable)
                : anuncioRepository.findByIdInAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        anuncioIds,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO,
                        pageable);

        Map<UUID, AnuncioLocalizacaoEntity> localizacaoPorAnuncio = localizacoes.stream()
                .collect(Collectors.toMap(AnuncioLocalizacaoEntity::getAnuncioId, Function.identity()));

        List<AnuncioCardPublicoDto> itens = anuncios.stream()
                .map(anuncio -> anuncioMapper.toCard(
                        anuncio,
                        toLocalizacao(estado, cidade, bairro, localizacaoPorAnuncio.get(anuncio.getId())),
                        anuncioConsultaService.midias(anuncio.getId()),
                        premiumMapper.flags(anuncio)))
                .toList();

        String uf = estado.getUf();
        String cidadeSlug = cidade.getSlug();
        return new ListaAnunciosPublicaDto(
                itens,
                PaginacaoPublicaDto.from(anuncios),
                bairro == null
                        ? seoService.paraCidade(uf, cidadeSlug)
                        : seoService.paraBairro(uf, cidadeSlug, bairro.getSlug()));
    }

    private ListaAnunciosPublicaDto vazia(Pageable pageable, br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto seo) {
        return new ListaAnunciosPublicaDto(
                List.of(),
                new PaginacaoPublicaDto(pageable.getPageNumber(), pageable.getPageSize(), 0, 0),
                seo);
    }

    private LocalizacaoPublicaDto toLocalizacao(
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro,
            AnuncioLocalizacaoEntity localizacao) {
        return new LocalizacaoPublicaDto(
                estado.getUf(),
                cidade.getNome(),
                cidade.getSlug(),
                bairro == null ? null : bairro.getNome(),
                bairro == null ? null : bairro.getSlug(),
                localizacao == null ? null : localizacao.getEnderecoResumido());
    }
}
