package br.com.topsdojob.v3.application.publico.anunciante;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoryUploadCleanupAuditServiceTest {

  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final UUID anuncioId = UUID.randomUUID();
  private final UUID storyId = UUID.randomUUID();
  private final UUID arquivoId = UUID.randomUUID();
  private final UUID vinculoId = UUID.randomUUID();
  private StoryUploadCleanupAuditService service;

  @BeforeEach
  void setUp() {
    service = new StoryUploadCleanupAuditService(
        anuncioRepository,
        storyRepository,
        arquivoRepository,
        midiaRepository,
        auditoriaRepository);
    when(anuncioRepository.findByIdForModeration(anuncioId))
        .thenReturn(Optional.of(mock(AnuncioEntity.class)));
  }

  @Test
  void removeSomenteOrfaoDepoisDeTravarOAnuncio() {
    service.limparSeOrfao(
        storage, "privado/story.bin", anuncioId, storyId, arquivoId, vinculoId);

    var ordem = inOrder(
        anuncioRepository, storyRepository, arquivoRepository, midiaRepository, storage);
    ordem.verify(anuncioRepository).findByIdForModeration(anuncioId);
    ordem.verify(storyRepository).existsById(storyId);
    ordem.verify(arquivoRepository).existsById(arquivoId);
    ordem.verify(midiaRepository).existsById(vinculoId);
    ordem.verify(storage).delete(StorageArea.PRIVATE_MEDIA, "privado/story.bin");
  }

  @Test
  void retryPersistidoImpedeCleanupDoMesmoObjeto() {
    when(storyRepository.existsById(storyId)).thenReturn(true);

    service.limparSeOrfao(
        storage, "privado/story.bin", anuncioId, storyId, arquivoId, vinculoId);

    verify(anuncioRepository).findByIdForModeration(anuncioId);
    verify(storage, never()).delete(StorageArea.PRIVATE_MEDIA, "privado/story.bin");
    verify(arquivoRepository, never()).existsById(arquivoId);
    verify(midiaRepository, never()).existsById(vinculoId);
  }
}
