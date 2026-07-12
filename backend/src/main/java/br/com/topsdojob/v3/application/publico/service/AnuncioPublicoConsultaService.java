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
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
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
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final PoliticaContatoPublicoService contatoService;
    private final AnuncioSeoIndexabilidadePolicy indexabilidadePolicy;
    private final IdadeAnunciantePublicaService idadeAnuncianteService;

    public AnuncioPublicoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            AnuncioPublicoMapper anuncioMapper,
            MidiaPublicaMapper midiaMapper,
            SeoPublicoConsultaService seoService,
            IdadePublicaService idadeService,
            PremiumPublicoMapper premiumMapper,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            PoliticaContatoPublicoService contatoService,
            AnuncioSeoIndexabilidadePolicy indexabilidadePolicy,
            IdadeAnunciantePublicaService idadeAnuncianteService) {
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.anuncioMapper = anuncioMapper;
        this.midiaMapper = midiaMapper;
        this.seoService = seoService;
        this.idadeService = idadeService;
        this.premiumMapper = premiumMapper;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.contatoService = contatoService;
        this.indexabilidadePolicy = indexabilidadePolicy;
        this.idadeAnuncianteService = idadeAnuncianteService;
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

        LocalizacaoPublicaDto localizacao = localizacao(anuncio.getId());
        List<MidiaPublicaDto> midias = midias(anuncio.getId(), idadeConfirmada);
        List<MidiaPublicaDto> midiasSeo = idadeConfirmada ? midias(anuncio.getId(), false) : midias;
        boolean indexavel = indexabilidadePolicy.indexavel(anuncio, localizacao, midiasSeo);
        var primeiraPublicacao = anuncioRepository
                .findPrimeiraPublicacaoByUsuarioIdIn(List.of(anuncio.getUsuarioId()))
                .stream()
                .findFirst()
                .map(AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection::getPrimeiraPublicacaoEm)
                .orElse(null);
        var premium = premiumMapper.flags(anuncio);
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
                idadeAnunciante.idadeOculta());
    }

    LocalizacaoPublicaDto localizacao(UUID anuncioId) {
        return localizacaoRepository.findByAnuncioId(anuncioId)
                .map(this::toLocalizacao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "localizacao publica nao encontrada"));
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
