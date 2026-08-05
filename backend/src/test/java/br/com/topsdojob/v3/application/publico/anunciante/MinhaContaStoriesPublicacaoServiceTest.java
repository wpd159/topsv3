package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesDireitoService.DireitoPublicacao;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class MinhaContaStoriesPublicacaoServiceTest {

  private static final UUID USUARIO_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID ANUNCIO_A = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID ANUNCIO_B = UUID.fromString("20000000-0000-4000-8000-000000000002");
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-04T18:00:00Z");

  private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
  private final MeuAnuncioStoryConsultaService consultaService = mock(MeuAnuncioStoryConsultaService.class);
  private final MinhaContaStoriesDireitoService direitoService = mock(MinhaContaStoriesDireitoService.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
  private final FotoUploadProcessor fotoProcessor = mock(FotoUploadProcessor.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
  private final StoryUploadCleanupAuditService cleanupAuditService = mock(StoryUploadCleanupAuditService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final List<StoryAnuncioEntity> stories = new ArrayList<>();
  private final Map<String, StoredObject> objects = new HashMap<>();
  private MinhaContaStoriesPublicacaoService service;

  @BeforeEach
  void setUp() throws Exception {
    UsuarioEntity usuario = mock(UsuarioEntity.class);
    when(usuario.getId()).thenReturn(USUARIO_ID);
    when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(storyRepository.findByCriadoPorAndModoConteudoAndAnuncioIdAndIdempotencyKey(
        any(), any(), any(), any()))
        .thenAnswer(invocation -> stories.stream()
            .filter(story -> invocation.getArgument(0).equals(story.getCriadoPor()))
            .filter(story -> invocation.getArgument(1) == story.getModoConteudoEfetivo())
            .filter(story -> invocation.getArgument(2).equals(story.getAnuncioId()))
            .filter(story -> invocation.getArgument(3).equals(story.getIdempotencyKey()))
            .findFirst());
    when(storyRepository.findByCriadoPorAndModoConteudoAndAnuncioIdIsNullAndIdempotencyKey(
        any(), any(), any()))
        .thenAnswer(invocation -> stories.stream()
            .filter(story -> invocation.getArgument(0).equals(story.getCriadoPor()))
            .filter(story -> invocation.getArgument(1) == story.getModoConteudoEfetivo())
            .filter(story -> story.getAnuncioId() == null)
            .filter(story -> invocation.getArgument(2).equals(story.getIdempotencyKey()))
            .findFirst());
    when(storyRepository.findByAnuncioIdForUpdate(any())).thenReturn(List.of());
    when(storyRepository.save(any())).thenAnswer(invocation -> {
      StoryAnuncioEntity story = invocation.getArgument(0);
      stories.add(story);
      return story;
    });
    when(arquivoRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(consultaService.consultar(any())).thenAnswer(invocation -> dto(invocation.getArgument(0)));

    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    when(ativacao.getId()).thenReturn(UUID.fromString("30000000-0000-4000-8000-000000000001"));
    DireitoPublicacao direito = new DireitoPublicacao(ativacao, mock(GrupoAtivacaoBeneficioEntity.class));
    when(direitoService.reservarParaPublicacao(any(), any(), eq(USUARIO_ID), any()))
        .thenReturn(direito);
    when(direitoService.iniciarVigencia(eq(direito), any()))
        .thenAnswer(invocation -> ((OffsetDateTime) invocation.getArgument(1)).plusHours(24));

    when(storageProvider.getIfAvailable()).thenReturn(storage);
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
        .thenAnswer(invocation -> {
          String key = invocation.getArgument(1);
          objects.put(key, new StoredObject(invocation.getArgument(2), invocation.getArgument(3)));
          return ObjectWriteResult.CREATED;
        });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), any()))
        .thenAnswer(invocation -> objects.get(invocation.getArgument(1)));
    when(validator.validarStory(any())).thenAnswer(invocation -> validada(invocation.getArgument(0)));

    service = new MinhaContaStoriesPublicacaoService(
        usuarioService,
        consultaService,
        direitoService,
        anuncioRepository,
        storyRepository,
        arquivoRepository,
        auditoriaRepository,
        validator,
        fotoProcessor,
        properties(),
        storageProvider,
        cleanupAuditService,
        Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));
  }

  @Test
  void springSelecionaOConstrutorDeProducao() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(MeusAnunciosConsultaService.class, () -> usuarioService);
      context.registerBean(MeuAnuncioStoryConsultaService.class, () -> consultaService);
      context.registerBean(MinhaContaStoriesDireitoService.class, () -> direitoService);
      context.registerBean(AnuncioRepository.class, () -> anuncioRepository);
      context.registerBean(StoryAnuncioRepository.class, () -> storyRepository);
      context.registerBean(ArquivoMidiaRepository.class, () -> arquivoRepository);
      context.registerBean(AuditoriaEventoRepository.class, () -> auditoriaRepository);
      context.registerBean(MidiaUploadValidator.class, () -> validator);
      context.registerBean(FotoUploadProcessor.class, () -> fotoProcessor);
      context.registerBean(R2StorageProperties.class, this::properties);
      context.registerBean(StoryUploadCleanupAuditService.class, () -> cleanupAuditService);
      context.registerBean(MinhaContaStoriesPublicacaoService.class);

      context.refresh();

      assertThat(context.getBean(MinhaContaStoriesPublicacaoService.class)).isNotNull();
    }
  }

  @Test
  void contaSemAnuncioPublicaMidiaDiretaComVigenciaDe24Horas() {
    MinhaContaStoryDto resposta = service.publicar(
        "MIDIA_UPLOAD", null, List.of(video("story.mp4")), "midia-1", authentication, "req-1");

    assertThat(resposta.anuncioId()).isNull();
    assertThat(resposta.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
    assertThat(resposta.fimEm()).isEqualTo(resposta.inicioEm().plusHours(24));
    assertThat(stories).singleElement().satisfies(story -> {
      assertThat(story.getAnuncioId()).isNull();
      assertThat(story.getAnuncioMidiaId()).isNull();
      assertThat(story.getArquivoMidiaId()).isNotNull();
      assertThat(story.getCriadoPor()).isEqualTo(USUARIO_ID);
    });
    verify(anuncioRepository, never()).findByIdForModeration(any());
  }

  @Test
  void anuncioPublicaSemCriarArquivoExclusivo() {
    AnuncioEntity anuncioA = anuncio(ANUNCIO_A);
    when(anuncioRepository.findByIdForModeration(ANUNCIO_A)).thenReturn(Optional.of(anuncioA));

    MinhaContaStoryDto resposta = service.publicar(
        "ANUNCIO", ANUNCIO_A, null, "anuncio-1", authentication, "req-2");

    assertThat(resposta.anuncioId()).isEqualTo(ANUNCIO_A);
    assertThat(stories).singleElement().satisfies(story -> {
      assertThat(story.getModoConteudoEfetivo()).isEqualTo(ModoConteudoStory.ANUNCIO);
      assertThat(story.getArquivoMidiaId()).isNull();
      assertThat(story.getAnuncioMidiaId()).isNull();
    });
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
  }

  @Test
  void anunciosDiferentesPodemCoexistirNaMesmaConta() {
    AnuncioEntity anuncioA = anuncio(ANUNCIO_A);
    AnuncioEntity anuncioB = anuncio(ANUNCIO_B);
    when(anuncioRepository.findByIdForModeration(ANUNCIO_A)).thenReturn(Optional.of(anuncioA));
    when(anuncioRepository.findByIdForModeration(ANUNCIO_B)).thenReturn(Optional.of(anuncioB));

    service.publicar("ANUNCIO", ANUNCIO_A, null, "mesma-chave", authentication, "req-a");
    service.publicar("ANUNCIO", ANUNCIO_B, null, "mesma-chave", authentication, "req-b");

    assertThat(stories).extracting(StoryAnuncioEntity::getAnuncioId)
        .containsExactlyInAnyOrder(ANUNCIO_A, ANUNCIO_B);
  }

  @Test
  void variasMidiasPodemCoexistirSemSingletonPorConta() {
    service.publicar("MIDIA_UPLOAD", null, List.of(video("a.mp4")), "midia-a", authentication, "req-a");
    service.publicar("MIDIA_UPLOAD", null, List.of(video("b.mp4")), "midia-b", authentication, "req-b");

    assertThat(stories).hasSize(2).allSatisfy(story -> {
      assertThat(story.getAnuncioId()).isNull();
      assertThat(story.getArquivoMidiaId()).isNotNull();
    });
  }

  @Test
  void contratoDosModosRecusaCamposIncompativeis() {
    assertStatus(() -> service.publicar(
        "ANUNCIO", null, null, "sem-anuncio", authentication, "req"), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.publicar(
        "ANUNCIO", ANUNCIO_A, List.of(video("indevido.mp4")), "com-arquivo", authentication, "req"),
        HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.publicar(
        "MIDIA_UPLOAD", ANUNCIO_A, List.of(video("indevido.mp4")), "com-anuncio", authentication, "req"),
        HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.publicar(
        "MIDIA_UPLOAD", null, List.of(), "sem-arquivo", authentication, "req"), HttpStatus.BAD_REQUEST);
    assertStatus(() -> service.publicar(
        "MIDIA_UPLOAD", null, List.of(video("a.mp4"), video("b.mp4")), "dois-arquivos", authentication, "req"),
        HttpStatus.BAD_REQUEST);
  }

  @Test
  void retryDaMesmaIntencaoRetornaMesmoStorySemNovoDireitoOuUpload() {
    var primeira = service.publicar(
        "MIDIA_UPLOAD", null, List.of(video("story.mp4")), "retry", authentication, "req-1");
    var repetida = service.publicar(
        "MIDIA_UPLOAD", null, List.of(video("story.mp4")), "retry", authentication, "req-2");

    assertThat(repetida.storyId()).isEqualTo(primeira.storyId());
    assertThat(stories).hasSize(1);
    verify(direitoService).reservarParaPublicacao(any(), any(), eq(USUARIO_ID), any());
    verify(storage).putIfAbsent(any(), any(), any(), any());
  }

  @Test
  void mesmaChaveClienteNaoColideEntreModosDistintos() {
    AnuncioEntity anuncioA = anuncio(ANUNCIO_A);
    when(anuncioRepository.findByIdForModeration(ANUNCIO_A)).thenReturn(Optional.of(anuncioA));

    service.publicar("ANUNCIO", ANUNCIO_A, null, "mesma-chave", authentication, "req-a");
    service.publicar(
        "MIDIA_UPLOAD", null, List.of(video("story.mp4")), "mesma-chave", authentication, "req-midia");

    assertThat(stories).hasSize(2);
    assertThat(stories).extracting(StoryAnuncioEntity::getModoConteudoEfetivo)
        .containsExactlyInAnyOrder(ModoConteudoStory.ANUNCIO, ModoConteudoStory.MIDIA_UPLOAD);
  }

  @Test
  void mesmaChaveNaMesmaIntencaoComArquivoDivergenteRetornaConflito() {
    service.publicar(
        "MIDIA_UPLOAD", null, List.of(video("a.mp4")), "arquivo-divergente", authentication, "req-a");

    MockMultipartFile diferente = new MockMultipartFile(
        "arquivo", "b.mp4", "video/mp4", new byte[] {9, 8, 7, 6});
    assertStatus(() -> service.publicar(
        "MIDIA_UPLOAD", null, List.of(diferente), "arquivo-divergente", authentication, "req-b"),
        HttpStatus.CONFLICT);

    assertThat(stories).hasSize(1);
  }

  @Test
  void umStoryAtivoImpedeSegundoStoryNoMesmoAnuncio() {
    AnuncioEntity anuncioA = anuncio(ANUNCIO_A);
    when(anuncioRepository.findByIdForModeration(ANUNCIO_A)).thenReturn(Optional.of(anuncioA));
    StoryAnuncioEntity existente = StoryAnuncioEntity.criarAnuncio(
        UUID.randomUUID(), ANUNCIO_A, UUID.randomUUID(), "existente",
        "a".repeat(64), AGORA.minusHours(1), AGORA.plusHours(23), USUARIO_ID);
    when(storyRepository.findByAnuncioIdForUpdate(ANUNCIO_A)).thenReturn(List.of(existente));

    assertThatThrownBy(() -> service.publicar(
        "ANUNCIO", ANUNCIO_A, null, "segundo", authentication, "req"))
        .isInstanceOf(StoryJaAtivoException.class);
    verify(direitoService, never()).reservarParaPublicacao(any(), any(), any(), any());
  }

  @Test
  void ownershipDoAnuncioVemDaSessao() {
    AnuncioEntity alheio = anuncio(ANUNCIO_A);
    when(alheio.getUsuarioId()).thenReturn(UUID.randomUUID());
    when(anuncioRepository.findByIdForModeration(ANUNCIO_A)).thenReturn(Optional.of(alheio));

    assertStatus(() -> service.publicar(
        "ANUNCIO", ANUNCIO_A, null, "alheio", authentication, "req"), HttpStatus.FORBIDDEN);
  }

  @Test
  void persistenciaCanonicaNuncaPreencheAnuncioMidia() {
    service.publicar("MIDIA_UPLOAD", null, List.of(video("story.mp4")), "direto", authentication, "req");

    ArgumentCaptor<StoryAnuncioEntity> captor = ArgumentCaptor.forClass(StoryAnuncioEntity.class);
    verify(storyRepository).save(captor.capture());
    assertThat(captor.getValue().getAnuncioMidiaId()).isNull();
  }

  private AnuncioEntity anuncio(UUID id) {
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncio.getId()).thenReturn(id);
    when(anuncio.getUsuarioId()).thenReturn(USUARIO_ID);
    when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
    when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
    when(anuncio.getRemovidoEm()).thenReturn(null);
    return anuncio;
  }

  private MockMultipartFile video(String nome) {
    return new MockMultipartFile("arquivo", nome, "video/mp4", new byte[] {1, 2, 3, 4});
  }

  private MidiaValidada validada(MultipartFile arquivo) throws Exception {
    byte[] bytes = arquivo.getBytes();
    return new MidiaValidada(
        bytes, true, "video/mp4", "mp4", arquivo.getOriginalFilename(),
        720, 1280, 15_000L, sha256(bytes));
  }

  private MinhaContaStoryDto dto(StoryAnuncioEntity story) {
    return new MinhaContaStoryDto(
        story.getId(), story.getAnuncioId(), story.getModoConteudoEfetivo().name(),
        story.getModoConteudoEfetivo() == ModoConteudoStory.MIDIA_UPLOAD ? "VIDEO" : null,
        story.getStatus().name(), story.getInicioEm(), story.getFimEm(), "DISPONIVEL");
  }

  private R2StorageProperties properties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setPrivateMediaBucket("midias-privadas");
    properties.setPrivateMediaPrefix("preprod/midias-pendentes/");
    return properties;
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
