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
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ComplianceProtectedMediaServiceTest {

  private static final String PRIVATE_BUCKET = "midias-privadas";
  private static final String PRIVATE_PREFIX = "hml/preprod/midias-pendentes/";

  private ComplianceVisitorAccessService accessService;
  private AnuncioMidiaRepository midiaRepository;
  private ArquivoMidiaRepository arquivoRepository;
  private AnuncioRepository anuncioRepository;
  private ObjectStorage storage;
  private HttpServletRequest request;
  private ComplianceProtectedMediaService service;

  @BeforeEach
  void setUp() {
    accessService = mock(ComplianceVisitorAccessService.class);
    midiaRepository = mock(AnuncioMidiaRepository.class);
    arquivoRepository = mock(ArquivoMidiaRepository.class);
    anuncioRepository = mock(AnuncioRepository.class);
    storage = mock(ObjectStorage.class);
    request = mock(HttpServletRequest.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    when(storageProvider.getIfAvailable()).thenReturn(storage);

    R2StorageProperties properties = new R2StorageProperties();
    properties.setPrivateMediaBucket(PRIVATE_BUCKET);
    properties.setPrivateMediaPrefix(PRIVATE_PREFIX);
    service = new ComplianceProtectedMediaService(
        accessService,
        midiaRepository,
        arquivoRepository,
        anuncioRepository,
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
    when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio(anuncioId)));
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
    when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio(anuncioId)));
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
