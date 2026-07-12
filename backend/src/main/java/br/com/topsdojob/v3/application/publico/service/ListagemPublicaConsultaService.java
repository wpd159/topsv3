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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final PoliticaContatoPublicoService contatoService;

    public ListagemPublicaConsultaService(
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioRepository anuncioRepository,
            AnuncioPublicoMapper anuncioMapper,
            AnuncioPublicoConsultaService anuncioConsultaService,
            SeoPublicoConsultaService seoService,
            PremiumPublicoMapper premiumMapper,
            PoliticaContatoPublicoService contatoService) {
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioRepository = anuncioRepository;
        this.anuncioMapper = anuncioMapper;
        this.anuncioConsultaService = anuncioConsultaService;
        this.seoService = seoService;
        this.premiumMapper = premiumMapper;
        this.contatoService = contatoService;
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porEstado(String uf, int pagina, int tamanho) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        Pageable pageable = pageable(pagina, tamanho);
        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(ufSeguro)
                .orElseThrow(() -> notFound("estado nao encontrado"));
        return listarLocalizacoes(
                estado,
                null,
                null,
                localizacaoRepository.findByEstadoId(estado.getId()),
                pageable);
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porCidade(String uf, String cidadeSlug, int pagina, int tamanho) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        Pageable pageable = pageable(pagina, tamanho);
        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(ufSeguro)
                .orElseThrow(() -> notFound("estado nao encontrado"));
        CidadeEntity cidade = cidadeRepository.findByEstadoIdAndSlug(estado.getId(), cidadeSegura)
                .orElseThrow(() -> notFound("cidade nao encontrada"));
        return listarLocalizacoes(
                estado,
                cidade,
                null,
                localizacaoRepository.findByCidadeId(cidade.getId()),
                pageable);
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porBairro(String uf, String cidadeSlug, String bairroSlug, int pagina, int tamanho) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        String bairroSeguro = RotaPublicaGuard.slug(bairroSlug, "bairro");
        Pageable pageable = pageable(pagina, tamanho);
        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(ufSeguro)
                .orElseThrow(() -> notFound("estado nao encontrado"));
        CidadeEntity cidade = cidadeRepository.findByEstadoIdAndSlug(estado.getId(), cidadeSegura)
                .orElseThrow(() -> notFound("cidade nao encontrada"));
        BairroEntity bairro = bairroRepository.findByCidadeIdAndSlug(cidade.getId(), bairroSeguro)
                .orElseThrow(() -> notFound("bairro nao encontrado"));
        return listarLocalizacoes(
                estado,
                cidade,
                bairro,
                localizacaoRepository.findByCidadeIdAndBairroId(cidade.getId(), bairro.getId()),
                pageable);
    }

    private ListaAnunciosPublicaDto listarLocalizacoes(
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro,
            List<AnuncioLocalizacaoEntity> localizacoes,
            Pageable pageable) {
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

        if (anuncios.isEmpty()) {
            throw notFound("nenhum anuncio publico encontrado na localidade");
        }

        Map<UUID, AnuncioLocalizacaoEntity> localizacaoPorAnuncio = localizacoes.stream()
                .collect(Collectors.toMap(AnuncioLocalizacaoEntity::getAnuncioId, Function.identity()));
        Map<UUID, CidadeEntity> cidades = cidadeRepository.findAllById(localizacoes.stream()
                        .map(AnuncioLocalizacaoEntity::getCidadeId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(CidadeEntity::getId, Function.identity()));
        Map<UUID, BairroEntity> bairros = bairroRepository.findAllById(localizacoes.stream()
                        .map(AnuncioLocalizacaoEntity::getBairroId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(BairroEntity::getId, Function.identity()));
        Map<UUID, java.time.OffsetDateTime> primeiraPublicacaoPorUsuario = anuncioRepository
                .findPrimeiraPublicacaoByUsuarioIdIn(anuncios.stream()
                        .map(AnuncioEntity::getUsuarioId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection::getUsuarioId,
                        AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection::getPrimeiraPublicacaoEm));

        List<AnuncioCardPublicoDto> itens = anuncios.stream()
                .map(anuncio -> {
                    AnuncioLocalizacaoEntity localizacao = localizacaoPorAnuncio.get(anuncio.getId());
                    CidadeEntity cidadeAnuncio = localizacao == null ? cidade : cidades.get(localizacao.getCidadeId());
                    BairroEntity bairroAnuncio = localizacao == null ? bairro : bairros.get(localizacao.getBairroId());
                    return anuncioMapper.toCard(
                            anuncio,
                            toLocalizacao(estado, cidadeAnuncio, bairroAnuncio, localizacao),
                            anuncioConsultaService.midias(anuncio.getId()),
                            premiumMapper.flags(anuncio),
                            contatoService.podeExporContato(anuncio),
                            primeiraPublicacaoPorUsuario.get(anuncio.getUsuarioId()));
                })
                .toList();

        String uf = estado.getUf();
        String cidadeSlug = cidade == null ? null : cidade.getSlug();
        LocalizacaoPublicaDto localidade = toLocalizacao(estado, cidade, bairro, null);
        return new ListaAnunciosPublicaDto(
                itens,
                PaginacaoPublicaDto.from(anuncios),
                localidade,
                cidade == null
                        ? seoService.buscarPorCaminho("/acompanhantes/" + uf.toLowerCase())
                        : bairro == null
                                ? seoService.paraCidade(uf, cidadeSlug)
                                : seoService.paraBairro(uf, cidadeSlug, bairro.getSlug()));
    }

    private LocalizacaoPublicaDto toLocalizacao(
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro,
            AnuncioLocalizacaoEntity localizacao) {
        return new LocalizacaoPublicaDto(
                estado.getUf(),
                estado.getNome(),
                cidade == null ? null : cidade.getNome(),
                cidade == null ? null : cidade.getSlug(),
                bairro == null ? null : bairro.getNome(),
                bairro == null ? null : bairro.getSlug(),
                localizacao == null ? null : localizacao.getEnderecoResumido());
    }

    private Pageable pageable(int pagina, int tamanho) {
        RotaPublicaGuard.page(pagina, tamanho);
        return PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "publicadoEm")
                .and(Sort.by("id")));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
