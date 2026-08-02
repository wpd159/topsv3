package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryUploadCleanupAuditService {

  private static final String ACAO = "STORY_UPLOAD_CLEANUP_R2_FALHOU";

  private final AnuncioRepository anuncioRepository;
  private final StoryAnuncioRepository storyRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AnuncioMidiaRepository midiaRepository;
  private final AuditoriaEventoRepository auditoriaRepository;

  public StoryUploadCleanupAuditService(
      AnuncioRepository anuncioRepository,
      StoryAnuncioRepository storyRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioMidiaRepository midiaRepository,
      AuditoriaEventoRepository auditoriaRepository) {
    this.anuncioRepository = anuncioRepository;
    this.storyRepository = storyRepository;
    this.arquivoRepository = arquivoRepository;
    this.midiaRepository = midiaRepository;
    this.auditoriaRepository = auditoriaRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void limparSeOrfao(
      ObjectStorage storage,
      String key,
      UUID anuncioId,
      UUID storyId,
      UUID arquivoId,
      UUID vinculoId) {
    anuncioRepository.findByIdForModeration(anuncioId);
    if (storyRepository.existsById(storyId)
        || arquivoRepository.existsById(arquivoId)
        || midiaRepository.existsById(vinculoId)) {
      return;
    }
    storage.delete(StorageArea.PRIVATE_MEDIA, key);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(UUID storyId, UUID atorId, String requestId) {
    if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(ACAO, storyId, requestId)) {
      return;
    }
    auditoriaRepository.save(AuditoriaEventoEntity.registrarErro(
        UUID.randomUUID(),
        atorId,
        ACAO,
        "STORY_ANUNCIO",
        storyId,
        null,
        "{\"etapa\":\"ROLLBACK_STORAGE\",\"resultado\":\"ERRO\",\"codigo\":\"FALHA_CLEANUP_R2\"}",
        requestId,
        OffsetDateTime.now(ZoneOffset.UTC)));
  }
}
