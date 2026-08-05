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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoryFeedPublicoService {

    private static final String PREFIXO_ADMIN = "administrativo:";
    private static final String IDADE_NAO_CONFIRMADA = "IDADE_NAO_CONFIRMADA";

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
    private final StoryAnuncioApresentacaoService apresentacaoService;

    @Autowired
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
            MidiaPublicaUrlService urlService,
            StoryAnuncioApresentacaoService apresentacaoService) {
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
        this.apresentacaoService = apresentacaoService;
    }

    StoryFeedPublicoService(
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
        this(
                selecaoRepository, storyRepository, anuncioMidiaRepository,
                arquivoMidiaRepository, anuncioRepository, usuarioRepository,
                elegibilidadeService, visitorAccessService, idadeAnuncianteService,
                premiumMapper, urlService, null);
    }

    @Transactional(readOnly = true)
    public List<StoryFeedBundleDto> listar(HttpServletRequest request) {
        boolean idadeConfirmada = visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY);
        List<UsuarioStory> usuarios = carregarStoriesUsuario();
        Map<UUID, IdadeAnunciantePublicaService.Resultado> idades =
                idadesPorAnuncio(usuarios.stream().map(UsuarioStory::anuncio)
                        .filter(java.util.Objects::nonNull).distinct().toList());
        Set<UUID> arquivosEmStoriesPagos = usuarios.stream()
                .map(item -> item.arquivo() == null ? null : item.arquivo().getId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<StoryFeedBundleDto> resposta = new ArrayList<>(bundlesUsuario(usuarios, idadeConfirmada, idades));
        resposta.addAll(bundlesAdministrativos(idadeConfirmada, arquivosEmStoriesPagos));
        return semDuplicidades(resposta);
    }

    @Transactional(readOnly = true)
    public StoryViewerPublicoDto buscar(String storyId, HttpServletRequest request) {
        if (storyId != null && storyId.startsWith(PREFIXO_ADMIN)) {
            return buscarAdministrativo(storyId, request);
        }
        return buscarUsuario(storyId, request);
    }

    private List<StoryFeedBundleDto> bundlesAdministrativos(
            boolean idadeConfirmada,
            Set<UUID> arquivosEmStoriesPagos) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<StorySelecaoAdministrativaEntity> selecoes =
                selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc().stream()
                .filter(selecao -> storyAdminAtivo(selecao, agora))
                .toList();
        if (selecoes.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnuncioEntity> anuncios = anunciosPublicaveisComProprietarioAtivo(
                anuncioRepository.findAllById(selecoes.stream()
                        .map(StorySelecaoAdministrativaEntity::getAnuncioId)
                        .distinct()
                        .toList()));
        Map<UUID, IdadeAnunciantePublicaService.Resultado> idades =
                idadesPorAnuncio(List.copyOf(anuncios.values()));
        Map<UUID, List<MidiaElegivel>> midias =
                elegibilidadeService.listarPorAnuncios(anuncios.keySet());
        return selecoes.stream()
                .map(selecao -> bundleAdministrativo(
                        selecao,
                        anuncios,
                        midias,
                        idades,
                        idadeConfirmada,
                        arquivosEmStoriesPagos))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private StoryFeedBundleDto bundleAdministrativo(
            StorySelecaoAdministrativaEntity selecao,
            Map<UUID, AnuncioEntity> anuncios,
            Map<UUID, List<MidiaElegivel>> midias,
            Map<UUID, IdadeAnunciantePublicaService.Resultado> idades,
            boolean idadeConfirmada,
            Set<UUID> arquivosEmStoriesPagos) {
        AnuncioEntity anuncio = anuncios.get(selecao.getAnuncioId());
        if (anuncio == null) {
            return null;
        }
        IdadeAnunciantePublicaService.Resultado idade = idades.get(anuncio.getId());
        List<StoryFeedItemDto> itens = midias.getOrDefault(anuncio.getId(), List.of()).stream()
                .filter(item -> !arquivosEmStoriesPagos.contains(item.vinculo().getArquivoMidiaId()))
                .map(item -> itemAdministrativo(
                        anuncio,
                        item,
                        idadeConfirmada,
                        idade,
                        expiraEm(selecao)))
                .toList();
        if (itens.isEmpty()) {
            return null;
        }
        return new StoryFeedBundleDto(
                PREFIXO_ADMIN + anuncio.getId(),
                null,
                anuncio.getTitulo(),
                itens.get(0).idade(),
                true,
                itens.get(0).previewUrl(),
                false,
                itens);
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
        Set<UUID> vinculoIds = stories.stream()
                .map(StoryAnuncioEntity::getAnuncioMidiaId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByIdIn(vinculoIds).stream()
                .collect(Collectors.toMap(AnuncioMidiaEntity::getId, Function.identity()));
        Set<UUID> arquivoIds = stories.stream()
                        .map(StoryAnuncioEntity::getArquivoMidiaId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        arquivoIds.addAll(vinculos.values().stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .filter(java.util.Objects::nonNull)
                        .toList());
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Set<UUID> anuncioIds = stories.stream()
                .map(StoryAnuncioEntity::getAnuncioId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        anuncioIds.addAll(vinculos.values().stream()
                .map(AnuncioMidiaEntity::getAnuncioId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, AnuncioEntity> anuncios = anuncioRepository.findAllById(anuncioIds).stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
        Set<UUID> proprietarioIds = stories.stream()
                .map(StoryAnuncioEntity::getCriadoPor)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        proprietarioIds.addAll(anuncios.values().stream()
                .map(AnuncioEntity::getUsuarioId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, UsuarioEntity> proprietarios = usuarioRepository.findAllById(proprietarioIds).stream()
                .filter(this::usuarioAtivo)
                .collect(Collectors.toMap(UsuarioEntity::getId, Function.identity()));
        return stories.stream()
                .map(story -> usuarioStory(story, vinculos, arquivos, anuncios, proprietarios))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private UsuarioStory usuarioStory(
            StoryAnuncioEntity story,
            Map<UUID, AnuncioMidiaEntity> vinculos,
            Map<UUID, ArquivoMidiaEntity> arquivos,
            Map<UUID, AnuncioEntity> anuncios,
            Map<UUID, UsuarioEntity> proprietarios) {
        if (story.getModoConteudo() == ModoConteudoStory.ANUNCIO) {
            AnuncioEntity anuncio = anuncios.get(story.getAnuncioId());
            UsuarioEntity proprietario = proprietarios.get(proprietarioId(story, anuncio));
            return anuncioPublicavel(anuncio) && proprietario != null
                    ? new UsuarioStory(story, null, null, anuncio, proprietario)
                    : null;
        }
        ArquivoMidiaEntity direto = arquivos.get(story.getArquivoMidiaId());
        if (story.getArquivoMidiaId() != null) {
            UsuarioEntity proprietario = proprietarios.get(story.getCriadoPor());
            return proprietario != null && arquivoStoryElegivel(direto)
                    ? new UsuarioStory(story, null, direto, null, proprietario)
                    : null;
        }
        AnuncioMidiaEntity vinculo = vinculos.get(story.getAnuncioMidiaId());
        if (!vinculoStoryUsuarioElegivel(vinculo)) {
            return null;
        }
        ArquivoMidiaEntity arquivo = arquivos.get(vinculo.getArquivoMidiaId());
        AnuncioEntity anuncio = anuncios.get(vinculo.getAnuncioId());
        UsuarioEntity proprietario = proprietarios.get(proprietarioId(story, anuncio));
        if (!arquivoStoryElegivel(arquivo)
                || anuncio == null
                || proprietario == null) {
            return null;
        }
        return new UsuarioStory(story, vinculo, arquivo, anuncio, proprietario);
    }

    private List<StoryFeedBundleDto> bundlesUsuario(
            List<UsuarioStory> stories,
            boolean idadeConfirmada,
            Map<UUID, IdadeAnunciantePublicaService.Resultado> idades) {
        Map<UUID, List<UsuarioStory>> porUsuario = new LinkedHashMap<>();
        stories.forEach(item -> porUsuario.computeIfAbsent(item.usuario().getId(), ignored -> new ArrayList<>()).add(item));
        return porUsuario.entrySet().stream().map(entry -> {
            List<UsuarioStory> itens = entry.getValue();
            UsuarioEntity usuario = itens.get(0).usuario();
            String username = identidadePublica(usuario);
            String displayUsername = nomeExibicao(usuario);
            List<StoryFeedItemDto> feed = itens.stream()
                    .map(item -> itemUsuario(
                            item.story(),
                            item.vinculo(),
                            item.arquivo(),
                            item.anuncio(),
                            item.usuario(),
                            idadeConfirmada,
                            item.anuncio() == null ? null : idades.get(item.anuncio().getId())))
                    .toList();
            return new StoryFeedBundleDto(
                    "story-owner:" + feed.get(0).storyId(),
                    idadeConfirmada ? username : null,
                    displayUsername,
                    feed.get(0).idade(),
                    idadeConfirmada && username != null,
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
        String url = idadeConfirmada
                ? previewPublica(item.vinculo(), item.arquivo())
                : null;
        return new StoryFeedItemDto(
                PREFIXO_ADMIN + item.vinculo().getId(),
                idadeConfirmada ? anuncio.getId().toString() : null,
                idadeConfirmada ? anuncio.getSlug() : null,
                null,
                idadeConfirmada ? anuncio.getTitulo() : null,
                idadeConfirmada && idade != null ? idade.idade() : null,
                idadeConfirmada,
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
            UsuarioEntity usuario,
            boolean idadeConfirmada,
            IdadeAnunciantePublicaService.Resultado idade) {
        if (story.getModoConteudo() == ModoConteudoStory.ANUNCIO) {
            return new StoryFeedItemDto(
                    story.getId().toString(),
                    idadeConfirmada ? anuncio.getId().toString() : null,
                    idadeConfirmada ? anuncio.getSlug() : null,
                    null,
                    idadeConfirmada ? anuncio.getTitulo() : null,
                    idadeConfirmada && idade != null ? idade.idade() : null,
                    idadeConfirmada,
                    idadeConfirmada ? "AVAILABLE" : IDADE_NAO_CONFIRMADA,
                    null,
                    ModoConteudoStory.ANUNCIO.name(),
                    "ANUNCIO",
                    story.getFimEm());
        }
        String url = idadeConfirmada
                ? storyMediaUrl(story)
                : null;
        String username = identidadePublica(usuario);
        String displayUsername = nomeExibicao(usuario);
        return new StoryFeedItemDto(
                story.getId().toString(),
                null,
                null,
                idadeConfirmada ? username : null,
                displayUsername,
                null,
                idadeConfirmada && username != null,
                previewState(idadeConfirmada, url),
                url,
                story.getModoConteudoEfetivo().name(),
                tipoPublico(arquivo),
                story.getFimEm());
    }

    private StoryViewerPublicoDto buscarAdministrativo(String storyId, HttpServletRequest request) {
        UUID vinculoId = uuidSeguro(storyId.substring(PREFIXO_ADMIN.length()));
        AnuncioMidiaEntity vinculo = anuncioMidiaRepository.findById(vinculoId).orElse(null);
        StorySelecaoAdministrativaEntity selecao = vinculo == null
                ? null
                : selecaoAtivaDoAnuncio(vinculo.getAnuncioId(), OffsetDateTime.now(ZoneOffset.UTC));
        AnuncioEntity anuncio = selecao == null ? null : anuncioPublicavel(selecao.getAnuncioId());
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
        if (story.getModoConteudo() == ModoConteudoStory.ANUNCIO) {
            AnuncioEntity anuncio = anuncioRepository.findById(story.getAnuncioId())
                    .filter(this::anuncioComProprietarioAtivo)
                    .filter(item -> story.getCriadoPor() == null
                            || story.getCriadoPor().equals(item.getUsuarioId()))
                    .orElseThrow(this::naoEncontrado);
            return viewerAnuncio(story, anuncio, request);
        }
        if (story.getArquivoMidiaId() != null) {
            UsuarioEntity usuario = usuarioRepository.findById(story.getCriadoPor())
                    .filter(this::usuarioAtivo)
                    .orElseThrow(this::naoEncontrado);
            ArquivoMidiaEntity arquivo = arquivoMidiaRepository.findById(story.getArquivoMidiaId())
                    .filter(this::arquivoStoryElegivel)
                    .orElseThrow(this::naoEncontrado);
            return viewerMidiaIndependente(story, usuario, arquivo, request);
        }
        AnuncioMidiaEntity vinculo = anuncioMidiaRepository.findById(story.getAnuncioMidiaId())
                .filter(this::vinculoStoryUsuarioElegivel)
                .orElseThrow(this::naoEncontrado);
        ArquivoMidiaEntity arquivo = arquivoMidiaRepository.findById(vinculo.getArquivoMidiaId())
                .filter(this::arquivoStoryElegivel)
                .orElseThrow(this::naoEncontrado);
        AnuncioEntity anuncio = anuncioRepository.findById(vinculo.getAnuncioId())
                .orElseThrow(this::naoEncontrado);
        UUID proprietarioId = proprietarioId(story, anuncio);
        UsuarioEntity usuario = usuarioRepository.findById(proprietarioId)
                .filter(this::usuarioAtivo)
                .orElseThrow(this::naoEncontrado);
        return viewerMidiaIndependente(story, usuario, arquivo, request);
    }

    private StoryViewerPublicoDto viewerMidiaIndependente(
            StoryAnuncioEntity story,
            UsuarioEntity usuario,
            ArquivoMidiaEntity arquivo,
            HttpServletRequest request) {
        boolean idadeConfirmada = visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY);
        String username = identidadePublica(usuario);
        String displayUsername = nomeExibicao(usuario);
        String url = idadeConfirmada ? storyMediaUrl(story) : null;
        String state = !idadeConfirmada
                ? IDADE_NAO_CONFIRMADA
                : url == null ? "INDISPONIVEL" : "LIBERADO";
        return new StoryViewerPublicoDto(
                story.getId().toString(),
                null,
                null,
                idadeConfirmada ? username : null,
                displayUsername,
                null,
                idadeConfirmada && username != null,
                state,
                url,
                ModoConteudoStory.MIDIA_UPLOAD.name(),
                tipoPublico(arquivo),
                story.getFimEm(),
                idadeConfirmada ? null : IDADE_NAO_CONFIRMADA,
                null,
                null,
                null,
                null);
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
                : null;
        String state = !idadeConfirmada ? IDADE_NAO_CONFIRMADA : url == null ? "INDISPONIVEL" : "LIBERADO";
        IdadeAnunciantePublicaService.Resultado idade = idadeAnuncio(anuncio);
        return new StoryViewerPublicoDto(
                storyId,
                idadeConfirmada ? anuncio.getId().toString() : null,
                idadeConfirmada ? anuncio.getSlug() : null,
                null,
                idadeConfirmada ? anuncio.getTitulo() : null,
                idadeConfirmada ? idade.idade() : null,
                idadeConfirmada,
                state,
                url,
                ModoConteudoStory.MIDIA_UPLOAD.name(),
                tipoPublico(vinculo, arquivo),
                expiraEm,
                idadeConfirmada ? null : IDADE_NAO_CONFIRMADA,
                null,
                null,
                null,
                null);
    }

    private StoryViewerPublicoDto viewerAnuncio(
            StoryAnuncioEntity story,
            AnuncioEntity anuncio,
            HttpServletRequest request) {
        boolean idadeConfirmada = visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY);
        IdadeAnunciantePublicaService.Resultado idade = idadeAnuncio(anuncio);
        StoryAnuncioApresentacaoService.Apresentacao apresentacao = idadeConfirmada
                && apresentacaoService != null
                ? apresentacaoService.apresentar(anuncio)
                : null;
        return new StoryViewerPublicoDto(
                story.getId().toString(),
                idadeConfirmada ? anuncio.getId().toString() : null,
                idadeConfirmada ? anuncio.getSlug() : null,
                null,
                idadeConfirmada ? anuncio.getTitulo() : null,
                idadeConfirmada ? idade.idade() : null,
                idadeConfirmada,
                idadeConfirmada ? "LIBERADO" : IDADE_NAO_CONFIRMADA,
                null,
                ModoConteudoStory.ANUNCIO.name(),
                "ANUNCIO",
                story.getFimEm(),
                idadeConfirmada ? null : IDADE_NAO_CONFIRMADA,
                apresentacao == null ? null : apresentacao.cidade(),
                apresentacao == null ? null : apresentacao.uf(),
                apresentacao == null ? null : apresentacao.preco(),
                apresentacao == null ? null : apresentacao.resumo());
    }

    private boolean arquivoPossuiStoryPago(UUID arquivoMidiaId) {
        List<UUID> vinculos = anuncioMidiaRepository.findByArquivoMidiaId(arquivoMidiaId).stream()
                .map(AnuncioMidiaEntity::getId)
                .toList();
        if (vinculos.isEmpty()) {
            return false;
        }
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
        return selecao == null ? null : selecao.getExpiraEm();
    }

    private StorySelecaoAdministrativaEntity selecaoAtivaDoAnuncio(
            UUID anuncioId,
            OffsetDateTime agora) {
        return selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc().stream()
                .filter(item -> anuncioId.equals(item.getAnuncioId()))
                .filter(item -> storyAdminAtivo(item, agora))
                .findFirst()
                .orElse(null);
    }

    private List<StoryFeedBundleDto> semDuplicidades(List<StoryFeedBundleDto> bundles) {
        Set<String> storyIds = new LinkedHashSet<>();
        List<StoryFeedBundleDto> resposta = new ArrayList<>();
        for (StoryFeedBundleDto bundle : bundles) {
            List<StoryFeedItemDto> itens = bundle.itens().stream()
                    .filter(item -> item.storyId() != null && storyIds.add(item.storyId()))
                    .toList();
            if (itens.isEmpty()) {
                continue;
            }
            resposta.add(new StoryFeedBundleDto(
                    bundle.bundleKey(),
                    bundle.usuarioUsername(),
                    bundle.displayUsername(),
                    bundle.idade(),
                    bundle.profileNavigable(),
                    bundle.avatarUrl(),
                    false,
                    itens));
        }
        return List.copyOf(resposta);
    }

    private AnuncioEntity anuncioPublicavel(UUID id) {
        return id == null ? null : anuncioRepository.findById(id)
                .filter(this::anuncioComProprietarioAtivo)
                .orElse(null);
    }

    private boolean anuncioPublicavel(AnuncioEntity anuncio) {
        return anuncio != null
                && anuncio.getRemovidoEm() == null
                && anuncio.getStatus() == StatusAnuncio.PUBLICADO
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO;
    }

    private boolean anuncioComProprietarioAtivo(AnuncioEntity anuncio) {
        return anuncioPublicavel(anuncio)
                && usuarioRepository.findById(anuncio.getUsuarioId())
                        .filter(this::usuarioAtivo)
                        .isPresent();
    }

    private Map<UUID, AnuncioEntity> anunciosPublicaveisComProprietarioAtivo(
            Iterable<AnuncioEntity> candidatos) {
        List<AnuncioEntity> publicaveis = new ArrayList<>();
        candidatos.forEach(anuncio -> {
            if (anuncioPublicavel(anuncio)) publicaveis.add(anuncio);
        });
        Set<UUID> proprietariosAtivos = usuarioRepository.findAllById(publicaveis.stream()
                        .map(AnuncioEntity::getUsuarioId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .filter(this::usuarioAtivo)
                .map(UsuarioEntity::getId)
                .collect(Collectors.toSet());
        return publicaveis.stream()
                .filter(anuncio -> proprietariosAtivos.contains(anuncio.getUsuarioId()))
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
    }

    private boolean usuarioAtivo(UsuarioEntity usuario) {
        return usuario != null
                && usuario.getStatus() == StatusUsuario.ATIVO
                && usuario.getDesativadoEm() == null
                && usuario.getExcluidoEm() == null;
    }

    private boolean arquivoStoryElegivel(ArquivoMidiaEntity arquivo) {
        return arquivo != null
                && arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO;
    }

    private String previewState(boolean idadeConfirmada, String url) {
        if (!idadeConfirmada) return IDADE_NAO_CONFIRMADA;
        return url == null ? "UNAVAILABLE" : "AVAILABLE";
    }

    private Map<UUID, IdadeAnunciantePublicaService.Resultado> idadesPorAnuncio(
            List<AnuncioEntity> anuncios) {
        if (anuncios.isEmpty()) return Map.of();
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

    private String tipoPublico(ArquivoMidiaEntity arquivo) {
        return arquivo != null && arquivo.getMimeType() != null
                        && arquivo.getMimeType().toLowerCase().startsWith("video/")
                ? "VIDEO"
                : "IMAGE";
    }

    private String storyMediaUrl(StoryAnuncioEntity story) {
        return "/api/public/compliance/visitor/media/stories/" + story.getId();
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

    private String identidadePublica(UsuarioEntity usuario) {
        if (usuario == null || usuario.getId() == null) {
            return null;
        }
        try {
            byte[] namespace = ("topsv3-public-user-v1:" + usuario.getId())
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5").digest(namespace));
        } catch (Exception exception) {
            throw new IllegalStateException("identificador publico indisponivel", exception);
        }
    }

    private String nomeExibicao(UsuarioEntity usuario) {
        String nome = usuario == null ? null : usuario.getNome();
        return nome == null || nome.isBlank() ? null : nome.trim();
    }

    private UUID proprietarioId(StoryAnuncioEntity story, AnuncioEntity anuncio) {
        return story.getCriadoPor() != null
                ? story.getCriadoPor()
                : anuncio == null ? null : anuncio.getUsuarioId();
    }

    private record UsuarioStory(
            StoryAnuncioEntity story,
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo,
            AnuncioEntity anuncio,
            UsuarioEntity usuario) {
    }
}
