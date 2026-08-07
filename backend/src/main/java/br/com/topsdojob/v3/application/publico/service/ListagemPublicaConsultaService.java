package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioCardPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosCategoriaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.PaginacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
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
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final OrdemSeedPublicaService ordemSeedService;
    private final VisualizacaoTotalCanonicaService visualizacaoService;
    private final IdadeAnunciantePublicaService idadeService;

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
            PoliticaContatoPublicoService contatoService,
            OrdemSeedPublicaService ordemSeedService,
            VisualizacaoTotalCanonicaService visualizacaoService,
            IdadeAnunciantePublicaService idadeService) {
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
        this.ordemSeedService = ordemSeedService;
        this.visualizacaoService = visualizacaoService;
        this.idadeService = idadeService;
    }

    @Transactional(readOnly = true)
    public ListaAnunciosCategoriaPublicaDto listar(
            String categoriaCodigo,
            String busca,
            String anunciante,
            int pagina,
            int tamanho,
            String ordemSeed) {
        Pageable pageable = pageable(pagina, tamanho);
        CategoriaAnuncio categoria = categoria(categoriaCodigo);
        String termoBusca = termoBusca(busca);
        UUID usuarioId = usuarioId(anunciante);
        long seed = ordemSeedService.resolver(ordemSeed);

        Page<AnuncioEntity> paginaAnuncios = anuncioRepository.findPublicosOrdenados(
                categoria == null ? null : categoria.name(),
                termoBusca,
                usuarioId,
                agora(),
                seed,
                pageable);

        List<AnuncioLocalizacaoEntity> localizacoes = paginaAnuncios.isEmpty()
                ? List.of()
                : localizacaoRepository.findByAnuncioIdIn(paginaAnuncios.stream()
                        .map(AnuncioEntity::getId)
                        .toList());
        Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio = premiumMapper.flagsPorAnuncios(paginaAnuncios.getContent());
        List<AnuncioCardPublicoDto> itens = mapearCardsCategoria(
                paginaAnuncios.getContent(), localizacoes, premiumPorAnuncio);

        return new ListaAnunciosCategoriaPublicaDto(
                itens,
                PaginacaoPublicaDto.from(paginaAnuncios, seed),
                categoria == null ? null : categoria.name());
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porEstado(String uf, int pagina, int tamanho, String ordemSeed) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        Pageable pageable = pageable(pagina, tamanho);
        long seed = ordemSeedService.resolver(ordemSeed);
        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(ufSeguro)
                .orElseThrow(() -> notFound("estado nao encontrado"));
        return listarLocalizacoes(
                estado,
                null,
                null,
                pageable,
                seed);
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porCidade(
            String uf, String cidadeSlug, int pagina, int tamanho, String ordemSeed) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        Pageable pageable = pageable(pagina, tamanho);
        long seed = ordemSeedService.resolver(ordemSeed);
        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(ufSeguro)
                .orElseThrow(() -> notFound("estado nao encontrado"));
        CidadeEntity cidade = cidadeRepository.findByEstadoIdAndSlug(estado.getId(), cidadeSegura)
                .orElseThrow(() -> notFound("cidade nao encontrada"));
        return listarLocalizacoes(
                estado,
                cidade,
                null,
                pageable,
                seed);
    }

    @Transactional(readOnly = true)
    public ListaAnunciosPublicaDto porBairro(
            String uf,
            String cidadeSlug,
            String bairroSlug,
            int pagina,
            int tamanho,
            String ordemSeed) {
        String ufSeguro = RotaPublicaGuard.uf(uf);
        String cidadeSegura = RotaPublicaGuard.slug(cidadeSlug, "cidade");
        String bairroSeguro = RotaPublicaGuard.slug(bairroSlug, "bairro");
        Pageable pageable = pageable(pagina, tamanho);
        long seed = ordemSeedService.resolver(ordemSeed);
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
                pageable,
                seed);
    }

    private ListaAnunciosPublicaDto listarLocalizacoes(
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro,
            Pageable pageable,
            long ordemSeed) {
        Page<AnuncioEntity> anuncios = anuncioRepository.findPublicosPorLocalidadeOrdenados(
                estado.getId(),
                cidade == null ? null : cidade.getId(),
                bairro == null ? null : bairro.getId(),
                agora(),
                ordemSeed,
                pageable);

        if (anuncios.isEmpty()) {
            throw notFound("nenhum anuncio publico encontrado na localidade");
        }

        List<UUID> anuncioIds = anuncios.stream().map(AnuncioEntity::getId).toList();
        List<AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findByAnuncioIdIn(anuncioIds);

        Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio = premiumMapper.flagsPorAnuncios(anuncios.getContent());
        Map<UUID, IdadeAnunciantePublicaService.Resultado> idadePorAnuncio =
                idadeService.resolverPorAnuncios(anuncios.getContent(), premiumPorAnuncio);
        Map<UUID, VisualizacoesCanonicasDto> visualizacoesPorAnuncio =
                visualizacaoService.calcularEmLote(anuncioIds);
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
        Map<UUID, List<MidiaPublicaDto>> midiasPorAnuncio =
                anuncioConsultaService.midiasPorAnuncios(anuncioIds, premiumPorAnuncio);

        List<AnuncioCardPublicoDto> itens = anuncios.stream()
                .map(anuncio -> {
                    AnuncioLocalizacaoEntity localizacao = localizacaoPorAnuncio.get(anuncio.getId());
                    CidadeEntity cidadeAnuncio = localizacao == null ? cidade : cidades.get(localizacao.getCidadeId());
                    BairroEntity bairroAnuncio = localizacao == null ? bairro : bairros.get(localizacao.getBairroId());
                    PremiumPublicoFlagsDto premium = premiumPorAnuncio
                            .getOrDefault(anuncio.getId(), PremiumPublicoFlagsDto.vazio());
                    return anuncioMapper.toCard(
                            anuncio,
                            toLocalizacao(estado, cidadeAnuncio, bairroAnuncio, localizacao),
                            midiasPorAnuncio.getOrDefault(anuncio.getId(), List.of()),
                            premium,
                            idadePorAnuncio.get(anuncio.getId()),
                            contatoService.podeExporContato(anuncio),
                            primeiraPublicacaoPorUsuario.get(anuncio.getUsuarioId()),
                            visualizacoes(visualizacoesPorAnuncio, anuncio.getId()));
                })
                .toList();

        String uf = estado.getUf();
        String cidadeSlug = cidade == null ? null : cidade.getSlug();
        LocalizacaoPublicaDto localidade = toLocalizacao(estado, cidade, bairro, null);
        return new ListaAnunciosPublicaDto(
                itens,
                PaginacaoPublicaDto.from(anuncios, ordemSeed),
                localidade,
                cidade == null
                        ? seoService.buscarPorCaminho("/acompanhantes/" + uf.toLowerCase())
                        : bairro == null
                                ? seoService.paraCidade(uf, cidadeSlug)
                                : seoService.paraBairro(uf, cidadeSlug, bairro.getSlug()));
    }

    private List<AnuncioCardPublicoDto> mapearCardsCategoria(
            List<AnuncioEntity> anuncios,
            List<AnuncioLocalizacaoEntity> localizacoes,
            Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio) {
        if (anuncios.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnuncioLocalizacaoEntity> localizacaoPorAnuncio = localizacoes.stream()
                .collect(Collectors.toMap(AnuncioLocalizacaoEntity::getAnuncioId, Function.identity()));
        Map<UUID, EstadoEntity> estados = estadoRepository.findAllById(localizacoes.stream()
                        .map(AnuncioLocalizacaoEntity::getEstadoId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(EstadoEntity::getId, Function.identity()));
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
        Map<UUID, List<MidiaPublicaDto>> midiasPorAnuncio =
                anuncioConsultaService.midiasPorAnuncios(
                        anuncios.stream().map(AnuncioEntity::getId).toList(),
                        premiumPorAnuncio);
        Map<UUID, VisualizacoesCanonicasDto> visualizacoesPorAnuncio = visualizacaoService.calcularEmLote(
                anuncios.stream().map(AnuncioEntity::getId).toList());
        Map<UUID, IdadeAnunciantePublicaService.Resultado> idadePorAnuncio =
                idadeService.resolverPorAnuncios(anuncios, premiumPorAnuncio);

        return anuncios.stream()
                .map(anuncio -> {
                    AnuncioLocalizacaoEntity localizacao = localizacaoPorAnuncio.get(anuncio.getId());
                    if (localizacao == null) {
                        throw new IllegalStateException("anuncio publico sem localizacao canonica");
                    }
                    EstadoEntity estado = estados.get(localizacao.getEstadoId());
                    CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
                    BairroEntity bairro = localizacao.getBairroId() == null
                            ? null
                            : bairros.get(localizacao.getBairroId());
                    if (estado == null || cidade == null) {
                        throw new IllegalStateException("anuncio publico com localidade inconsistente");
                    }
                    PremiumPublicoFlagsDto premium = premiumPorAnuncio
                            .getOrDefault(anuncio.getId(), PremiumPublicoFlagsDto.vazio());
                    return anuncioMapper.toCard(
                            anuncio,
                            toLocalizacao(estado, cidade, bairro, localizacao),
                            midiasPorAnuncio.getOrDefault(anuncio.getId(), List.of()),
                            premium,
                            idadePorAnuncio.get(anuncio.getId()),
                            contatoService.podeExporContato(anuncio),
                            primeiraPublicacaoPorUsuario.get(anuncio.getUsuarioId()),
                            visualizacoes(visualizacoesPorAnuncio, anuncio.getId()));
                })
                .toList();
    }

    private VisualizacoesCanonicasDto visualizacoes(
            Map<UUID, VisualizacoesCanonicasDto> visualizacoesPorAnuncio,
            UUID anuncioId) {
        return Objects.requireNonNull(
                visualizacoesPorAnuncio.get(anuncioId),
                "visualizacoes canonicas ausentes para anuncio");
    }

    private CategoriaAnuncio categoria(String codigo) {
        if (codigo == null || codigo.isBlank() || "TODOS".equalsIgnoreCase(codigo.trim())) {
            return null;
        }
        return CategoriaAnuncio.porCodigo(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria invalida"));
    }

    private String termoBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = busca.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (termo.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "busca deve ter no maximo 80 caracteres");
        }
        return normalizarBusca(termo);
    }

    private UUID usuarioId(String anunciante) {
        if (anunciante == null || anunciante.isBlank()) {
            return null;
        }
        String username = RotaPublicaGuard.username(anunciante);
        return anuncioRepository.findUsuarioPublicoPorUsername(username)
                .map(AnuncioRepository.UsuarioPublicoProjection::getUsuarioId)
                .orElseThrow(() -> notFound("anunciante nao encontrada"));
    }

    private String normalizarBusca(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
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
        return PageRequest.of(pagina, tamanho);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
