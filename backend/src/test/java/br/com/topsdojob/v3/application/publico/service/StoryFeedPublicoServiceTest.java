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
        assertThat(viewer.displayUsername()).isNull();
        assertThat(viewer.cidade()).isNull();
        verify(apresentacaoService, never()).apresentar(any());
        verify(urlService, never()).resolver(any(), any());
        verify(urlService, never()).resolverPreviewRestrita(any());
    }

    @Test
    void storyAnuncioDepoisDoGateLiberaApresentacaoSemSelecionarMidia() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, "story-anuncio-liberado");
        StoryAnuncioEntity story = storyAnuncio(anuncioId, 0);
        when(visitorAccessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
        when(storyRepository.findByIdAndStatus(story.getId(), StatusStoryAnuncio.PUBLICADO))
                .thenReturn(Optional.of(story));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(apresentacaoService.apresentar(anuncio)).thenReturn(
                new StoryAnuncioApresentacaoService.Apresentacao(
                        anuncio.getTitulo(), "Cidade QA", "GO", java.math.BigDecimal.valueOf(150), "Resumo seguro"));

        var viewer = service.buscar(story.getId().toString(), request);

        assertThat(viewer.viewerState()).isEqualTo("LIBERADO");
        assertThat(viewer.modoConteudo()).isEqualTo("ANUNCIO");
        assertThat(viewer.tipo()).isEqualTo("ANUNCIO");
        assertThat(viewer.midiaUrl()).isNull();
        assertThat(viewer.displayUsername()).isEqualTo(anuncio.getTitulo());
        assertThat(viewer.cidade()).isEqualTo("Cidade QA");
        assertThat(viewer.uf()).isEqualTo("GO");
        assertThat(viewer.resumo()).isEqualTo("Resumo seguro");
        verify(midiaRepository, never()).findById(any());
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
                .filter(item -> item.usuarioId().startsWith("administrativo:"))
                .findFirst()
                .orElseThrow();
        var paidBundle = response.stream()
                .filter(item -> !item.usuarioId().startsWith("administrativo:"))
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

        assertThat(feed).singleElement().satisfies(bundle -> {
                assertThat(bundle.avatarUrl()).contains("restritas-borradas");
                assertThat(bundle.itens()).singleElement().satisfies(item -> {
                    assertThat(item.previewState()).isEqualTo("AVAILABLE");
                    assertThat(item.previewUrl()).contains("restritas-borradas");
                });
        });
        assertThat(viewer.viewerState()).isEqualTo("LIBERADO");
        assertThat(viewer.midiaUrl())
                .isEqualTo("/api/public/compliance/visitor/media/" + vinculo.getId())
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
        selecao.ativar(anuncioId, UUID.randomUUID(), OffsetDateTime.now());
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
