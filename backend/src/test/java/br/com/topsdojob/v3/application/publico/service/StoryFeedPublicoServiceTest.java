package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService.MidiaElegivel;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService.ResultadoUrlPublica;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class StoryFeedPublicoServiceTest {

    private final StorySelecaoAdministrativaRepository selecaoRepository = mock(StorySelecaoAdministrativaRepository.class);
    private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final StoryMidiaElegibilidadeService elegibilidadeService = mock(StoryMidiaElegibilidadeService.class);
    private final ComplianceVisitorAccessService visitorAccessService =
            mock(ComplianceVisitorAccessService.class);
    private final IdadeAnunciantePublicaService idadeAnuncianteService =
            mock(IdadeAnunciantePublicaService.class);
    private final PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
    private final MidiaPublicaUrlService urlService = mock(MidiaPublicaUrlService.class);
    private final MidiaPublicaMapper midiaMapper = new MidiaPublicaMapper(urlService);
    private final StoryAnuncioApresentacaoService apresentacaoService =
            mock(StoryAnuncioApresentacaoService.class);
    private StoryFeedPublicoService service;

    @BeforeEach
    void setUp() {
        service = new StoryFeedPublicoService(
                selecaoRepository,
                storyRepository,
                midiaRepository,
                arquivoRepository,
                anuncioRepository,
                usuarioRepository,
                elegibilidadeService,
                visitorAccessService,
                idadeAnuncianteService,
                premiumMapper,
                urlService,
                midiaMapper,
                apresentacaoService);
        when(premiumMapper.flagsPorAnuncios(any())).thenReturn(Map.of());
        when(premiumMapper.flags(any())).thenReturn(PremiumPublicoFlagsDto.vazio());
        when(idadeAnuncianteService.resolverPorAnuncios(any(), any())).thenReturn(Map.of());
        when(idadeAnuncianteService.resolver(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new IdadeAnunciantePublicaService.Resultado("Perfil", null, false));
        when(elegibilidadeService.listarPorAnuncios(any())).thenReturn(Map.of());
        when(usuarioRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            java.util.ArrayList<UsuarioEntity> usuarios = new java.util.ArrayList<>();
            ids.forEach(id -> usuarios.add(usuarioAtivo(id)));
            return usuarios;
        });
        when(usuarioRepository.findById(any())).thenAnswer(invocation ->
                Optional.of(usuarioAtivo(invocation.getArgument(0))));
        when(urlService.resolverPreviewRestrita(any())).thenAnswer(invocation -> {
            ArquivoMidiaEntity arquivo = invocation.getArgument(0);
            return arquivo.getMimeType() != null && arquivo.getMimeType().startsWith("image/")
                    ? new ResultadoUrlPublica("/restritas-borradas/preview.jpg", null)
                    : new ResultadoUrlPublica(null, "PENDENTE_DERIVACAO_RESTRITA");
        });
    }

    @Test
    void storyAnuncioAntesDoGateUsaSuperficieNeutraSemConsultarGaleria() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "story-anuncio-neutro");
        StoryAnuncioEntity story = storyAnuncio(anuncioId, 0);
        when(visitorAccessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(false);
        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of());
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of(story));
        when(anuncioRepository.findAllById(any())).thenReturn(List.of(anuncio));
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));

        var feed = service.listar(request);
        var viewer = service.buscar(story.getId().toString(), request);

        assertThat(feed).singleElement().satisfies(bundle -> {
            assertThat(bundle.displayUsername()).isNull();
            assertThat(bundle.avatarUrl()).isNull();
            assertThat(bundle.itens()).singleElement().satisfies(item -> {
                assertThat(item.modoConteudo()).isEqualTo("ANUNCIO");
                assertThat(item.tipo()).isEqualTo("ANUNCIO");
                assertThat(item.previewState()).isEqualTo("IDADE_NAO_CONFIRMADA");
                assertThat(item.previewUrl()).isNull();
                assertThat(item.displayUsername()).isNull();
            });
        });
        assertThat(viewer.viewerState()).isEqualTo("IDADE_NAO_CONFIRMADA");
        assertThat(viewer.midiaUrl()).isNull();
        assertThat(viewer.midias()).isEmpty();
        assertThat(viewer.displayUsername()).isNull();
        assertThat(viewer.cidade()).isNull();
        verify(apresentacaoService, never()).apresentar(any());
        verify(elegibilidadeService, never()).listar(any());
        verify(urlService, never()).resolver(any(), any());
        verify(urlService, never()).resolverPreviewRestrita(any());
    }

    @Test
    void storyAnuncioDepoisDoGateRetornaGaleriaPublicavelNaOrdemCanonica() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID anuncioId = UUID.randomUUID();
        UUID outroAnuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "story-anuncio-liberado");
        StoryAnuncioEntity story = storyAnuncio(anuncioId, 0);
        MidiaElegivel ordemDois = midiaElegivel(anuncioId, UUID.randomUUID(), 2);
        MidiaElegivel ordemZero = midiaElegivel(anuncioId, UUID.randomUUID(), 0);
        set(ordemZero.vinculo(), "tipo", TipoAnuncioMidia.VIDEO);
        set(ordemZero.arquivo(), "mimeType", "video/mp4");
        MidiaElegivel ordemUm = midiaElegivel(anuncioId, UUID.randomUUID(), 1);
        MidiaElegivel pendente = midiaElegivel(anuncioId, UUID.randomUUID(), 3);
        set(pendente.vinculo(), "status", StatusAnuncioMidia.PENDENTE);
        MidiaElegivel rejeitada = midiaElegivel(anuncioId, UUID.randomUUID(), 4);
        set(rejeitada.vinculo(), "status", StatusAnuncioMidia.REJEITADA);
        MidiaElegivel removida = midiaElegivel(anuncioId, UUID.randomUUID(), 5);
        set(removida.vinculo(), "status", StatusAnuncioMidia.REMOVIDA);
        MidiaElegivel arquivoPendente = midiaElegivel(anuncioId, UUID.randomUUID(), 6);
        set(arquivoPendente.arquivo(), "statusArquivo", StatusArquivoMidia.PENDENTE);
        MidiaElegivel deOutroAnuncio = midiaElegivel(outroAnuncioId, UUID.randomUUID(), 0);

        when(visitorAccessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        UsuarioEntity usuario = usuarioAtivo(usuarioId);
        set(usuario, "nome", "qa_publica");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of(
                ordemDois, rejeitada, ordemZero, deOutroAnuncio, ordemUm,
                pendente, removida, arquivoPendente));
        when(urlService.resolver(ordemZero.vinculo(), ordemZero.arquivo())).thenReturn(
                new ResultadoUrlPublica("/api/public/compliance/visitor/media/" + ordemZero.vinculo().getId(), null));
        when(urlService.resolver(ordemUm.vinculo(), ordemUm.arquivo())).thenReturn(
                new ResultadoUrlPublica("/api/public/compliance/visitor/media/" + ordemUm.vinculo().getId(), null));
        when(urlService.resolver(ordemDois.vinculo(), ordemDois.arquivo())).thenReturn(
                new ResultadoUrlPublica("/api/public/compliance/visitor/media/" + ordemDois.vinculo().getId(), null));
        when(apresentacaoService.apresentar(anuncio)).thenReturn(
                new StoryAnuncioApresentacaoService.Apresentacao(
                        anuncio.getTitulo(), "Cidade QA", "GO", java.math.BigDecimal.valueOf(150), "Resumo seguro"));

        var viewer = service.buscar(story.getId().toString(), request);

        assertThat(viewer.viewerState()).isEqualTo("LIBERADO");
        assertThat(viewer.modoConteudo()).isEqualTo("ANUNCIO");
        assertThat(viewer.tipo()).isEqualTo("ANUNCIO");
        assertThat(viewer.midiaUrl()).isNull();
        assertThat(viewer.midias())
                .extracting(item -> item.ordem())
                .containsExactly(0, 1, 2);
        assertThat(viewer.midias())
                .extracting(item -> item.id())
                .containsExactly(
                        ordemZero.vinculo().getId(),
                        ordemUm.vinculo().getId(),
                        ordemDois.vinculo().getId());
        assertThat(viewer.midias())
                .extracting(item -> item.urlPublica())
                .allSatisfy(url -> assertThat(url)
                        .startsWith("/api/public/compliance/visitor/media/")
                        .doesNotContain("X-Amz-"));
        assertThat(viewer.usuarioUsername()).isEqualTo("qa_publica");
        assertThat(viewer.displayUsername()).isEqualTo("qa_publica");
        assertThat(viewer.anuncioTitulo()).isEqualTo(anuncio.getTitulo());
        assertThat(viewer.cidade()).isEqualTo("Cidade QA");
        assertThat(viewer.uf()).isEqualTo("GO");
        assertThat(viewer.resumo()).isEqualTo("Resumo seguro");
        verify(elegibilidadeService).listar(anuncioId);
        verify(midiaRepository, never()).findById(any());
    }

    @Test
    void storyAnuncioSemMidiaPublicavelMantemFallbackTextualLiberado() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "story-anuncio-fallback");
        StoryAnuncioEntity story = storyAnuncio(anuncioId, 0);
        when(visitorAccessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuarioAtivo(usuarioId)));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of());
        when(apresentacaoService.apresentar(anuncio)).thenReturn(
                new StoryAnuncioApresentacaoService.Apresentacao(
                        anuncio.getTitulo(), "Cidade QA", "GO", java.math.BigDecimal.TEN, "Resumo seguro"));

        var viewer = service.buscar(story.getId().toString(), request);

        assertThat(viewer.viewerState()).isEqualTo("LIBERADO");
        assertThat(viewer.midias()).isEmpty();
        assertThat(viewer.anuncioTitulo()).isEqualTo(anuncio.getTitulo());
        assertThat(viewer.resumo()).isEqualTo("Resumo seguro");
        verify(elegibilidadeService).listar(anuncioId);
        verify(urlService, never()).resolver(any(), any());
    }

    @Test
    void administrativoEntraNoFeedEmPosicaoLivreEStoryPagoPrevaleceNaDeduplicacao() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY)).thenReturn(false);
        UUID arquivoRepetido = UUID.randomUUID();
        UUID arquivoAdmin = UUID.randomUUID();
        UUID anuncioAdminId = UUID.randomUUID();
        UUID anuncioUsuarioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        AnuncioEntity anuncioAdmin = anuncio(anuncioAdminId, UUID.randomUUID(), "admin-selecionado");
        AnuncioEntity anuncioUsuario = anuncio(anuncioUsuarioId, usuarioId, "story-pago");
        StorySelecaoAdministrativaEntity selecao = selecao(anuncioAdminId);
        MidiaElegivel adminDuplicada = midiaElegivel(anuncioAdminId, arquivoRepetido, 0);
        MidiaElegivel adminUnica = midiaElegivel(anuncioAdminId, arquivoAdmin, 1);
        set(adminUnica.vinculo(), "tipo", TipoAnuncioMidia.VIDEO);
        set(adminUnica.vinculo(), "visibilidadeMidia", VisibilidadeMidia.LIVRE);
        StoryAnuncioEntity storyPago = story(0);
        AnuncioMidiaEntity vinculoPago = vinculoStory(storyPago.getAnuncioMidiaId(), anuncioUsuarioId, arquivoRepetido);
        ArquivoMidiaEntity arquivoPago = arquivo(arquivoRepetido, "image/jpeg");

        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of(selecao));
        when(anuncioRepository.findById(anuncioAdminId)).thenReturn(Optional.of(anuncioAdmin));
        when(elegibilidadeService.listar(anuncioAdminId)).thenReturn(List.of(adminDuplicada, adminUnica));
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of(storyPago));
        when(midiaRepository.findByIdIn(any())).thenReturn(List.of(vinculoPago));
        when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivoPago));
        when(anuncioRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            java.util.Set<UUID> solicitados = new java.util.HashSet<>();
            ids.forEach(solicitados::add);
            return List.of(anuncioAdmin, anuncioUsuario).stream()
                    .filter(item -> solicitados.contains(item.getId()))
                    .toList();
        });
        when(elegibilidadeService.listarPorAnuncios(any()))
                .thenReturn(Map.of(anuncioAdminId, List.of(adminDuplicada, adminUnica)));
        when(midiaRepository.findByArquivoMidiaId(arquivoAdmin)).thenReturn(List.of(adminUnica.vinculo()));
        when(midiaRepository.findById(adminUnica.vinculo().getId()))
                .thenReturn(Optional.of(adminUnica.vinculo()));
        when(storyRepository.findByAnuncioMidiaIdIn(List.of(adminUnica.vinculo().getId()))).thenReturn(List.of());

        var response = service.listar(request);

        assertThat(response).hasSize(2);
        var adminBundle = response.stream()
                .filter(item -> item.bundleKey().startsWith("administrativo:"))
                .findFirst()
                .orElseThrow();
        var paidBundle = response.stream()
                .filter(item -> !item.bundleKey().startsWith("administrativo:"))
                .findFirst()
                .orElseThrow();
        assertThat(adminBundle.itens()).hasSize(1);
        assertThat(adminBundle.avatarUrl()).isNull();
        assertThat(adminBundle.itens().get(0).storyId()).isEqualTo("administrativo:" + adminUnica.vinculo().getId());
        assertThat(adminBundle.itens().get(0).tipo()).isEqualTo("VIDEO");
        assertThat(paidBundle.avatarUrl()).isNull();
        assertThat(paidBundle.itens().get(0).storyId()).isEqualTo(storyPago.getId().toString());
        assertThat(paidBundle.itens().get(0).previewState()).isEqualTo("IDADE_NAO_CONFIRMADA");
        assertThat(paidBundle.itens().get(0).previewUrl()).isNull();
        verify(premiumMapper).flagsPorAnuncios(List.of(anuncioAdmin));
        verify(elegibilidadeService).listarPorAnuncios(java.util.Set.of(anuncioAdminId));

        var viewer = service.buscar("administrativo:" + adminUnica.vinculo().getId(), request);
        assertThat(viewer.viewerState()).isEqualTo("IDADE_NAO_CONFIRMADA");
        assertThat(viewer.midiaUrl()).isNull();
        assertThat(adminUnica.vinculo().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.LIVRE);
        verify(urlService, never()).resolverPreviewRestrita(any());
        verify(urlService, never()).resolver(any(), any());
    }

    @Test
    void autorizacaoValidaMantemPreviewSeparadaELiberaMidiaRestritaSomentePeloEndpointProtegido() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY)).thenReturn(true);
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "story-autorizado");
        StoryAnuncioEntity story = story(0);
        AnuncioMidiaEntity vinculo = vinculoStory(story.getAnuncioMidiaId(), anuncioId, arquivoId);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId, "image/jpeg");

        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of());
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of(story));
        when(midiaRepository.findByIdIn(any())).thenReturn(List.of(vinculo));
        when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
        when(anuncioRepository.findAllById(any())).thenReturn(List.of(anuncio));
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(midiaRepository.findById(vinculo.getId())).thenReturn(Optional.of(vinculo));
        when(arquivoRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(urlService.resolver(vinculo, arquivo)).thenReturn(new ResultadoUrlPublica(
                "/api/public/compliance/visitor/media/" + vinculo.getId(),
                null));

        var feed = service.listar(request);
        var viewer = service.buscar(story.getId().toString(), request);

        String endpointProtegido = "/api/public/compliance/visitor/media/stories/" + story.getId();
        assertThat(feed).singleElement().satisfies(bundle -> {
                assertThat(bundle.avatarUrl()).isEqualTo(endpointProtegido);
                assertThat(bundle.itens()).singleElement().satisfies(item -> {
                    assertThat(item.previewState()).isEqualTo("AVAILABLE");
                    assertThat(item.previewUrl()).isEqualTo(endpointProtegido);
                });
        });
        assertThat(viewer.viewerState()).isEqualTo("LIBERADO");
        assertThat(viewer.midiaUrl())
                .isEqualTo(endpointProtegido)
                .doesNotContain("X-Amz-");
    }

    @Test
    void selecoesAdministrativasABCEntramJuntasSemDuplicidade() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        List<UUID> anuncioIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<StorySelecaoAdministrativaEntity> selecoes = anuncioIds.stream().map(this::selecao).toList();
        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(selecoes);
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of());
        List<AnuncioEntity> anuncios = new java.util.ArrayList<>();
        Map<UUID, List<MidiaElegivel>> midias = new java.util.LinkedHashMap<>();
        for (int index = 0; index < anuncioIds.size(); index++) {
            UUID anuncioId = anuncioIds.get(index);
            MidiaElegivel midia = midiaElegivel(anuncioId, UUID.randomUUID(), 0);
            anuncios.add(anuncio(anuncioId, UUID.randomUUID(), "admin-" + index));
            midias.put(anuncioId, List.of(midia));
        }
        when(anuncioRepository.findAllById(any())).thenReturn(anuncios);
        when(elegibilidadeService.listarPorAnuncios(any())).thenReturn(midias);

        var response = service.listar(request);

        assertThat(response).hasSize(3);
        assertThat(response.stream().flatMap(bundle -> bundle.itens().stream()).map(item -> item.storyId()))
                .hasSize(3)
                .doesNotHaveDuplicates();
        verify(anuncioRepository, never()).findById(any());
        verify(elegibilidadeService, never()).listar(any());
    }

    @Test
    void selecaoAdministrativaExpiradaNaoEntraNoFeed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID anuncioId = UUID.randomUUID();
        StorySelecaoAdministrativaEntity expirada = selecao(anuncioId);
        set(expirada, "expiraEm", OffsetDateTime.now().minusSeconds(1));
        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of(expirada));
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of());

        assertThat(service.listar(request)).isEmpty();
    }

    @Test
    void novasMidiasEntramERemovidasSaemSemPersistirLista() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY)).thenReturn(false);
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, UUID.randomUUID(), "admin-dinamico");
        MidiaElegivel primeira = midiaElegivel(anuncioId, UUID.randomUUID(), 0);
        MidiaElegivel segunda = midiaElegivel(anuncioId, UUID.randomUUID(), 1);
        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc())
                .thenReturn(List.of(selecao(anuncioId)));
        when(anuncioRepository.findAllById(any())).thenReturn(List.of(anuncio));
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of());
        when(elegibilidadeService.listarPorAnuncios(any()))
                .thenReturn(Map.of(anuncioId, List.of(primeira)))
                .thenReturn(Map.of(anuncioId, List.of(primeira, segunda)))
                .thenReturn(Map.of(anuncioId, List.of(segunda)));

        assertThat(service.listar(request).get(0).itens()).hasSize(1);
        assertThat(service.listar(request).get(0).itens()).hasSize(2);
        assertThat(service.listar(request).get(0).itens())
                .extracting(item -> item.storyId())
                .containsExactly("administrativo:" + segunda.vinculo().getId());
    }

    @Test
    void midiaUploadDiretaUsaUsernamePublicoCanonicoSemAnuncio() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY)).thenReturn(true);
        UUID usuarioId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();
        StoryAnuncioEntity story = StoryAnuncioEntity.criarMidiaUpload(
                UUID.randomUUID(),
                arquivoId,
                UUID.randomUUID(),
                "story-direto-token",
                "a".repeat(64),
                agora.minusHours(1),
                agora.plusHours(23),
                usuarioId);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId, "image/jpeg");
        UsuarioEntity usuario = usuarioAtivo(usuarioId);
        set(usuario, "nome", "qa_publica");

        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of());
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of(story));
        when(midiaRepository.findByIdIn(any())).thenReturn(List.of());
        when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
        when(anuncioRepository.findAllById(any())).thenReturn(List.of());
        org.mockito.Mockito.doReturn(List.of(usuario)).when(usuarioRepository).findAllById(any());
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(arquivoRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        var feed = service.listar(request);
        var viewer = service.buscar(story.getId().toString(), request);

        assertThat(feed).singleElement().satisfies(bundle -> {
            assertThat(bundle.bundleKey()).isEqualTo(story.getId().toString());
            assertThat(bundle.usuarioUsername()).isEqualTo("qa_publica");
            assertThat(bundle.displayUsername()).isEqualTo("qa_publica");
            assertThat(bundle.profileNavigable()).isTrue();
            assertThat(bundle.itens()).singleElement().satisfies(item -> {
                assertThat(item.anuncioId()).isNull();
                assertThat(item.anuncioSlug()).isNull();
                assertThat(item.anuncioTitulo()).isNull();
                assertThat(item.usuarioUsername()).isEqualTo("qa_publica");
                assertThat(item.displayUsername()).isEqualTo("qa_publica");
                assertThat(item.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
                assertThat(item.previewUrl())
                        .isEqualTo("/api/public/compliance/visitor/media/stories/" + story.getId());
            });
        });
        assertThat(viewer.anuncioId()).isNull();
        assertThat(viewer.anuncioSlug()).isNull();
        assertThat(viewer.anuncioTitulo()).isNull();
        assertThat(viewer.usuarioUsername()).isEqualTo("qa_publica");
        assertThat(viewer.displayUsername()).isEqualTo("qa_publica");
        assertThat(viewer.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
        assertThat(viewer.midiaUrl())
                .isEqualTo("/api/public/compliance/visitor/media/stories/" + story.getId());
    }

    @Test
    void doisStoriesDaMesmaContaGeramItensIndependentesComMesmoUsernamePublico() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.STORY)).thenReturn(true);
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();
        StoryAnuncioEntity storyAnuncio = storyAnuncio(anuncioId, 0);
        StoryAnuncioEntity storyMidia = StoryAnuncioEntity.criarMidiaUpload(
                UUID.randomUUID(),
                arquivoId,
                UUID.randomUUID(),
                "story-independente",
                "b".repeat(64),
                agora.minusHours(1),
                agora.plusHours(23),
                usuarioId);
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "anuncio-wesley");
        ArquivoMidiaEntity arquivo = arquivo(arquivoId, "image/jpeg");
        UsuarioEntity usuario = usuarioAtivo(usuarioId);
        set(usuario, "nome", "wesley");
        var idade = new IdadeAnunciantePublicaService.Resultado("wesley", 30, false);

        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of());
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of(storyAnuncio, storyMidia));
        when(midiaRepository.findByIdIn(any())).thenReturn(List.of());
        when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
        when(anuncioRepository.findAllById(any())).thenReturn(List.of(anuncio));
        org.mockito.Mockito.doReturn(List.of(usuario)).when(usuarioRepository).findAllById(any());
        when(idadeAnuncianteService.resolverPorAnuncios(any(), any()))
                .thenReturn(Map.of(anuncioId, idade));
        when(storyRepository.findByIdAndStatus(storyAnuncio.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(storyAnuncio));
        when(storyRepository.findByIdAndStatus(storyMidia.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(storyMidia));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(arquivoRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(apresentacaoService.apresentar(anuncio)).thenReturn(
                new StoryAnuncioApresentacaoService.Apresentacao(
                        anuncio.getTitulo(), "Cidade QA", "GO", java.math.BigDecimal.TEN, "Resumo"));

        var feed = service.listar(request);
        var viewerAnuncio = service.buscar(storyAnuncio.getId().toString(), request);
        var viewerMidia = service.buscar(storyMidia.getId().toString(), request);

        assertThat(feed).hasSize(2);
        assertThat(feed).extracting(bundle -> bundle.bundleKey())
                .containsExactly(storyAnuncio.getId().toString(), storyMidia.getId().toString());
        assertThat(feed).allSatisfy(bundle -> {
            assertThat(bundle.usuarioUsername()).isEqualTo("wesley");
            assertThat(bundle.displayUsername()).isEqualTo("wesley");
            assertThat(bundle.itens()).hasSize(1);
            assertThat(bundle.bundleKey()).isEqualTo(bundle.itens().get(0).storyId());
        });
        var itemAnuncio = feed.get(0).itens().get(0);
        var itemMidia = feed.get(1).itens().get(0);
        assertThat(itemAnuncio.modoConteudo()).isEqualTo("ANUNCIO");
        assertThat(itemAnuncio.anuncioTitulo()).isEqualTo(anuncio.getTitulo());
        assertThat(itemAnuncio.displayUsername()).isEqualTo("wesley");
        assertThat(itemMidia.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
        assertThat(itemMidia.anuncioTitulo()).isNull();
        assertThat(itemMidia.displayUsername()).isEqualTo("wesley");
        assertThat(itemAnuncio.storyId()).isNotEqualTo(itemMidia.storyId());
        assertThat(viewerAnuncio.usuarioUsername()).isEqualTo("wesley");
        assertThat(viewerAnuncio.displayUsername()).isEqualTo("wesley");
        assertThat(viewerAnuncio.anuncioTitulo()).isEqualTo(anuncio.getTitulo());
        assertThat(viewerMidia.usuarioUsername()).isEqualTo("wesley");
        assertThat(viewerMidia.displayUsername()).isEqualTo("wesley");
        assertThat(viewerMidia.anuncioTitulo()).isNull();
        assertThat(List.of(
                itemAnuncio.usuarioUsername(),
                itemAnuncio.displayUsername(),
                itemMidia.usuarioUsername(),
                itemMidia.displayUsername(),
                viewerAnuncio.usuarioUsername(),
                viewerMidia.usuarioUsername()))
                .allSatisfy(value -> assertThat(value).doesNotMatch("[0-9a-f]{32}"));
    }

    @Test
    void proprietarioInativoNaoApareceNoFeedNemNoViewer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "story-inativo");
        StoryAnuncioEntity story = storyAnuncio(anuncioId, 0);
        UsuarioEntity inativo = entity(UsuarioEntity.class);
        set(inativo, "id", usuarioId);
        set(inativo, "status", StatusUsuario.DESATIVADO);
        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of());
        when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio.PUBLICADO))
                .thenReturn(List.of(story));
        when(anuncioRepository.findAllById(any())).thenReturn(List.of(anuncio));
        org.mockito.Mockito.doReturn(List.of(inativo)).when(usuarioRepository).findAllById(any());
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(inativo));

        assertThat(service.listar(request)).isEmpty();
        assertThatThrownBy(() -> service.buscar(story.getId().toString(), request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private StorySelecaoAdministrativaEntity selecao(UUID anuncioId) {
        StorySelecaoAdministrativaEntity selecao = entity(StorySelecaoAdministrativaEntity.class);
        set(selecao, "id", 1L);
        OffsetDateTime agora = OffsetDateTime.now();
        set(selecao, "anuncioId", anuncioId);
        set(selecao, "ativa", true);
        set(selecao, "ativadoPor", UUID.randomUUID());
        set(selecao, "ativadoEm", agora);
        set(selecao, "expiraEm", agora.plusHours(24));
        return selecao;
    }

    private AnuncioEntity anuncio(UUID id, UUID usuarioId, String slug) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", id);
        set(anuncio, "usuarioId", usuarioId);
        set(anuncio, "slug", slug);
        set(anuncio, "titulo", "Anuncio de demonstracao");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        return anuncio;
    }

    private MidiaElegivel midiaElegivel(UUID anuncioId, UUID arquivoId, int ordem) {
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", UUID.randomUUID());
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);
        set(vinculo, "ordem", ordem);
        return new MidiaElegivel(vinculo, arquivo(arquivoId, "image/jpeg"));
    }

    private StoryAnuncioEntity story(int ordem) {
        StoryAnuncioEntity story = entity(StoryAnuncioEntity.class);
        set(story, "id", UUID.randomUUID());
        set(story, "anuncioMidiaId", UUID.randomUUID());
        set(story, "status", StatusStoryAnuncio.PUBLICADO);
        set(story, "inicioEm", OffsetDateTime.now().minusHours(1));
        set(story, "fimEm", OffsetDateTime.now().plusHours(1));
        set(story, "ordem", ordem);
        return story;
    }

    private StoryAnuncioEntity storyAnuncio(UUID anuncioId, int ordem) {
        StoryAnuncioEntity story = entity(StoryAnuncioEntity.class);
        set(story, "id", UUID.randomUUID());
        set(story, "anuncioId", anuncioId);
        set(story, "modoConteudo", ModoConteudoStory.ANUNCIO);
        set(story, "status", StatusStoryAnuncio.PUBLICADO);
        set(story, "inicioEm", OffsetDateTime.now().minusHours(1));
        set(story, "fimEm", OffsetDateTime.now().plusHours(1));
        set(story, "ordem", ordem);
        return story;
    }

    private AnuncioMidiaEntity vinculoStory(UUID id, UUID anuncioId, UUID arquivoId) {
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", id);
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.STORY);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.STORY);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);
        return vinculo;
    }

    private ArquivoMidiaEntity arquivo(UUID id, String mimeType) {
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", id);
        set(arquivo, "mimeType", mimeType);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        return arquivo;
    }

    private UsuarioEntity usuarioAtivo(UUID id) {
        UsuarioEntity usuario = entity(UsuarioEntity.class);
        set(usuario, "id", id);
        set(usuario, "status", StatusUsuario.ATIVO);
        return usuario;
    }
}
