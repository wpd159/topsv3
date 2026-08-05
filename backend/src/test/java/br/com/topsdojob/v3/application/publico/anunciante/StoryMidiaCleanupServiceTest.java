package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemEncerramentoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StoryMidiaCleanupServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-04T20:00:00Z");
  private static final UUID USUARIO_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");

  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
  private final R2StorageProperties properties = properties();
  private StoryMidiaCleanupService service;
  private StoryAnuncioEntity story;
  private ArquivoMidiaEntity arquivo;

  @BeforeEach
  void setUp() {
    UUID storyId = UUID.randomUUID();
    UUID arquivoId = UUID.randomUUID();
    String key = properties.getPrivateMediaPrefix()
        + "stories/contas/" + USUARIO_ID + "/" + storyId + "/" + arquivoId + "/video.mp4";
    story = StoryAnuncioEntity.criarMidiaUpload(
        storyId,
        arquivoId,
        UUID.randomUUID(),
        "cleanup-story",
        "a".repeat(64),
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        USUARIO_ID);
    story.encerrar(
        USUARIO_ID,
        OrigemEncerramentoStory.USUARIO,
        "EXCLUSAO_VOLUNTARIA",
        null,
        false,
        AGORA);
    arquivo = ArquivoMidiaEntity.criarUploadPendente(
        arquivoId,
        "R2",
        properties.getPrivateMediaBucket(),
        key,
        "video.mp4",
        "video/mp4",
        4,
        720,
        1280,
        15_000,
        "b".repeat(64),
        AGORA.minusHours(1));
    arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);

    when(storyRepository.findByIdForUpdate(storyId)).thenReturn(Optional.of(story));
    when(arquivoRepository.findByIdForUpdate(arquivoId)).thenReturn(Optional.of(arquivo));
    when(storyRepository.countByArquivoMidiaId(arquivoId)).thenReturn(1L);
    when(midiaRepository.findByArquivoMidiaId(arquivoId)).thenReturn(List.of());
    when(documentoRepository
        .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoId))
        .thenReturn(false);
    when(storageProvider.getIfAvailable()).thenReturn(storage);
    when(auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(any(), any(), any()))
        .thenReturn(false);
    when(arquivoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service = new StoryMidiaCleanupService(
        storyRepository,
        arquivoRepository,
        midiaRepository,
        anuncioRepository,
        documentoRepository,
        auditoriaRepository,
        storageProvider,
        properties);
  }

  @Test
  void removeSomenteObjetoExclusivoDepoisDoEncerramentoLogico() {
    when(storage.exists(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto()))
        .thenReturn(true, false);

    StoryMidiaCleanupService.Resultado resultado = service.limpar(
        story.getId(), USUARIO_ID, "request-cleanup");

    assertThat(resultado).isEqualTo(StoryMidiaCleanupService.Resultado.CONCLUIDO);
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.REMOVIDO);
    verify(storage).delete(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto());
    verify(arquivoRepository).save(arquivo);
    verify(auditoriaRepository).save(any());
    verify(anuncioRepository, never()).save(any());
  }

  @Test
  void objetoJaAusenteEhAceitoEArquivoFicaRemovido() {
    when(storage.exists(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto())).thenReturn(false);

    StoryMidiaCleanupService.Resultado resultado = service.limpar(
        story.getId(), USUARIO_ID, "request-ausente");

    assertThat(resultado).isEqualTo(StoryMidiaCleanupService.Resultado.CONCLUIDO);
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.REMOVIDO);
    verify(storage, never()).delete(any(), any());
  }

  @Test
  void protegeDocumentoKycEArquivoCompartilhado() {
    when(documentoRepository
        .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivo.getId()))
        .thenReturn(true);

    StoryMidiaCleanupService.Resultado kyc = service.limpar(
        story.getId(), USUARIO_ID, "request-kyc");

    assertThat(kyc).isEqualTo(StoryMidiaCleanupService.Resultado.FALHOU);
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
    verify(storage, never()).delete(any(), any());

    when(documentoRepository
        .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivo.getId()))
        .thenReturn(false);
    when(storyRepository.countByArquivoMidiaId(arquivo.getId())).thenReturn(2L);

    StoryMidiaCleanupService.Resultado compartilhado = service.limpar(
        story.getId(), USUARIO_ID, "request-compartilhado");

    assertThat(compartilhado).isEqualTo(StoryMidiaCleanupService.Resultado.FALHOU);
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
  }

  @Test
  void retryAposArquivoJaLimpoEhIdempotente() {
    arquivo.aplicarDecisao(StatusArquivoMidia.REMOVIDO);

    StoryMidiaCleanupService.Resultado resultado = service.limpar(
        story.getId(), USUARIO_ID, "request-retry");

    assertThat(resultado).isEqualTo(StoryMidiaCleanupService.Resultado.JA_LIMPO);
    verify(storage, never()).exists(any(), any());
    verify(storage, never()).delete(any(), any());
  }

  private R2StorageProperties properties() {
    R2StorageProperties value = new R2StorageProperties();
    value.setEnabled(true);
    value.setPrivateMediaBucket("midias-privadas");
    value.setPrivateMediaPrefix("hml/midias-pendentes/");
    value.setDocumentPrefix("hml/documentos/");
    return value;
  }
}
