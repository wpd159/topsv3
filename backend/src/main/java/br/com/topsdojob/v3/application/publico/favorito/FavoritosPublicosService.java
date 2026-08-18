package br.com.topsdojob.v3.application.publico.favorito;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.favorito.dto.FavoritoEstadoDto;
import br.com.topsdojob.v3.application.publico.favorito.dto.FavoritoPublicoDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.service.PoliticaContatoPublicoService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.FavoritoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.FavoritoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FavoritosPublicosService {

    private final MeusAnunciosConsultaService usuarioService;
    private final FavoritoAnuncioRepository favoritoRepository;
    private final FavoritoGravacaoService gravacaoService;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final MidiaPublicaMapper midiaMapper;
    private final MidiaPublicaSeguraPolicy midiaSeguraPolicy;
    private final PoliticaContatoPublicoService contatoService;

    public FavoritosPublicosService(
            MeusAnunciosConsultaService usuarioService,
            FavoritoAnuncioRepository favoritoRepository,
            FavoritoGravacaoService gravacaoService,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            MidiaPublicaMapper midiaMapper,
            MidiaPublicaSeguraPolicy midiaSeguraPolicy,
            PoliticaContatoPublicoService contatoService) {
        this.usuarioService = usuarioService;
        this.favoritoRepository = favoritoRepository;
        this.gravacaoService = gravacaoService;
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.midiaMapper = midiaMapper;
        this.midiaSeguraPolicy = midiaSeguraPolicy;
        this.contatoService = contatoService;
    }

    @Transactional(readOnly = true)
    public List<FavoritoPublicoDto> listar(Authentication authentication) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        List<FavoritoAnuncioEntity> favoritos = favoritoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
        if (favoritos.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = favoritos.stream().map(FavoritoAnuncioEntity::getAnuncioId).toList();
        Map<UUID, AnuncioEntity> anuncios = anuncioRepository
                .findPublicosPublicadosComProprietarioAtivoPorIds(ids)
                .stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        if (anuncios.isEmpty()) {
            return List.of();
        }

        DadosCards dados = carregarDadosEmLote(anuncios.values());
        return favoritos.stream()
                .map(favorito -> {
                    AnuncioEntity anuncio = anuncios.get(favorito.getAnuncioId());
                    return anuncio == null ? null : toDto(anuncio, favorito.getCriadoEm(), dados);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public FavoritoEstadoDto incluir(String slug, Authentication authentication) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        AnuncioEntity anuncio = anuncioRepository
                .findPublicoPublicadoComProprietarioAtivoPorSlug(slugSeguro(slug))
                .orElseThrow(this::notFound);
        try {
            gravacaoService.incluirSeAusente(usuarioId, anuncio.getId(), OffsetDateTime.now());
        } catch (DataIntegrityViolationException conflitoConcorrente) {
            if (!favoritoRepository.existsByUsuarioIdAndAnuncioId(usuarioId, anuncio.getId())) {
                throw conflitoConcorrente;
            }
        }
        return new FavoritoEstadoDto(anuncio.getSlug(), true);
    }

    @Transactional
    public FavoritoEstadoDto remover(String slug, Authentication authentication) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        AnuncioEntity anuncio = anuncioRepository.findBySlugAndRemovidoEmIsNull(slugSeguro(slug))
                .orElseThrow(this::notFound);
        gravacaoService.removerSeExistente(usuarioId, anuncio.getId());
        return new FavoritoEstadoDto(anuncio.getSlug(), false);
    }

    private DadosCards carregarDadosEmLote(Collection<AnuncioEntity> anuncios) {
        List<UUID> anuncioIds = anuncios.stream().map(AnuncioEntity::getId).toList();
        Map<UUID, AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findByAnuncioIdIn(anuncioIds).stream()
                .collect(Collectors.toMap(AnuncioLocalizacaoEntity::getAnuncioId, Function.identity()));
        Map<UUID, EstadoEntity> estados = entidadesPorId(
                estadoRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getEstadoId)),
                EstadoEntity::getId);
        Map<UUID, CidadeEntity> cidades = entidadesPorId(
                cidadeRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getCidadeId)),
                CidadeEntity::getId);
        Map<UUID, BairroEntity> bairros = entidadesPorId(
                bairroRepository.findAllById(ids(localizacoes.values(), AnuncioLocalizacaoEntity::getBairroId)),
                BairroEntity::getId);

        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds);
        Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio = vinculos.stream()
                .collect(Collectors.groupingBy(AnuncioMidiaEntity::getAnuncioId));
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(vinculos.stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, OffsetDateTime> primeiraPublicacao = anuncioRepository
                .findPrimeiraPublicacaoByUsuarioIdIn(anuncios.stream()
                        .map(AnuncioEntity::getUsuarioId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection::getUsuarioId,
                        AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection::getPrimeiraPublicacaoEm));

        return new DadosCards(
                localizacoes,
                estados,
                cidades,
                bairros,
                vinculosPorAnuncio,
                arquivos,
                primeiraPublicacao);
    }

    private FavoritoPublicoDto toDto(AnuncioEntity anuncio, OffsetDateTime adicionadoEm, DadosCards dados) {
        AnuncioLocalizacaoEntity localizacao = dados.localizacoes().get(anuncio.getId());
        List<MidiaPublicaDto> midias = midiaSeguraPolicy.paraCard(midiaMapper.publicas(
                dados.vinculosPorAnuncio().getOrDefault(anuncio.getId(), List.of()),
                dados.arquivos(),
                false));
        return new FavoritoPublicoDto(
                anuncio.getId(),
                anuncio.getSlug(),
                anuncio.getTitulo(),
                resumo(anuncio.getDescricao()),
                anuncio.getPreco(),
                localizacao(localizacao, dados.estados(), dados.cidades(), dados.bairros()),
                midias,
                contatoService.podeExporContato(anuncio),
                anuncio.getLocaisAtendimento().contains(LocalAtendimentoAnuncio.MEU_LOCAL),
                anuncio.getServicos().contains(ServicoAnuncio.ANAL),
                dados.primeiraPublicacao().get(anuncio.getUsuarioId()),
                adicionadoEm);
    }

    private LocalizacaoPublicaDto localizacao(
            AnuncioLocalizacaoEntity localizacao,
            Map<UUID, EstadoEntity> estados,
            Map<UUID, CidadeEntity> cidades,
            Map<UUID, BairroEntity> bairros) {
        if (localizacao == null) {
            return new LocalizacaoPublicaDto(null, null, null, null, null, null, null);
        }
        EstadoEntity estado = estados.get(localizacao.getEstadoId());
        CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
        BairroEntity bairro = bairros.get(localizacao.getBairroId());
        return new LocalizacaoPublicaDto(
                estado == null ? null : estado.getUf(),
                estado == null ? null : estado.getNome(),
                cidade == null ? null : cidade.getNome(),
                cidade == null ? null : cidade.getSlug(),
                bairro == null ? null : bairro.getNome(),
                bairro == null ? null : bairro.getSlug(),
                localizacao.getEnderecoResumido());
    }

    private String slugSeguro(String slug) {
        if (slug == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug obrigatorio");
        }
        String normalizado = slug.trim().toLowerCase();
        if (!normalizado.matches("[a-z0-9][a-z0-9-]{1,120}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug invalido");
        }
        return normalizado;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado");
    }

    private String resumo(String descricao) {
        if (descricao == null || descricao.length() <= 160) {
            return descricao;
        }
        return descricao.substring(0, 157).trim() + "...";
    }

    private <T> List<UUID> ids(Collection<T> entidades, Function<T, UUID> extractor) {
        return entidades.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
    }

    private <T> Map<UUID, T> entidadesPorId(Iterable<T> entidades, Function<T, UUID> extractor) {
        Map<UUID, T> resultado = new LinkedHashMap<>();
        entidades.forEach(entidade -> resultado.put(extractor.apply(entidade), entidade));
        return resultado;
    }

    private record DadosCards(
            Map<UUID, AnuncioLocalizacaoEntity> localizacoes,
            Map<UUID, EstadoEntity> estados,
            Map<UUID, CidadeEntity> cidades,
            Map<UUID, BairroEntity> bairros,
            Map<UUID, List<AnuncioMidiaEntity>> vinculosPorAnuncio,
            Map<UUID, ArquivoMidiaEntity> arquivos,
            Map<UUID, OffsetDateTime> primeiraPublicacao) {
    }
}
