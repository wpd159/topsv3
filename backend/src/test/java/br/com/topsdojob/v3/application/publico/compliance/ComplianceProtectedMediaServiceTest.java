package br.com.topsdojob.v3.application.publico.compliance;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ComplianceProtectedMediaServiceTest {

  private static final String PRIVATE_BUCKET = "midias-privadas";
  private static final String PRIVATE_PREFIX = "hml/preprod/midias-pendentes/";
  private static final String DOCUMENT_PREFIX = PRIVATE_PREFIX + "documentos/";

  private ComplianceVisitorAccessService accessService;
  private AnuncioMidiaRepository midiaRepository;
  private ArquivoMidiaRepository arquivoRepository;
  private AnuncioRepository anuncioRepository;
  private StoryAnuncioRepository storyRepository;
  private UsuarioRepository usuarioRepository;
  private ObjectStorage storage;
  private HttpServletRequest request;
  private ComplianceProtectedMediaService service;

  @BeforeEach
  void setUp() {
    accessService = mock(ComplianceVisitorAccessService.class);
    midiaRepository = mock(AnuncioMidiaRepository.class);
    arquivoRepository = mock(ArquivoMidiaRepository.class);
    anuncioRepository = mock(AnuncioRepository.class);
    storyRepository = mock(StoryAnuncioRepository.class);
    usuarioRepository = mock(UsuarioRepository.class);
    storage = mock(ObjectStorage.class);
    request = mock(HttpServletRequest.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    when(storageProvider.getIfAvailable()).thenReturn(storage);

    R2StorageProperties properties = new R2StorageProperties();
    properties.setPrivateMediaBucket(PRIVATE_BUCKET);
    properties.setPrivateMediaPrefix(PRIVATE_PREFIX);
    properties.setDocumentPrefix(DOCUMENT_PREFIX);
    service = new ComplianceProtectedMediaService(
        accessService,
        midiaRepository,
        arquivoRepository,
        anuncioRepository,
        storyRepository,
        usuarioRepository,
        storageProvider,
        properties);
  }

  @Test
  void bloqueiaOriginalAntesDaVerificacaoSemConsultarStorage() {
    when(accessService.autorizado(request, EscopoConteudoVisitante.MIDIA_RESTRITA))
        .thenReturn(false);

    assertThatThrownBy(() -> service.carregar(UUID.randomUUID(), request))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    verify(storage, never()).get(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void entregaOriginalPrivadoSomenteComTokenEContextoPublicavel() {
    UUID midiaId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    String key = PRIVATE_PREFIX + anuncioId + "/foto.jpg";
    byte[] bytes = "imagem-protegida".getBytes(StandardCharsets.UTF_8);
    when(accessService.autorizado(request, EscopoConteudoVisitante.MIDIA_RESTRITA))
        .thenReturn(true);
    when(midiaRepository.findById(midiaId))
        .thenReturn(Optional.of(midia(midiaId, anuncioId, arquivoId, VisibilidadeMidia.RESTRITA_18)));
    when(anuncioRepository.findPublicoComProprietarioAtivoPorId(anuncioId))
        .thenReturn(Optional.of(anuncio(anuncioId)));
    when(arquivoRepository.findById(arquivoId))
        .thenReturn(Optional.of(arquivo(arquivoId, key, PRIVATE_BUCKET)));
    when(storage.get(StorageArea.PRIVATE_MEDIA, key))
        .thenReturn(new StoredObject(bytes, "image/jpeg"));

    var result = service.carregar(midiaId, request);

    assertThat(result.bytes()).isEqualTo(bytes);
    assertThat(result.mimeType()).isEqualTo("image/jpeg");
  }

  @Test
  void rejeitaMidiaLivreMesmoComTokenValido() {
    UUID midiaId = UUID.randomUUID();
    when(accessService.autorizado(request, EscopoConteudoVisitante.MIDIA_RESTRITA))
        .thenReturn(true);
    when(midiaRepository.findById(midiaId))
        .thenReturn(Optional.of(
            midia(midiaId, UUID.randomUUID(), UUID.randomUUID(), VisibilidadeMidia.LIVRE)));

    assertThatThrownBy(() -> service.carregar(midiaId, request))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    verify(storage, never()).get(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void rejeitaObjetoForaDoBucketOuPrefixoPrivadoConfigurado() {
    UUID midiaId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    when(accessService.autorizado(request, EscopoConteudoVisitante.MIDIA_RESTRITA))
        .thenReturn(true);
    when(midiaRepository.findById(midiaId))
        .thenReturn(Optional.of(midia(
            midiaId,
            anuncioId,
            arquivoId,
            VisibilidadeMidia.RESTRITA_18)));
    when(anuncioRepository.findPublicoComProprietarioAtivoPorId(anuncioId))
        .thenReturn(Optional.of(anuncio(anuncioId)));
    when(arquivoRepository.findById(arquivoId))
        .thenReturn(Optional.of(arquivo(
            arquivoId,
            "hml/preprod/midias-aprovadas/foto.jpg",
            "midias-publicas")));

    assertThatThrownBy(() -> service.carregar(midiaId, request))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    verify(storage, never()).get(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void entregaStoryDiretoSomenteQuandoObjetoPertenceAoStoryEProprietario() {
    UUID storyId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    UUID proprietarioId = UUID.randomUUID();
    String key = PRIVATE_PREFIX + "stories/contas/" + proprietarioId + "/"
        + storyId + "/" + arquivoId + "/story.jpg";
    byte[] bytes = "story-protegido".getBytes(StandardCharsets.UTF_8);
    StoryAnuncioEntity story = storyDireto(storyId, arquivoId, proprietarioId);
    when(accessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
    when(storyRepository.findByIdAndStatus(storyId, StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.of(story));
    when(usuarioRepository.findById(proprietarioId)).thenReturn(Optional.of(usuarioAtivo()));
    when(arquivoRepository.findById(arquivoId))
        .thenReturn(Optional.of(arquivo(arquivoId, key, PRIVATE_BUCKET)));
    when(storage.get(StorageArea.PRIVATE_MEDIA, key))
        .thenReturn(new StoredObject(bytes, "image/jpeg"));

    var result = service.carregarStory(storyId, request);

    assertThat(result.bytes()).isEqualTo(bytes);
    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    verify(anuncioRepository, never()).findById(org.mockito.ArgumentMatchers.any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NAO_REMOVIDO", "STATUS_REMOVIDO", "DATA_REMOCAO"})
  void storyLegadoConfereEncerramentoAntesDeConsultarStorage(String estadoRemocao) {
    UUID storyId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    UUID proprietarioId = UUID.randomUUID();
    StoryAnuncioEntity story = entity(StoryAnuncioEntity.class);
    set(story, "id", storyId);
    set(story, "anuncioMidiaId", midiaId);
    set(story, "status", StatusStoryAnuncio.PUBLICADO);
    set(story, "inicioEm", OffsetDateTime.now().minusMinutes(1));
    set(story, "fimEm", OffsetDateTime.now().plusHours(1));
    AnuncioMidiaEntity vinculo = midia(midiaId, anuncioId, arquivoId, VisibilidadeMidia.RESTRITA_18);
    set(vinculo, "tipo", TipoAnuncioMidia.STORY);
    AnuncioEntity anuncio = anuncio(anuncioId);
    set(anuncio, "usuarioId", proprietarioId);
    if ("STATUS_REMOVIDO".equals(estadoRemocao)) {
      set(anuncio, "status", StatusAnuncio.REMOVIDO);
    } else if ("DATA_REMOCAO".equals(estadoRemocao)) {
      set(anuncio, "removidoEm", OffsetDateTime.now().minusSeconds(1));
    }
    when(accessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
    when(storyRepository.findByIdAndStatus(storyId, StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.of(story));
    when(midiaRepository.findById(midiaId)).thenReturn(Optional.of(vinculo));
    when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));

    if ("NAO_REMOVIDO".equals(estadoRemocao)) {
      String key = PRIVATE_PREFIX + anuncioId + "/story.jpg";
      byte[] bytes = "story-legado-protegido".getBytes(StandardCharsets.UTF_8);
      when(usuarioRepository.findById(proprietarioId)).thenReturn(Optional.of(usuarioAtivo()));
      when(arquivoRepository.findById(arquivoId))
          .thenReturn(Optional.of(arquivo(arquivoId, key, PRIVATE_BUCKET)));
      when(storage.get(StorageArea.PRIVATE_MEDIA, key)).thenReturn(new StoredObject(bytes, "image/jpeg"));

      assertThat(service.carregarStory(storyId, request).bytes()).isEqualTo(bytes);
      verify(storage).get(StorageArea.PRIVATE_MEDIA, key);
    } else {
      assertThatThrownBy(() -> service.carregarStory(storyId, request))
          .isInstanceOfSatisfying(ResponseStatusException.class, error ->
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
      verify(arquivoRepository, never()).findById(org.mockito.ArgumentMatchers.any());
      verify(storage, never()).get(
          org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
    assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.PUBLICADO);
    assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
  }

  @Test
  void rejeitaStoryDiretoApontandoParaArquivoDeOutroStory() {
    UUID storyId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    UUID proprietarioId = UUID.randomUUID();
    String keyAlheia = PRIVATE_PREFIX + "stories/contas/" + UUID.randomUUID() + "/"
        + UUID.randomUUID() + "/" + arquivoId + "/story.jpg";
    StoryAnuncioEntity story = storyDireto(storyId, arquivoId, proprietarioId);
    when(accessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
    when(storyRepository.findByIdAndStatus(storyId, StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.of(story));
    when(usuarioRepository.findById(proprietarioId)).thenReturn(Optional.of(usuarioAtivo()));
    when(arquivoRepository.findById(arquivoId))
        .thenReturn(Optional.of(arquivo(arquivoId, keyAlheia, PRIVATE_BUCKET)));

    assertThatThrownBy(() -> service.carregarStory(storyId, request))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    verify(storage, never()).get(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void rejeitaStoryDiretoApontandoParaDocumentoPrivado() {
    UUID storyId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    UUID proprietarioId = UUID.randomUUID();
    String documentKey = DOCUMENT_PREFIX + proprietarioId + "/documento.jpg";
    StoryAnuncioEntity story = storyDireto(storyId, arquivoId, proprietarioId);
    when(accessService.autorizado(request, EscopoConteudoVisitante.STORY)).thenReturn(true);
    when(storyRepository.findByIdAndStatus(storyId, StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.of(story));
    when(usuarioRepository.findById(proprietarioId)).thenReturn(Optional.of(usuarioAtivo()));
    when(arquivoRepository.findById(arquivoId))
        .thenReturn(Optional.of(arquivo(arquivoId, documentKey, PRIVATE_BUCKET)));

    assertThatThrownBy(() -> service.carregarStory(storyId, request))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    verify(storage, never()).get(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString());
  }

  private StoryAnuncioEntity storyDireto(
      UUID storyId,
      UUID arquivoId,
      UUID proprietarioId) {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    return StoryAnuncioEntity.criarMidiaUpload(
        storyId,
        arquivoId,
        UUID.randomUUID(),
        "story-protegido-" + storyId,
        "a".repeat(64),
        agora.minusMinutes(1),
        agora.plusHours(1),
        proprietarioId);
  }

  private UsuarioEntity usuarioAtivo() {
    UsuarioEntity usuario = entity(UsuarioEntity.class);
    set(usuario, "status", StatusUsuario.ATIVO);
    return usuario;
  }

  private AnuncioMidiaEntity midia(
      UUID id,
      UUID anuncioId,
      UUID arquivoId,
      VisibilidadeMidia visibilidade) {
    AnuncioMidiaEntity midia = entity(AnuncioMidiaEntity.class);
    set(midia, "id", id);
    set(midia, "anuncioId", anuncioId);
    set(midia, "arquivoMidiaId", arquivoId);
    set(midia, "status", StatusAnuncioMidia.PUBLICAVEL);
    set(midia, "visibilidadeMidia", visibilidade);
    return midia;
  }

  private AnuncioEntity anuncio(UUID id) {
    AnuncioEntity anuncio = entity(AnuncioEntity.class);
    set(anuncio, "id", id);
    set(anuncio, "status", StatusAnuncio.PUBLICADO);
    set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
    return anuncio;
  }

  private ArquivoMidiaEntity arquivo(UUID id, String key, String bucket) {
    ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
    set(arquivo, "id", id);
    set(arquivo, "storageProvider", "R2");
    set(arquivo, "bucket", bucket);
    set(arquivo, "chaveObjeto", key);
    set(arquivo, "mimeType", "image/jpeg");
    set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
    return arquivo;
  }
}
