package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioRelacionadoPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;
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
    private final MidiaPublicaSeguraPolicy midiaSeguraPolicy;
    private final SeoPublicoConsultaService seoService;
    private final ComplianceVisitorAccessService visitorAccessService;
    private final PremiumPublicoMapper premiumMapper;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final PoliticaContatoPublicoService contatoService;
    private final AnuncioSeoIndexabilidadePolicy indexabilidadePolicy;
    private final IdadeAnunciantePublicaService idadeAnuncianteService;
    private final VisualizacaoTotalCanonicaService visualizacaoService;

    public AnuncioPublicoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            AnuncioPublicoMapper anuncioMapper,
            MidiaPublicaMapper midiaMapper,
            MidiaPublicaSeguraPolicy midiaSeguraPolicy,
            SeoPublicoConsultaService seoService,
            ComplianceVisitorAccessService visitorAccessService,
            PremiumPublicoMapper premiumMapper,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            PoliticaContatoPublicoService contatoService,
            AnuncioSeoIndexabilidadePolicy indexabilidadePolicy,
            IdadeAnunciantePublicaService idadeAnuncianteService,
            VisualizacaoTotalCanonicaService visualizacaoService) {
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.anuncioMapper = anuncioMapper;
        this.midiaMapper = midiaMapper;
        this.midiaSeguraPolicy = midiaSeguraPolicy;
        this.seoService = seoService;
        this.visitorAccessService = visitorAccessService;
        this.premiumMapper = premiumMapper;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.contatoService = contatoService;
        this.indexabilidadePolicy = indexabilidadePolicy;
        this.idadeAnuncianteService = idadeAnuncianteService;
        this.visualizacaoService = visualizacaoService;
    }

    @Transactional(readOnly = true)
    public AnuncioDetalhePublicoDto buscarPorSlug(String slug) {
        return buscarPorSlug(slug, false);
    }

    @Transactional(readOnly = true)
    public AnuncioDetalhePublicoDto buscarPorSlug(String slug, HttpServletRequest request) {
        return buscarPorSlug(
                slug,
                visitorAccessService.autorizado(
                        request,
                        EscopoConteudoVisitante.MIDIA_RESTRITA));
    }

    private AnuncioDetalhePublicoDto buscarPorSlug(String slug, boolean idadeConfirmada) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        AnuncioEntity anuncio = anuncioRepository
                .findPublicoComProprietarioAtivoPorSlug(slugSeguro)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

        AnuncioLocalizacaoEntity localizacaoEntity = localizacaoRepository.findByAnuncioId(anuncio.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "localizacao publica nao encontrada"));
        LocalizacaoPublicaDto localizacao = toLocalizacao(localizacaoEntity);
        PremiumPublicoFlagsDto premium = premiumMapper.flags(anuncio);
        List<MidiaPublicaDto> midias = midias(anuncio.getId(), idadeConfirmada, premium);
        List<MidiaPublicaDto> midiasSeo = idadeConfirmada
                ? midias(anuncio.getId(), false, premium)
                : midias;
        boolean indexavel = indexabilidadePolicy.indexavel(anuncio, localizacao, midiasSeo);
        var primeiraPublicacao = anuncioRepository
                .findPrimeiraPublicacaoByUsuarioIdIn(List.of(anuncio.getUsuarioId()))
                .stream()
                .findFirst()
                .map(AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection::getPrimeiraPublicacaoEm)
                .orElse(null);
        var idadeAnunciante = idadeAnuncianteService.resolver(anuncio.getUsuarioId(), premium.idadeOculta());

        return anuncioMapper.toDetalhe(
                anuncio,
                localizacao,
                midias,
                seoService.paraAnuncio(slugSeguro, indexavel),
                premium,
                contatoService.podeExporContato(anuncio),
                primeiraPublicacao,
                idadeAnunciante.username(),
                idadeAnunciante.idade(),
                idadeAnunciante.idadeOculta(),
                visualizacaoService.calcular(anuncio.getId()),
                relacionados(anuncio, localizacaoEntity));
    }

    private List<AnuncioRelacionadoPublicoDto> relacionados(
            AnuncioEntity anuncioAtual,
            AnuncioLocalizacaoEntity localizacaoAtual) {
        if (anuncioAtual.getCategoria() == null || localizacaoAtual.getCidadeId() == null) {
            return List.of();
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        var limite = PageRequest.of(0, 6);
        List<AnuncioEntity> candidatos = anuncioRepository.findRelacionadosComBeneficioVigente(
                anuncioAtual.getId(),
                anuncioAtual.getCategoria(),
                localizacaoAtual.getCidadeId(),
                true,
                agora,
                limite);
        if (candidatos.isEmpty()) {
            candidatos = anuncioRepository.findRelacionadosComBeneficioVigente(
                    anuncioAtual.getId(),
                    anuncioAtual.getCategoria(),
                    localizacaoAtual.getCidadeId(),
                    false,
                    agora,
                    limite);
        }
        return mapearRelacionados(candidatos);
    }

    private List<AnuncioRelacionadoPublicoDto> mapearRelacionados(List<AnuncioEntity> candidatos) {
        if (candidatos == null || candidatos.isEmpty()) {
            return List.of();
        }
        List<UUID> anuncioIds = candidatos.stream().map(AnuncioEntity::getId).toList();
        Map<UUID, AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findByAnuncioIdIn(anuncioIds)
                .stream()
                .collect(Collectors.toMap(
                        AnuncioLocalizacaoEntity::getAnuncioId,
                        Function.identity(),
                        (primeiro, ignorado) -> primeiro));
        Map<UUID, br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity> cidades = cidadeRepository
                .findAllById(localizacoes.values().stream()
                        .map(AnuncioLocalizacaoEntity::getCidadeId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity::getId,
                        Function.identity()));
        Map<UUID, br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity> estados = estadoRepository
                .findAllById(cidades.values().stream()
                        .map(br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity::getEstadoId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity::getId,
                        Function.identity()));
        Map<UUID, PremiumPublicoFlagsDto> premium = premiumMapper.flagsPorAnuncios(candidatos);
        Map<UUID, List<MidiaPublicaDto>> midias = midiasPorAnuncios(anuncioIds, premium);
        Map<UUID, IdadeAnunciantePublicaService.Resultado> idades =
                idadeAnuncianteService.resolverPorAnuncios(candidatos, premium);

        return candidatos.stream()
                .map(anuncio -> {
                    AnuncioLocalizacaoEntity localizacao = localizacoes.get(anuncio.getId());
                    var cidade = localizacao == null ? null : cidades.get(localizacao.getCidadeId());
                    var estado = cidade == null ? null : estados.get(cidade.getEstadoId());
                    if (cidade == null || estado == null) {
                        return null;
                    }
                    var idade = idades.get(anuncio.getId());
                    return new AnuncioRelacionadoPublicoDto(
                            anuncio.getId(),
                            anuncio.getSlug(),
                            anuncio.getTitulo(),
                            idade == null ? null : idade.idade(),
                            anuncio.getPreco(),
                            cidade.getNome(),
                            estado.getUf().trim(),
                            midiaSeguraPolicy.paraCard(
                                    midias.getOrDefault(anuncio.getId(), List.of())));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    LocalizacaoPublicaDto localizacao(UUID anuncioId) {
        return localizacaoRepository.findByAnuncioId(anuncioId)
                .map(this::toLocalizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "localizacao publica nao encontrada"));
    }

    List<MidiaPublicaDto> midias(UUID anuncioId, PremiumPublicoFlagsDto premium) {
        return midias(anuncioId, false, premium);
    }

    List<MidiaPublicaDto> midiasParaStory(
            AnuncioEntity anuncio,
            boolean idadeConfirmada) {
        if (!idadeConfirmada || anuncio == null || anuncio.getId() == null) {
            return List.of();
        }
        return midias(anuncio.getId(), true, premiumMapper.flags(anuncio));
    }

    Map<UUID, List<MidiaPublicaDto>> midiasPorAnuncios(
            List<UUID> anuncioIds,
            Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio) {
        return midiasPorAnuncios(anuncioIds, premiumPorAnuncio, false);
    }

    Map<UUID, List<MidiaPublicaDto>> midiasParaCardsPorAnuncios(
            List<UUID> anuncioIds,
            Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio) {
        return midiasPorAnuncios(anuncioIds, premiumPorAnuncio, true);
    }

    private Map<UUID, List<MidiaPublicaDto>> midiasPorAnuncios(
            List<UUID> anuncioIds,
            Map<UUID, PremiumPublicoFlagsDto> premiumPorAnuncio,
            boolean paraCards) {
        if (anuncioIds == null || anuncioIds.isEmpty()) {
            return Map.of();
        }
        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds);
        List<UUID> arquivoIds = vinculos.stream()
                .map(AnuncioMidiaEntity::getArquivoMidiaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio = vinculos.stream()
                .collect(Collectors.groupingBy(AnuncioMidiaEntity::getAnuncioId));
        return anuncioIds.stream().collect(Collectors.toMap(
                Function.identity(),
                anuncioId -> {
                    PremiumPublicoFlagsDto premium = premiumPorAnuncio == null
                            ? PremiumPublicoFlagsDto.vazio()
                            : premiumPorAnuncio.getOrDefault(anuncioId, PremiumPublicoFlagsDto.vazio());
                    int maxFotos = premium.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
                    if (paraCards) {
                        return midiaMapper.publicasParaCard(
                                vinculosPorAnuncio.getOrDefault(anuncioId, List.of()), arquivos,
                                maxFotos, premium.videoAtivo(), premium.carrosselFotosAtivo(), midiaSeguraPolicy);
                    }
                    return midiaMapper.publicas(
                            vinculosPorAnuncio.getOrDefault(anuncioId, List.of()),
                            arquivos,
                            false,
                            maxFotos,
                            premium.videoAtivo());
                },
                (primeiro, ignorado) -> primeiro));
    }

    private List<MidiaPublicaDto> midias(
            UUID anuncioId,
            boolean idadeConfirmada,
            PremiumPublicoFlagsDto premium) {
        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioId(anuncioId);
        List<UUID> arquivoIds = vinculos.stream()
                .map(AnuncioMidiaEntity::getArquivoMidiaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        int maxFotos = premium.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
        return midiaMapper.publicas(
                vinculos,
                arquivos,
                idadeConfirmada,
                maxFotos,
                premium.videoAtivo());
    }

    private LocalizacaoPublicaDto toLocalizacao(AnuncioLocalizacaoEntity localizacao) {
        if (localizacao.getEstadoId() == null || localizacao.getCidadeId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "localizacao publica incompleta");
        }
        var estado = estadoRepository.findById(localizacao.getEstadoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "estado do anuncio nao encontrado"));
        var cidade = cidadeRepository.findById(localizacao.getCidadeId())
                .filter(item -> estado.getId().equals(item.getEstadoId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cidade do anuncio nao encontrada"));
        var bairro = localizacao.getBairroId() == null
                ? null
                : bairroRepository.findById(localizacao.getBairroId())
                        .filter(item -> cidade.getId().equals(item.getCidadeId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bairro do anuncio nao encontrado"));
        return new LocalizacaoPublicaDto(
                estado.getUf(),
                estado.getNome(),
                cidade.getNome(),
                cidade.getSlug(),
                bairro == null ? null : bairro.getNome(),
                bairro == null ? null : bairro.getSlug(),
                localizacao.getEnderecoResumido());
    }
}
