package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.StoryFeedBundleDto;
import br.com.topsdojob.v3.application.publico.dto.StoryFeedItemDto;
import br.com.topsdojob.v3.application.publico.dto.StoryViewerPublicoDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService.MidiaElegivel;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoryFeedPublicoService {

    private static final String PREFIXO_ADMIN = "administrativo:";
    private static final String IDADE_NAO_CONFIRMADA = "IDADE_NAO_CONFIRMADA";
    private static final Duration DURACAO_STORY_ADMIN = Duration.ofHours(24);

    private final StorySelecaoAdministrativaRepository selecaoRepository;
    private final StoryAnuncioRepository storyRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final AnuncioRepository anuncioRepository;
    private final UsuarioRepository usuarioRepository;
    private final StoryMidiaElegibilidadeService elegibilidadeService;
    private final ComplianceVisitorAccessService visitorAccessService;
    private final IdadeAnunciantePublicaService idadeAnuncianteService;
    private final PremiumPublicoMapper premiumMapper;
    private final MidiaPublicaUrlService urlService;

    public StoryFeedPublicoService(
            StorySelecaoAdministrativaRepository selecaoRepository,
            StoryAnuncioRepository storyRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            StoryMidiaElegibilidadeService elegibilidadeService,
            ComplianceVisitorAccessService visitorAccessService,
            IdadeAnunciantePublicaService idadeAnuncianteService,
            PremiumPublicoMapper premiumMapper,
            MidiaPublicaUrlService urlService) {
        this.selecaoRepository = selecaoRepository;
        this.storyRepository = storyRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.elegibilidadeService = elegibilidadeService;
        this.visitorAccessService = visitorAccessService;
        this.idadeAnuncianteService = idadeAnuncianteService;
        this.premiumMapper = premiumMapper;
        this.urlService = urlService;
    }

    @Transactional(readOnly = true)
    public List<StoryFeedBundleDto> listar(HttpServletRequest request) {
        boolean idadeConfirmada = visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY);
        List<UsuarioStory> usuarios = carregarStoriesUsuario();
        Map<UUID, IdadeAnunciantePublicaService.Resultado> idades =
                idadesPorAnuncio(usuarios.stream().map(UsuarioStory::anuncio).distinct().toList());
        Set<UUID> arquivosEmStoriesPagos = usuarios.stream()
                .map(UsuarioStory::vinculo)
                .map(AnuncioMidiaEntity::getArquivoMidiaId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<StoryFeedBundleDto> resposta = new ArrayList<>(bundlesUsuario(usuarios, idadeConfirmada, idades));
        bundleAdministrativo(idadeConfirmada, arquivosEmStoriesPagos).ifPresent(admin ->
                resposta.add(ThreadLocalRandom.current().nextInt(resposta.size() + 1), admin));
        return List.copyOf(resposta);
    }

    @Transactional(readOnly = true)
    public StoryViewerPublicoDto buscar(String storyId, HttpServletRequest request) {
        if (storyId != null && storyId.startsWith(PREFIXO_ADMIN)) {
            return buscarAdministrativo(storyId, request);
        }
        return buscarUsuario(storyId, request);
    }

    private java.util.Optional<StoryFeedBundleDto> bundleAdministrativo(
            boolean idadeConfirmada,
            Set<UUID> arquivosEmStoriesPagos) {
        StorySelecaoAdministrativaEntity selecao = selecaoRepository.atual().orElse(null);
        if (!storyAdminAtivo(selecao, OffsetDateTime.now(ZoneOffset.UTC))) {
            return java.util.Optional.empty();
        }
        AnuncioEntity anuncio = anuncioPublicavel(selecao.getAnuncioId());
        if (anuncio == null) {
            return java.util.Optional.empty();
        }
        IdadeAnunciantePublicaService.Resultado idade = idadeAnuncio(anuncio);
        List<StoryFeedItemDto> itens = elegibilidadeService.listar(anuncio.getId()).stream()
                .filter(item -> !arquivosEmStoriesPagos.contains(item.vinculo().getArquivoMidiaId()))
                .map(item -> itemAdministrativo(
                        anuncio,
                        item,
                        idadeConfirmada,
                        idade,
                        expiraEm(selecao)))
                .toList();
        if (itens.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new StoryFeedBundleDto(
                PREFIXO_ADMIN + anuncio.getId(),
                null,
                anuncio.getTitulo(),
                itens.get(0).idade(),
                true,
                itens.get(0).previewUrl(),
                false,
                itens));
    }

    private List<UsuarioStory> carregarStoriesUsuario() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<StoryAnuncioEntity> stories = storyRepository
                .findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO).stream()
                .filter(story -> janelaValida(story, agora))
                .toList();
        if (stories.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByIdIn(stories.stream()
                        .map(StoryAnuncioEntity::getAnuncioMidiaId)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(vinculos.values().stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, AnuncioEntity> anuncios = anuncioRepository.findAllById(vinculos.values().stream()
                        .map(AnuncioMidiaEntity::getAnuncioId)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        return stories.stream()
                .map(story -> usuarioStory(story, vinculos, arquivos, anuncios))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private UsuarioStory usuarioStory(
            StoryAnuncioEntity story,
            Map<UUID, AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivos,
            Map<UUID, AnuncioEntity> anuncios) {
        AnuncioMidiaEntity vinculo = vinculos.get(story.getAnuncioMidiaId());
        if (!vinculoStoryUsuarioElegivel(vinculo)) {
            return null;
        }
        ArquivoMidiaEntity arquivo = arquivos.get(vinculo.getArquivoMidiaId());
        AnuncioEntity anuncio = anuncios.get(vinculo.getAnuncioId());
        if (arquivo == null || arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO || !anuncioPublicavel(anuncio)) {
            return null;
        }
        return new UsuarioStory(story, vinculo, arquivo, anuncio);
    }

    private List<StoryFeedBundleDto> bundlesUsuario(
            List<UsuarioStory> stories,
            boolean idadeConfirmada,
            Map<UUID, IdadeAnunciantePublicaService.Resultado> idades) {
        Map<UUID, List<UsuarioStory>> porUsuario = new LinkedHashMap<>();
        stories.forEach(item -> porUsuario.computeIfAbsent(item.anuncio().getUsuarioId(), ignored -> new ArrayList<>()).add(item));
        Map<UUID, UsuarioEntity> usuarios = usuarioRepository.findAllById(porUsuario.keySet()).stream()
                .collect(Collectors.toMap(UsuarioEntity::getId, Function.identity()));
        return porUsuario.entrySet().stream().map(entry -> {
            List<UsuarioStory> itens = entry.getValue();
            UsuarioEntity usuario = usuarios.get(entry.getKey());
            String nome = usuario == null ? itens.get(0).anuncio().getTitulo() : usuario.getNome();
            List<StoryFeedItemDto> feed = itens.stream()
                    .map(item -> itemUsuario(
                            item.story(),
                            item.vinculo(),
                            item.arquivo(),
                            item.anuncio(),
                            idadeConfirmada,
                            idades.get(item.anuncio().getId())))
                    .toList();
            return new StoryFeedBundleDto(
                    entry.getKey().toString(),
                    null,
                    nome,
                    feed.get(0).idade(),
                    true,
                    feed.get(0).previewUrl(),
                    false,
                    feed);
        }).toList();
    }

    private StoryFeedItemDto itemAdministrativo(
            AnuncioEntity anuncio,
            MidiaElegivel item,
            boolean idadeConfirmada,
            IdadeAnunciantePublicaService.Resultado idade,
            OffsetDateTime expiraEm) {
        String url = previewPublica(item.vinculo(), item.arquivo());
        return new StoryFeedItemDto(
                PREFIXO_ADMIN + item.vinculo().getId(),
                anuncio.getId().toString(),
                anuncio.getSlug(),
                null,
                anuncio.getTitulo(),
                idade == null ? null : idade.idade(),
                true,
                previewState(idadeConfirmada, url),
                url,
                tipoPublico(item.vinculo(), item.arquivo()),
                expiraEm);
    }

    private StoryFeedItemDto itemUsuario(
            StoryAnuncioEntity story,
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo,
            AnuncioEntity anuncio,
            boolean idadeConfirmada,
            IdadeAnunciantePublicaService.Resultado idade) {
        String url = previewPublica(vinculo, arquivo);
        return new StoryFeedItemDto(
                story.getId().toString(),
                anuncio.getId().toString(),
                anuncio.getSlug(),
                null,
                anuncio.getTitulo(),
                idade == null ? null : idade.idade(),
                true,
                previewState(idadeConfirmada, url),
                url,
                tipoPublico(vinculo, arquivo),
                story.getFimEm());
    }

    private StoryViewerPublicoDto buscarAdministrativo(String storyId, HttpServletRequest request) {
        UUID vinculoId = uuidSeguro(storyId.substring(PREFIXO_ADMIN.length()));
        StorySelecaoAdministrativaEntity selecao = selecaoRepository.atual().orElse(null);
        if (!storyAdminAtivo(selecao, OffsetDateTime.now(ZoneOffset.UTC))) {
            throw naoEncontrado();
        }
        AnuncioEntity anuncio = anuncioPublicavel(selecao.getAnuncioId());
        MidiaElegivel item = anuncio == null ? null : elegibilidadeService.listar(anuncio.getId()).stream()
                .filter(candidato -> candidato.vinculo().getId().equals(vinculoId))
                .findFirst()
                .orElse(null);
        if (anuncio == null || item == null || arquivoPossuiStoryPago(item.vinculo().getArquivoMidiaId())) {
            throw naoEncontrado();
        }
        return viewer(
                storyId,
                anuncio,
                item.vinculo(),
                item.arquivo(),
                expiraEm(selecao),
                request);
    }

    private StoryViewerPublicoDto buscarUsuario(String storyId, HttpServletRequest request) {
        UUID id = uuidSeguro(storyId);
        StoryAnuncioEntity story = storyRepository.findByIdAndStatus(id, StatusStoryAnuncio.PUBLICADO)
                .filter(item -> janelaValida(item, OffsetDateTime.now(ZoneOffset.UTC)))
                .orElseThrow(this::naoEncontrado);
        AnuncioMidiaEntity vinculo = anuncioMidiaRepository.findById(story.getAnuncioMidiaId())
                .filter(this::vinculoStoryUsuarioElegivel)
                .orElseThrow(this::naoEncontrado);
        ArquivoMidiaEntity arquivo = arquivoMidiaRepository.findById(vinculo.getArquivoMidiaId())
                .filter(item -> item.getStatusArquivo() == StatusArquivoMidia.VALIDADO)
                .orElseThrow(this::naoEncontrado);
        AnuncioEntity anuncio = anuncioRepository.findById(vinculo.getAnuncioId())
                .filter(this::anuncioPublicavel)
                .orElseThrow(this::naoEncontrado);
        return viewer(storyId, anuncio, vinculo, arquivo, story.getFimEm(), request);
    }

    private StoryViewerPublicoDto viewer(
            String storyId,
            AnuncioEntity anuncio,
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo,
            OffsetDateTime expiraEm,
            HttpServletRequest request) {
        boolean idadeConfirmada = visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY);
        String url = idadeConfirmada
                ? urlService.resolver(vinculo, arquivo).urlPublica()
                : previewPublica(vinculo, arquivo);
        String state = !idadeConfirmada ? IDADE_NAO_CONFIRMADA : url == null ? "INDISPONIVEL" : "LIBERADO";
        IdadeAnunciantePublicaService.Resultado idade = idadeAnuncio(anuncio);
        return new StoryViewerPublicoDto(
                storyId,
                anuncio.getId().toString(),
                anuncio.getSlug(),
                null,
                anuncio.getTitulo(),
                idade.idade(),
                true,
                state,
                url,
                tipoPublico(vinculo, arquivo),
                expiraEm,
                idadeConfirmada ? null : IDADE_NAO_CONFIRMADA);
    }

    private boolean arquivoPossuiStoryPago(UUID arquivoMidiaId) {
        List<UUID> vinculos = anuncioMidiaRepository.findByArquivoMidiaId(arquivoMidiaId).stream()
                .map(AnuncioMidiaEntity::getId)
                .toList();
        return !storyRepository.findByAnuncioMidiaIdIn(vinculos).stream()
                .filter(story -> story.getStatus() == StatusStoryAnuncio.PUBLICADO)
                .filter(story -> janelaValida(story, OffsetDateTime.now(ZoneOffset.UTC)))
                .toList()
                .isEmpty();
    }

    private boolean vinculoStoryUsuarioElegivel(AnuncioMidiaEntity vinculo) {
        return vinculo != null
                && vinculo.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                && (vinculo.getTipo() == TipoAnuncioMidia.STORY || vinculo.getFinalidade() == FinalidadeAnuncioMidia.STORY)
                && vinculo.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18;
    }

    private boolean janelaValida(StoryAnuncioEntity story, OffsetDateTime agora) {
        return (story.getInicioEm() == null || !story.getInicioEm().isAfter(agora))
                && (story.getFimEm() == null || story.getFimEm().isAfter(agora));
    }

    private boolean storyAdminAtivo(StorySelecaoAdministrativaEntity selecao, OffsetDateTime agora) {
        OffsetDateTime expiraEm = expiraEm(selecao);
        return selecao != null && selecao.isAtiva() && expiraEm != null && expiraEm.isAfter(agora);
    }

    private OffsetDateTime expiraEm(StorySelecaoAdministrativaEntity selecao) {
        return selecao == null || selecao.getAtivadoEm() == null
                ? null
                : selecao.getAtivadoEm().plus(DURACAO_STORY_ADMIN);
    }

    private AnuncioEntity anuncioPublicavel(UUID id) {
        return id == null ? null : anuncioRepository.findById(id).filter(this::anuncioPublicavel).orElse(null);
    }

    private boolean anuncioPublicavel(AnuncioEntity anuncio) {
        return anuncio != null
                && anuncio.getRemovidoEm() == null
                && anuncio.getStatus() == StatusAnuncio.PUBLICADO
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO;
    }

    private String previewState(boolean idadeConfirmada, String url) {
        if (url == null) return "UNAVAILABLE";
        return idadeConfirmada ? "AVAILABLE" : IDADE_NAO_CONFIRMADA;
    }

    private Map<UUID, IdadeAnunciantePublicaService.Resultado> idadesPorAnuncio(
            List<AnuncioEntity> anuncios) {
        Map<UUID, PremiumPublicoFlagsDto> premium = premiumMapper.flagsPorAnuncios(anuncios);
        return idadeAnuncianteService.resolverPorAnuncios(anuncios, premium);
    }

    private IdadeAnunciantePublicaService.Resultado idadeAnuncio(AnuncioEntity anuncio) {
        PremiumPublicoFlagsDto premium = premiumMapper.flags(anuncio);
        return idadeAnuncianteService.resolver(anuncio.getUsuarioId(), premium.idadeOculta());
    }

    private String tipoPublico(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo) {
        return vinculo.getTipo() == TipoAnuncioMidia.VIDEO
                        || (arquivo.getMimeType() != null && arquivo.getMimeType().toLowerCase().startsWith("video/"))
                ? "VIDEO"
                : "IMAGE";
    }

    private String previewPublica(
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo) {
        if (!"IMAGE".equals(tipoPublico(vinculo, arquivo))) {
            return null;
        }
        MidiaPublicaUrlService.ResultadoUrlPublica resultado =
                vinculo.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18
                        ? urlService.resolverPreviewRestrita(arquivo)
                        : urlService.resolver(vinculo, arquivo);
        return resultado == null ? null : resultado.urlPublica();
    }

    private UUID uuidSeguro(String value) {
        try {
            return UUID.fromString(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw naoEncontrado();
        }
    }

    private ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "story nao encontrado");
    }

    private record UsuarioStory(
            StoryAnuncioEntity story,
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo,
            AnuncioEntity anuncio) {
    }
}
