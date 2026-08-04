package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.service.IdadeAnunciantePublicaService;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.application.publico.service.StoryAnuncioApresentacaoService;
import br.com.topsdojob.v3.application.publico.service.StoryFeedPublicoService;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class MeuAnuncioStoryServiceTest {

  private static final UUID USUARIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID ANUNCIO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID ANUNCIO_B_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final String SLUG = "anuncio-story-teste";
  private static final String SLUG_B = "anuncio-story-teste-b";
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-01T12:00:00Z");
  private static final OffsetDateTime FIM = AGORA.plusHours(24);

  private final MeusAnunciosConsultaService consultaService = mock(MeusAnunciosConsultaService.class);
  private final MeuAnuncioStoryConsultaService storyConsultaService = mock(MeuAnuncioStoryConsultaService.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AtivacaoBeneficioRepository ativacaoRepository = mock(AtivacaoBeneficioRepository.class);
  private final BeneficioAnuncioConsultaService beneficioService = mock(BeneficioAnuncioConsultaService.class);
  private final BeneficioPremiumOpcaoRepository opcaoRepository = mock(BeneficioPremiumOpcaoRepository.class);
  private final GrupoAtivacaoBeneficioRepository grupoRepository = mock(GrupoAtivacaoBeneficioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final StoryUploadCleanupAuditService cleanupAuditService = mock(StoryUploadCleanupAuditService.class);
  private final MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
  private final FotoUploadProcessor fotoProcessor = mock(FotoUploadProcessor.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
  private final Authentication authentication = mock(Authentication.class);
  private final R2StorageProperties properties = properties();
  private final Map<String, StoredObject> objetos = new LinkedHashMap<>();
  private final List<StoryAnuncioEntity> stories = new ArrayList<>();
  private final List<AnuncioMidiaEntity> midias = new ArrayList<>();
  private final Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
  private MeuAnuncioStoryService service;
  private AnuncioEntity anuncio;
  private UsuarioEntity usuario;
  private AtivacaoBeneficioEntity ativacao;
  private GrupoAtivacaoBeneficioEntity grupo;

  @BeforeEach
  void setUp() {
    usuario = UsuarioEntity.criarCadastroPublico(
        USUARIO_ID, "QA Story", "qa-story@example.invalid", "+5562999999999",
        java.time.LocalDate.of(1990, 1, 1), AGORA.minusYears(1));
    usuario.confirmarEmail(AGORA.minusYears(1));
    anuncio = anuncio(StatusAnuncio.PUBLICADO, USUARIO_ID);
    UUID grupoId = UUID.randomUUID();
    ativacao = AtivacaoBeneficioEntity.criarFixtureHomologacao(
        UUID.randomUUID(), UUID.randomUUID(), USUARIO_ID, ANUNCIO_ID, grupoId,
        OrigemBeneficio.CREDITO, AGORA.minusHours(1), FIM.minusHours(1),
        StatusAtivacaoBeneficio.ATIVA, 5, BigDecimal.ZERO, "beneficio-story", AGORA.minusHours(1));
    grupo = GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
        grupoId, TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.CREDITO,
        USUARIO_ID, ANUNCIO_ID, AGORA.minusHours(1), FIM.minusHours(1),
        StatusGrupoAtivacaoBeneficio.ATIVO, "grupo-story", AGORA.minusHours(1));
    BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
        ativacao.getBeneficioId(), STORIES, "Stories", "Story por 24 horas",
        EscopoBeneficioPremium.ANUNCIO, false, true, AGORA.minusDays(1));
    PremiumBeneficioCalculado calculado = new PremiumBeneficioCalculado(
        ativacao, beneficio, null, PremiumBeneficioStatusCalculado.ATIVO,
        List.of(), false, false);

    when(consultaService.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(consultaService.slugSeguro(SLUG)).thenReturn(SLUG);
    when(anuncioRepository.findBySlugForLifecycle(SLUG)).thenReturn(Optional.of(anuncio));
    when(storyRepository.findByAnuncioIdForUpdate(any())).thenAnswer(invocation -> {
      UUID anuncioId = invocation.getArgument(0);
      return stories.stream()
          .filter(story -> anuncioId.equals(story.getAnuncioId()))
          .toList();
    });
    when(storyRepository.findByAnuncioIdAndCriadoPorAndIdempotencyKey(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(storyRepository.save(any())).thenAnswer(invocation -> {
      StoryAnuncioEntity story = invocation.getArgument(0);
      stories.add(story);
      return story;
    });
    when(ativacaoRepository.findVigentesByCodigoForUpdate(
        eq(ANUNCIO_ID), eq(USUARIO_ID), eq(STORIES), eq(StatusAtivacaoBeneficio.ATIVA), any()))
        .thenReturn(List.of(ativacao));
    when(ativacaoRepository.findAguardandoUsoByCodigoForUpdate(
        eq(ANUNCIO_ID), eq(USUARIO_ID), eq(STORIES),
        eq(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO))).thenReturn(List.of());
    when(beneficioService.calcular(any(), any())).thenReturn(List.of(calculado));
    when(grupoRepository.findByIdForUpdate(grupoId)).thenReturn(Optional.of(grupo));
    when(grupoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ativacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(storyConsultaService.consultar(any())).thenAnswer(invocation -> dto(invocation.getArgument(0)));
    when(storageProvider.getIfAvailable()).thenReturn(storage);
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any())).thenAnswer(invocation -> {
      String key = invocation.getArgument(1);
      if (objetos.containsKey(key)) return ObjectWriteResult.ALREADY_EXISTS;
      objetos.put(key, new StoredObject(invocation.getArgument(2), invocation.getArgument(3)));
      return ObjectWriteResult.CREATED;
    });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), any())).thenAnswer(invocation -> objetos.get(invocation.getArgument(1)));
    when(arquivoRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      ArquivoMidiaEntity arquivo = invocation.getArgument(0);
      return persistirComoJpa(arquivo);
    });
    when(arquivoRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(arquivos.get(invocation.getArgument(0))));
    when(midiaRepository.findByAnuncioId(any())).thenAnswer(invocation -> {
      UUID anuncioId = invocation.getArgument(0);
      return midias.stream()
          .filter(midia -> anuncioId.equals(midia.getAnuncioId()))
          .toList();
    });
    when(midiaRepository.save(any())).thenAnswer(invocation -> {
      AnuncioMidiaEntity midia = invocation.getArgument(0);
      midias.add(midia);
      return midia;
    });
    when(midiaRepository.findById(any())).thenAnswer(invocation -> midias.stream()
        .filter(item -> item.getId().equals(invocation.getArgument(0)))
        .findFirst());

    service = serviceComClock(
        Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC));
  }

  private MeuAnuncioStoryService serviceComClock(Clock serviceClock) {
    return new MeuAnuncioStoryService(
        consultaService, storyConsultaService, anuncioRepository, storyRepository,
        midiaRepository, arquivoRepository, ativacaoRepository, beneficioService,
        grupoRepository, auditoriaRepository, validator, fotoProcessor, properties, storageProvider,
        cleanupAuditService, serviceClock);
  }

  @Test
  void modoAnuncioPublicaSemUploadSemStorageESemNovaCobranca() {
    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "ANUNCIO", List.of(), "story-anuncio-1", authentication, "req-1");

    assertThat(response.modoConteudo()).isEqualTo("ANUNCIO");
    assertThat(response.inicioEm()).isEqualTo(AGORA);
    assertThat(response.fimEm()).isEqualTo(FIM.minusHours(1));
    assertThat(stories).singleElement().satisfies(story -> {
      assertThat(story.getAnuncioId()).isEqualTo(ANUNCIO_ID);
      assertThat(story.getAnuncioMidiaId()).isNull();
      assertThat(story.getAtivacaoBeneficioId()).isEqualTo(ativacao.getId());
      assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.PUBLICADO);
    });
    assertThat(midias).isEmpty();
    assertThat(arquivos).isEmpty();
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    verify(validator, never()).validarStory(any());
    verify(ativacaoRepository, never()).save(ativacao);
    verify(grupoRepository, never()).save(grupo);
  }

  @Test
  void mesmoUsuarioMantemStoriesAtivosEmDoisAnunciosEFeedRetornaAmbos() {
    AnuncioEntity anuncioB = anuncio(
        ANUNCIO_B_ID, SLUG_B, StatusAnuncio.PUBLICADO, USUARIO_ID);
    UUID grupoBId = UUID.randomUUID();
    AtivacaoBeneficioEntity ativacaoB = AtivacaoBeneficioEntity.criarFixtureHomologacao(
        UUID.randomUUID(), UUID.randomUUID(), USUARIO_ID, ANUNCIO_B_ID, grupoBId,
        OrigemBeneficio.CREDITO, AGORA.minusHours(1), FIM.minusHours(1),
        StatusAtivacaoBeneficio.ATIVA, 5, BigDecimal.ZERO,
        "beneficio-story-b", AGORA.minusHours(1));
    GrupoAtivacaoBeneficioEntity grupoB = GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
        grupoBId, TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.CREDITO,
        USUARIO_ID, ANUNCIO_B_ID, AGORA.minusHours(1), FIM.minusHours(1),
        StatusGrupoAtivacaoBeneficio.ATIVO, "grupo-story-b", AGORA.minusHours(1));

    when(consultaService.slugSeguro(SLUG_B)).thenReturn(SLUG_B);
    when(anuncioRepository.findBySlugForLifecycle(SLUG_B)).thenReturn(Optional.of(anuncioB));
    when(ativacaoRepository.findVigentesByCodigoForUpdate(
        eq(ANUNCIO_B_ID), eq(USUARIO_ID), eq(STORIES),
        eq(StatusAtivacaoBeneficio.ATIVA), any())).thenReturn(List.of(ativacaoB));
    when(ativacaoRepository.findAguardandoUsoByCodigoForUpdate(
        eq(ANUNCIO_B_ID), eq(USUARIO_ID), eq(STORIES),
        eq(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO))).thenReturn(List.of());
    when(beneficioService.calcular(eq(List.of(ativacaoB)), any()))
        .thenReturn(List.of(calculado(ativacaoB, beneficio(ativacaoB))));
    when(grupoRepository.findByIdForUpdate(grupoBId)).thenReturn(Optional.of(grupoB));

    MeuAnuncioStoryDto respostaA = service.publicar(
        SLUG, "ANUNCIO", List.of(), "story-chave-compartilhada",
        authentication, "req-story-a");
    StoryAnuncioEntity storyA = stories.get(0);
    UUID storyAId = storyA.getId();
    OffsetDateTime storyAInicio = storyA.getInicioEm();
    OffsetDateTime storyAFim = storyA.getFimEm();

    assertThatThrownBy(() -> service.publicar(
        SLUG, "ANUNCIO", List.of(), "story-segundo-no-mesmo-anuncio",
        authentication, "req-story-a-duplicado"))
        .isInstanceOf(StoryJaAtivoException.class);

    MeuAnuncioStoryDto respostaB = service.publicar(
        SLUG_B, "ANUNCIO", List.of(), "story-chave-compartilhada",
        authentication, "req-story-b");
    StoryAnuncioEntity storyB = stories.stream()
        .filter(item -> ANUNCIO_B_ID.equals(item.getAnuncioId()))
        .findFirst()
        .orElseThrow();

    assertThat(stories).hasSize(2);
    assertThat(respostaA.anuncioId()).isEqualTo(ANUNCIO_ID);
    assertThat(respostaB.anuncioId()).isEqualTo(ANUNCIO_B_ID);
    assertThat(storyA.getId()).isEqualTo(storyAId);
    assertThat(storyA.getModoConteudoEfetivo()).isEqualTo(ModoConteudoStory.ANUNCIO);
    assertThat(storyA.getInicioEm()).isEqualTo(storyAInicio);
    assertThat(storyA.getFimEm()).isEqualTo(storyAFim);
    assertThat(storyA.getAnuncioMidiaId()).isNull();
    assertThat(storyA.getStatus()).isEqualTo(StatusStoryAnuncio.PUBLICADO);
    assertThat(storyB.getStatus()).isEqualTo(StatusStoryAnuncio.PUBLICADO);
    assertThat(storyB.getInicioEm()).isEqualTo(AGORA);
    assertThat(storyB.getFimEm()).isEqualTo(FIM.minusHours(1));
    assertThat(storyA.getIdempotencyKey()).isEqualTo(storyB.getIdempotencyKey());
    assertThat(storyA.getId()).isNotEqualTo(storyB.getId());
    assertThat(storyA.getAtivacaoBeneficioId()).isEqualTo(ativacao.getId());
    assertThat(storyB.getAtivacaoBeneficioId()).isEqualTo(ativacaoB.getId());

    StorySelecaoAdministrativaRepository selecoes =
        mock(StorySelecaoAdministrativaRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    StoryMidiaElegibilidadeService elegibilidade =
        mock(StoryMidiaElegibilidadeService.class);
    ComplianceVisitorAccessService acesso = mock(ComplianceVisitorAccessService.class);
    IdadeAnunciantePublicaService idades = mock(IdadeAnunciantePublicaService.class);
    PremiumPublicoMapper premium = mock(PremiumPublicoMapper.class);
    MidiaPublicaUrlService urls = mock(MidiaPublicaUrlService.class);
    StoryAnuncioApresentacaoService apresentacao =
        mock(StoryAnuncioApresentacaoService.class);
    StoryFeedPublicoService feedService = new StoryFeedPublicoService(
        selecoes, storyRepository, midiaRepository, arquivoRepository,
        anuncioRepository, usuarios, elegibilidade, acesso, idades,
        premium, urls, apresentacao);

    when(selecoes.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of());
    OffsetDateTime feedAgora = OffsetDateTime.now(ZoneOffset.UTC);
    StoryAnuncioEntity feedStoryA = StoryAnuncioEntity.criarAutogestao(
        storyA.getId(), ANUNCIO_ID, null, ModoConteudoStory.ANUNCIO, storyA.getAtivacaoBeneficioId(),
        storyA.getIdempotencyKey(), storyA.getRequestFingerprint(), feedAgora.minusMinutes(1),
        feedAgora.plusHours(1), USUARIO_ID);
    StoryAnuncioEntity feedStoryB = StoryAnuncioEntity.criarAutogestao(
        storyB.getId(), ANUNCIO_B_ID, null, ModoConteudoStory.ANUNCIO, storyB.getAtivacaoBeneficioId(),
        storyB.getIdempotencyKey(), storyB.getRequestFingerprint(), feedAgora.minusMinutes(1),
        feedAgora.plusHours(1), USUARIO_ID);
    when(storyRepository.findByStatusOrderByOrdemAscCriadoEmAscIdAsc(
        StatusStoryAnuncio.PUBLICADO)).thenReturn(List.of(feedStoryA, feedStoryB));
    when(anuncioRepository.findAllById(any())).thenReturn(List.of(anuncio, anuncioB));
    when(usuarios.findAllById(any())).thenReturn(List.of(usuario));
    when(elegibilidade.listarPorAnuncios(any())).thenReturn(Map.of());
    when(premium.flagsPorAnuncios(any())).thenReturn(Map.of());
    when(premium.flags(any())).thenReturn(PremiumPublicoFlagsDto.vazio());
    when(idades.resolverPorAnuncios(any(), any())).thenReturn(Map.of());

    var feed = feedService.listar(new MockHttpServletRequest());

    assertThat(feed).singleElement().satisfies(bundle ->
        assertThat(bundle.itens())
            .extracting(item -> item.storyId())
            .containsExactly(storyA.getId().toString(), storyB.getId().toString()));
    verify(ativacaoRepository).findVigentesByCodigoForUpdate(
        eq(ANUNCIO_ID), eq(USUARIO_ID), eq(STORIES),
        eq(StatusAtivacaoBeneficio.ATIVA), any());
    verify(ativacaoRepository).findVigentesByCodigoForUpdate(
        eq(ANUNCIO_B_ID), eq(USUARIO_ID), eq(STORIES),
        eq(StatusAtivacaoBeneficio.ATIVA), any());
  }

  @Test
  void modoMidiaUploadProcessaFotoPrivadaEExclusivaSemEntrarNaGaleria() {
    MultipartFile multipart = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());

    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(multipart), "story-foto-1", authentication, "req-2");

    assertThat(response.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
    assertThat(response.tipoMidia()).isEqualTo("FOTO");
    assertThat(response.estadoMidia()).isEqualTo("DISPONIVEL");
    assertThat(midias).singleElement().satisfies(midia -> {
      assertThat(midia.getTipo()).isEqualTo(TipoAnuncioMidia.STORY);
      assertThat(midia.getFinalidade()).isEqualTo(FinalidadeAnuncioMidia.STORY);
      assertThat(midia.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
      assertThat(midia.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
    });
    assertThat(arquivos.values()).singleElement().satisfies(arquivo ->
        assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO));
    verify(arquivoRepository, times(2)).saveAndFlush(any());
    assertThat(arquivos).hasSize(1);
    assertThat(objetos).hasSize(1);
    assertThat(stories).singleElement().satisfies(story ->
        assertThat(story.getAnuncioMidiaId()).isEqualTo(midias.get(0).getId()));
  }

  @Test
  void modoMidiaUploadAceitaVideoCanonicoSempreRestrito() {
    MultipartFile multipart = new MockMultipartFile("arquivo", "story.mp4", "video/mp4", new byte[] {4, 5, 6});
    when(validator.validarStory(multipart)).thenReturn(validada(true));

    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(multipart), "story-video-1", authentication, "req-3");

    assertThat(response.tipoMidia()).isEqualTo("VIDEO");
    assertThat(midias).singleElement().satisfies(midia -> {
      assertThat(midia.getTipo()).isEqualTo(TipoAnuncioMidia.STORY);
      assertThat(midia.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
    });
    verify(fotoProcessor, never()).processar(any());
  }

  @Test
  void inicioDoStoryEhDefinidoSomenteDepoisDoProcessamentoDoUpload() {
    Clock progressivo = mock(Clock.class);
    when(progressivo.getZone()).thenReturn(ZoneOffset.UTC);
    when(progressivo.instant()).thenReturn(
        AGORA.toInstant(),
        AGORA.plusMinutes(7).toInstant());
    service = serviceComClock(progressivo);
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());

    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(multipart), "story-upload-lento", authentication, "req-tempo");

    assertThat(response.inicioEm()).isEqualTo(AGORA.plusMinutes(7));
    assertThat(response.fimEm()).isEqualTo(FIM.minusHours(1));
  }

  @Test
  void validaCardinalidadeDoArquivoParaOsDoisModos() {
    MultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[] {1});
    assertStatus(() -> service.publicar(
        SLUG, "ANUNCIO", List.of(arquivo), "anuncio-com-arquivo", authentication, "req"), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(), "midia-sem-arquivo", authentication, "req"), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(arquivo, arquivo), "midia-dois-arquivos", authentication, "req"), HttpStatus.BAD_REQUEST);
    verify(validator, never()).validarStory(any());
  }

  @Test
  void recusaTerceiroEAnuncioForaDoEstadoPublicavel() {
    anuncio = anuncio(StatusAnuncio.PUBLICADO, UUID.randomUUID());
    when(anuncioRepository.findBySlugForLifecycle(SLUG)).thenReturn(Optional.of(anuncio));
    assertStatus(() -> service.publicar(
        SLUG, "ANUNCIO", List.of(), "terceiro", authentication, "req"), HttpStatus.FORBIDDEN);

    anuncio = anuncio(StatusAnuncio.PAUSADO, USUARIO_ID);
    when(anuncioRepository.findBySlugForLifecycle(SLUG)).thenReturn(Optional.of(anuncio));
    assertStatus(() -> service.publicar(
        SLUG, "ANUNCIO", List.of(), "pausado", authentication, "req"), HttpStatus.CONFLICT);
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
  }

  @Test
  void exigeBeneficioStoriesVigenteSemDebitarNovamente() {
    when(beneficioService.calcular(any(), any())).thenReturn(List.of());

    assertStatus(() -> service.publicar(
        SLUG, "ANUNCIO", List.of(), "sem-beneficio", authentication, "req"), HttpStatus.CONFLICT);

    verify(storyRepository, never()).save(any());
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
  }

  @Test
  void retryDaMesmaIntencaoRetornaStoryAntesDeReprocessar() {
    StoryAnuncioEntity existente = StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), ANUNCIO_ID, null, ModoConteudoStory.ANUNCIO,
        ativacao.getId(), "retry-igual", fingerprint("ANUNCIO"),
        AGORA.minusMinutes(5), FIM, USUARIO_ID);
    stories.add(existente);

    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "ANUNCIO", List.of(), "retry-igual", authentication, "req-retry");

    assertThat(response.storyId()).isEqualTo(existente.getId());
    assertThat(stories).containsExactly(existente);
    verify(validator, never()).validarStory(any());
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void retryDaMesmaMidiaRetornaStorySemReprocessar() {
    byte[] conteudo = {7, 8, 9};
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "story.mp4", "video/mp4", conteudo);
    StoryAnuncioEntity existente = StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), ANUNCIO_ID, UUID.randomUUID(), ModoConteudoStory.MIDIA_UPLOAD,
        ativacao.getId(), "retry-midia-igual", fingerprintUpload(conteudo),
        AGORA.minusMinutes(5), FIM, USUARIO_ID);
    MeuAnuncioStoryDto esperado = new MeuAnuncioStoryDto(
        existente.getId(), ANUNCIO_ID, "MIDIA_UPLOAD", "VIDEO", "PUBLICADO",
        existente.getInicioEm(), existente.getFimEm(), "DISPONIVEL");
    stories.add(existente);
    when(storyConsultaService.consultar(existente)).thenReturn(esperado);

    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(multipart), "retry-midia-igual", authentication, "req-retry");

    assertThat(response).isEqualTo(esperado);
    verify(validator, never()).validarStory(any());
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void mesmaChaveComArquivoDiferenteEhConflitoAntesDoProcessamento() {
    byte[] original = {7, 8, 9};
    StoryAnuncioEntity existente = StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), ANUNCIO_ID, UUID.randomUUID(), ModoConteudoStory.MIDIA_UPLOAD,
        ativacao.getId(), "retry-midia-diferente", fingerprintUpload(original),
        AGORA.minusMinutes(5), FIM, USUARIO_ID);
    stories.add(existente);
    MultipartFile diferente = new MockMultipartFile(
        "arquivo", "story.mp4", "video/mp4", new byte[] {7, 8, 0});

    assertStatus(() -> service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(diferente), "retry-midia-diferente", authentication, "req"),
        HttpStatus.CONFLICT);

    verify(validator, never()).validarStory(any());
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
  }

  @Test
  void storyAtivoRecusaNovaIntencaoComCodigoEstavel() {
    stories.add(StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), ANUNCIO_ID, null, ModoConteudoStory.ANUNCIO,
        ativacao.getId(), "primeira-intencao", fingerprint("ANUNCIO"),
        AGORA.minusMinutes(5), FIM, USUARIO_ID));

    assertThatThrownBy(() -> service.publicar(
        SLUG, "ANUNCIO", List.of(), "nova-intencao", authentication, "req"))
        .isInstanceOf(StoryJaAtivoException.class);
    verify(storyRepository, never()).save(any());
  }

  @Test
  void expiradoEhPersistidoAntesDoNovoStoryParaLiberarIndiceUnico() {
    AtivacaoBeneficioEntity segunda = AtivacaoBeneficioEntity.criarFixtureHomologacao(
        UUID.randomUUID(), UUID.randomUUID(), USUARIO_ID, ANUNCIO_ID, grupo.getId(),
        OrigemBeneficio.CREDITO, AGORA.minusHours(1), FIM.plusHours(24),
        StatusAtivacaoBeneficio.ATIVA, 5, BigDecimal.ZERO, "beneficio-story-indice", AGORA.minusHours(1));
    StoryAnuncioEntity expirado = StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), ANUNCIO_ID, null, ModoConteudoStory.ANUNCIO,
        ativacao.getId(), "expirado", fingerprint("ANUNCIO"),
        AGORA.minusDays(2), AGORA.minusDays(1), USUARIO_ID);
    stories.add(expirado);
    when(ativacaoRepository.findVigentesByCodigoForUpdate(
        eq(ANUNCIO_ID), eq(USUARIO_ID), eq(STORIES), eq(StatusAtivacaoBeneficio.ATIVA), any()))
        .thenReturn(List.of(ativacao, segunda));
    when(beneficioService.calcular(any(), any())).thenReturn(List.of(
        calculado(ativacao, beneficio(ativacao)), calculado(segunda, beneficio(segunda))));

    service.publicar(SLUG, "ANUNCIO", List.of(), "depois-expiracao", authentication, "req");

    assertThat(expirado.getStatus()).isEqualTo(StatusStoryAnuncio.EXPIRADO);
    verify(storyRepository, times(2)).flush();
    assertThat(stories).hasSize(2);
  }

  @Test
  void ativacaoConsumidaPorStoryExpiradoNaoEhReutilizada() {
    AtivacaoBeneficioEntity segunda = AtivacaoBeneficioEntity.criarFixtureHomologacao(
        UUID.randomUUID(), UUID.randomUUID(), USUARIO_ID, ANUNCIO_ID, grupo.getId(),
        OrigemBeneficio.CREDITO, AGORA.minusHours(1), FIM.plusHours(24),
        StatusAtivacaoBeneficio.ATIVA, 5, BigDecimal.ZERO, "beneficio-story-2", AGORA.minusHours(1));
    BeneficioPremiumEntity beneficioUm = beneficio(ativacao);
    BeneficioPremiumEntity beneficioDois = beneficio(segunda);
    stories.add(StoryAnuncioEntity.criarAutogestao(
        UUID.randomUUID(), ANUNCIO_ID, null, ModoConteudoStory.ANUNCIO,
        ativacao.getId(), "ativacao-consumida", fingerprint("ANUNCIO"),
        AGORA.minusDays(2), AGORA.minusDays(1), USUARIO_ID));
    when(ativacaoRepository.findVigentesByCodigoForUpdate(
        eq(ANUNCIO_ID), eq(USUARIO_ID), eq(STORIES), eq(StatusAtivacaoBeneficio.ATIVA), any()))
        .thenReturn(List.of(ativacao, segunda));
    when(beneficioService.calcular(any(), any())).thenReturn(List.of(
        calculado(ativacao, beneficioUm), calculado(segunda, beneficioDois)));

    service.publicar(SLUG, "ANUNCIO", List.of(), "nova-ativacao", authentication, "req");

    assertThat(stories.get(1).getAtivacaoBeneficioId()).isEqualTo(segunda.getId());
  }

  @Test
  void falhaDeStorageNaoConfirmaStoryNemRetornaSucessoFalso() {
    MultipartFile multipart = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
        .thenThrow(new IllegalStateException("storage offline"));

    assertStatus(() -> service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(multipart), "storage-falhou", authentication, "req"),
        HttpStatus.SERVICE_UNAVAILABLE);
    verify(storyRepository, never()).save(any());
    assertThat(midias).isEmpty();
  }

  @Test
  void falhaDeProcessamentoNaoPublicaNemIniciaVigencia() {
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenThrow(new IllegalStateException("processamento falhou"));

    assertThatThrownBy(() -> service.publicar(
        SLUG, "MIDIA_UPLOAD", List.of(multipart), "processamento-falhou", authentication, "req"))
        .isInstanceOf(IllegalStateException.class);
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    verify(storyRepository, never()).save(any());
    assertThat(midias).isEmpty();
    assertThat(arquivos).isEmpty();
  }

  @Test
  void falhaAoPersistirArquivoValidadoExecutaCleanupENaoPublica() {
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());
    org.mockito.Mockito.doAnswer(invocation ->
        persistirComoJpa((ArquivoMidiaEntity) invocation.getArgument(0)))
        .doThrow(new IllegalStateException("persistencia indisponivel"))
        .when(arquivoRepository).saveAndFlush(any());

    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThatThrownBy(() -> service.publicar(
          SLUG, "MIDIA_UPLOAD", List.of(multipart), "persistencia-falhou", authentication, "req"))
          .isInstanceOf(IllegalStateException.class);
      TransactionSynchronizationUtils.triggerAfterCompletion(
          TransactionSynchronization.STATUS_ROLLED_BACK);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(cleanupAuditService).limparSeOrfao(
        eq(storage), any(), eq(ANUNCIO_ID), any(), any(), any());
    verify(storyRepository, never()).save(any());
    assertThat(midias).isEmpty();
    assertThat(stories).isEmpty();
    assertThat(ativacao.getStatus()).isEqualTo(StatusAtivacaoBeneficio.ATIVA);
    assertThat(ativacao.getInicioEm()).isEqualTo(AGORA.minusHours(1));
    assertThat(ativacao.getFimEm()).isEqualTo(FIM.minusHours(1));
  }

  @Test
  void uploadQueUltrapassaFimDaAtivacaoNaoReiniciaNemProlongaVigencia() {
    Clock progressivo = mock(Clock.class);
    when(progressivo.getZone()).thenReturn(ZoneOffset.UTC);
    when(progressivo.instant()).thenReturn(AGORA.toInstant(), FIM.plusSeconds(1).toInstant());
    service = serviceComClock(progressivo);
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());
    TransactionSynchronizationManager.initSynchronization();
    try {
      assertStatus(() -> service.publicar(
          SLUG, "MIDIA_UPLOAD", List.of(multipart), "expirou-no-upload", authentication, "req-expirou"),
          HttpStatus.CONFLICT);
      TransactionSynchronizationUtils.triggerAfterCompletion(
          TransactionSynchronization.STATUS_ROLLED_BACK);
      assertThat(ativacao.getInicioEm()).isEqualTo(AGORA.minusHours(1));
      assertThat(ativacao.getFimEm()).isEqualTo(FIM.minusHours(1));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(cleanupAuditService).limparSeOrfao(
        eq(storage), any(), eq(ANUNCIO_ID), any(), any(), any());
    assertThat(stories).isEmpty();
  }

  @Test
  void ativacaoAguardandoComecaSomenteQuandoStoryFicaPublicavel() {
    UUID beneficioId = UUID.randomUUID();
    UUID opcaoId = UUID.randomUUID();
    UUID grupoId = UUID.randomUUID();
    AtivacaoBeneficioEntity aguardando = AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
        UUID.randomUUID(), beneficioId, opcaoId, USUARIO_ID, ANUNCIO_ID, grupoId,
        5, "story-aguardando", AGORA.minusDays(2));
    BeneficioPremiumOpcaoEntity opcao = BeneficioPremiumOpcaoEntity.criar(
        opcaoId, beneficioId, 7, 5, true, 0, AGORA.minusDays(3));
    GrupoAtivacaoBeneficioEntity grupoAguardando = GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
        grupoId, TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.CREDITO,
        USUARIO_ID, ANUNCIO_ID, AGORA.minusDays(2), AGORA.minusDays(1),
        StatusGrupoAtivacaoBeneficio.EXPIRADO, "grupo-story-aguardando", AGORA.minusDays(2));
    PremiumBeneficioCalculado pendente = new PremiumBeneficioCalculado(
        aguardando,
        BeneficioPremiumEntity.criarFixtureHomologacao(
            beneficioId, STORIES, "Stories", "Story por sete dias",
            EscopoBeneficioPremium.ANUNCIO, false, true, AGORA.minusDays(3)),
        grupoAguardando,
        PremiumBeneficioStatusCalculado.PENDENTE,
        List.of(),
        false,
        false);
    when(ativacaoRepository.findVigentesByCodigoForUpdate(
        eq(ANUNCIO_ID), eq(USUARIO_ID), eq(STORIES), eq(StatusAtivacaoBeneficio.ATIVA), any()))
        .thenReturn(List.of());
    when(ativacaoRepository.findAguardandoUsoByCodigoForUpdate(
        ANUNCIO_ID, USUARIO_ID, STORIES, StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO))
        .thenReturn(List.of(aguardando));
    when(beneficioService.calcular(eq(List.of(aguardando)), any())).thenReturn(List.of(pendente));
    when(opcaoRepository.findById(opcaoId)).thenReturn(Optional.of(opcao));
    when(grupoRepository.findByIdForUpdate(grupoId)).thenReturn(Optional.of(grupoAguardando));

    MeuAnuncioStoryDto response = service.publicar(
        SLUG, "ANUNCIO", List.of(), "story-aguardando-publicacao", authentication, "req-aguardando");

    assertThat(response.inicioEm()).isEqualTo(AGORA);
    assertThat(response.fimEm()).isEqualTo(AGORA.plusHours(24));
    assertThat(aguardando.getStatus()).isEqualTo(StatusAtivacaoBeneficio.ATIVA);
    assertThat(aguardando.getInicioEm()).isEqualTo(AGORA);
    assertThat(aguardando.getFimEm()).isEqualTo(AGORA.plusHours(24));
    assertThat(grupoAguardando.getValidadeFimEm()).isEqualTo(AGORA.plusHours(24));
  }

  @Test
  void rollbackNaoApagaObjetoQueJaExistiaAntesDaTentativa() {
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
        .thenReturn(ObjectWriteResult.ALREADY_EXISTS);
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), any()))
        .thenReturn(new StoredObject(new byte[] {9, 8, 7}, "image/jpeg"));
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.publicar(
          SLUG, "MIDIA_UPLOAD", List.of(multipart), "objeto-preexistente", authentication, "req-existente");
      TransactionSynchronizationUtils.triggerAfterCompletion(
          TransactionSynchronization.STATUS_ROLLED_BACK);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(storage, never()).delete(any(), any());
  }

  @Test
  void falhaNoCleanupDeRollbackEhAuditadaSemExporChave() {
    MultipartFile multipart = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(validator.validarStory(multipart)).thenReturn(validada(false));
    when(fotoProcessor.processar(any())).thenReturn(processada());
    org.mockito.Mockito.doThrow(new IllegalStateException("persistencia indisponivel"))
        .when(storyRepository).save(any());
    org.mockito.Mockito.doThrow(new IllegalStateException("storage indisponivel"))
        .when(cleanupAuditService).limparSeOrfao(
            eq(storage), any(), eq(ANUNCIO_ID), any(), any(), any());
    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThatThrownBy(() -> service.publicar(
          SLUG, "MIDIA_UPLOAD", List.of(multipart), "cleanup-auditado", authentication, "req-cleanup"))
          .isInstanceOf(IllegalStateException.class);
      TransactionSynchronizationUtils.triggerAfterCompletion(
          TransactionSynchronization.STATUS_ROLLED_BACK);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(cleanupAuditService).registrar(any(), eq(USUARIO_ID), eq("req-cleanup"));
  }

  private ArquivoMidiaEntity persistirComoJpa(ArquivoMidiaEntity arquivo) {
    ArquivoMidiaEntity existente = arquivos.get(arquivo.getId());
    if (existente != null) {
      arquivos.put(arquivo.getId(), arquivo);
      return arquivo;
    }

    ArquivoMidiaEntity gerenciado = ArquivoMidiaEntity.criarUploadPendente(
        arquivo.getId(),
        arquivo.getStorageProvider(),
        arquivo.getBucket(),
        arquivo.getChaveObjeto(),
        arquivo.getNomeOriginal(),
        arquivo.getMimeType(),
        arquivo.getTamanhoBytes(),
        arquivo.getLargura(),
        arquivo.getAltura(),
        arquivo.getDuracaoMs(),
        arquivo.getSha256(),
        arquivo.getCriadoEm());
    if (arquivo.getPipelineVersao() != null) {
      gerenciado.registrarProcessamento(
          arquivo.getPipelineVersao(),
          arquivo.getMarcaDaguaVersao(),
          arquivo.getProcessadoEm(),
          arquivo.getSha256Origem());
    }
    arquivos.put(gerenciado.getId(), gerenciado);
    return gerenciado;
  }

  private AnuncioEntity anuncio(StatusAnuncio status, UUID usuarioId) {
    return anuncio(ANUNCIO_ID, SLUG, status, usuarioId);
  }

  private AnuncioEntity anuncio(
      UUID anuncioId, String slug, StatusAnuncio status, UUID usuarioId) {
    return AnuncioEntity.criarFixtureHomologacao(
        anuncioId, usuarioId, slug, "Anuncio QA", "Descricao publica",
        status, StatusModeracaoAnuncio.APROVADO, AGORA.minusDays(1));
  }

  private MidiaValidada validada(boolean video) {
    return new MidiaValidada(
        video ? new byte[] {4, 5, 6} : new byte[] {1, 2, 3},
        video,
        video ? "video/mp4" : "image/jpeg",
        video ? "mp4" : "jpg",
        video ? "story.mp4" : "foto.jpg",
        video ? 720 : 800,
        video ? 1280 : 1200,
        video ? 15_000L : null,
        video ? "b".repeat(64) : "a".repeat(64));
  }

  private FotoProcessada processada() {
    return new FotoProcessada(
        new byte[] {9, 8, 7}, "image/jpeg", "jpg", 800, 1200,
        "c".repeat(64), "a".repeat(64), 1, "oficial-v1", AGORA);
  }

  private BeneficioPremiumEntity beneficio(AtivacaoBeneficioEntity item) {
    return BeneficioPremiumEntity.criarFixtureHomologacao(
        item.getBeneficioId(), STORIES, "Stories", "Story por 24 horas",
        EscopoBeneficioPremium.ANUNCIO, false, true, AGORA.minusDays(1));
  }

  private PremiumBeneficioCalculado calculado(
      AtivacaoBeneficioEntity item,
      BeneficioPremiumEntity beneficio) {
    return new PremiumBeneficioCalculado(
        item, beneficio, null, PremiumBeneficioStatusCalculado.ATIVO,
        List.of(), false, false);
  }

  private MeuAnuncioStoryDto dto(StoryAnuncioEntity story) {
    AnuncioMidiaEntity midia = story.getAnuncioMidiaId() == null ? null : midias.stream()
        .filter(item -> item.getId().equals(story.getAnuncioMidiaId()))
        .findFirst()
        .orElse(null);
    String tipo = midia == null ? null : arquivos.get(midia.getArquivoMidiaId()).getMimeType().startsWith("video/")
        ? "VIDEO" : "FOTO";
    return new MeuAnuncioStoryDto(
        story.getId(), story.getAnuncioId(), story.getModoConteudoEfetivo().name(), tipo,
        story.getStatus().name(), story.getInicioEm(), story.getFimEm(),
        midia == null ? null : "DISPONIVEL");
  }

  private R2StorageProperties properties() {
    R2StorageProperties value = new R2StorageProperties();
    value.setEnabled(true);
    value.setPrivateMediaBucket("midias-privadas");
    value.setPrivateMediaPrefix("hml/midias-pendentes/");
    return value;
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }

  private String fingerprint(String value) {
    try {
      return java.util.HexFormat.of().formatHex(
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String fingerprintUpload(byte[] bytes) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      digest.update(("MIDIA_UPLOAD:" + bytes.length + ":")
          .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      digest.update(bytes);
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
