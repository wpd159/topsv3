package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryMidiaCleanupService {

  private static final String ACAO_SUCESSO = "STORY_MIDIA_CLEANUP_R2_CONCLUIDO";
  private static final String ACAO_FALHA = "STORY_MIDIA_CLEANUP_R2_FALHOU";

  private final StoryAnuncioRepository storyRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AnuncioMidiaRepository midiaRepository;
  private final AnuncioRepository anuncioRepository;
  private final DocumentoUsuarioRepository documentoRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;

  public StoryMidiaCleanupService(
      StoryAnuncioRepository storyRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioMidiaRepository midiaRepository,
      AnuncioRepository anuncioRepository,
      DocumentoUsuarioRepository documentoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this.storyRepository = storyRepository;
    this.arquivoRepository = arquivoRepository;
    this.midiaRepository = midiaRepository;
    this.anuncioRepository = anuncioRepository;
    this.documentoRepository = documentoRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Resultado limpar(UUID storyId, UUID atorId, String requestId) {
    StoryAnuncioEntity story = storyRepository.findByIdForUpdate(storyId).orElse(null);
    if (story == null
        || story.getModoConteudoEfetivo() != ModoConteudoStory.MIDIA_UPLOAD
        || story.getStatus() != StatusStoryAnuncio.REMOVIDO
        || story.getEncerradoEm() == null) {
      return Resultado.NAO_APLICAVEL;
    }
    try {
      Alvo alvo = resolverAlvo(story);
      if (alvo.arquivo().getStatusArquivo() == StatusArquivoMidia.REMOVIDO) {
        return Resultado.JA_LIMPO;
      }
      validarExclusividade(story, alvo);
      excluirObjeto(alvo.arquivo());
      alvo.arquivo().aplicarDecisao(StatusArquivoMidia.REMOVIDO);
      arquivoRepository.save(alvo.arquivo());
      if (alvo.vinculo() != null
          && alvo.vinculo().getStatus() != StatusAnuncioMidia.REMOVIDA) {
        alvo.vinculo().removerLogicamente(OffsetDateTime.now(ZoneOffset.UTC));
        midiaRepository.save(alvo.vinculo());
      }
      registrarSucesso(story, atorId, requestId);
      return Resultado.CONCLUIDO;
    } catch (RuntimeException exception) {
      registrarFalha(story, atorId, requestId, codigo(exception));
      return Resultado.FALHOU;
    }
  }

  private Alvo resolverAlvo(StoryAnuncioEntity story) {
    if (story.getArquivoMidiaId() != null) {
      if (story.getAnuncioId() != null || story.getAnuncioMidiaId() != null) {
        throw falha("VINCULO_DIRETO_INCONSISTENTE");
      }
      ArquivoMidiaEntity arquivo = arquivoRepository
          .findByIdForUpdate(story.getArquivoMidiaId())
          .orElseThrow(() -> falha("ARQUIVO_AUSENTE"));
      validarChaveDireta(story, arquivo);
      return new Alvo(arquivo, null);
    }
    UUID vinculoId = story.getAnuncioMidiaId();
    if (vinculoId == null || story.getAnuncioId() == null) {
      throw falha("VINCULO_HISTORICO_AUSENTE");
    }
    AnuncioMidiaEntity vinculo = midiaRepository.findByIdForUpdate(vinculoId)
        .orElseThrow(() -> falha("VINCULO_HISTORICO_AUSENTE"));
    if (vinculo.getFinalidade() != FinalidadeAnuncioMidia.STORY
        || vinculo.getTipo() != TipoAnuncioMidia.STORY
        || !story.getAnuncioId().equals(vinculo.getAnuncioId())) {
      throw falha("FINALIDADE_STORY_INVALIDA");
    }
    AnuncioEntity anuncio = anuncioRepository.findById(story.getAnuncioId())
        .orElseThrow(() -> falha("ANUNCIO_HISTORICO_AUSENTE"));
    if (!Objects.equals(anuncio.getUsuarioId(), story.getCriadoPor())) {
      throw falha("OWNERSHIP_INCONSISTENTE");
    }
    ArquivoMidiaEntity arquivo = arquivoRepository.findByIdForUpdate(vinculo.getArquivoMidiaId())
        .orElseThrow(() -> falha("ARQUIVO_AUSENTE"));
    validarChavePrivada(arquivo);
    return new Alvo(arquivo, vinculo);
  }

  private void validarExclusividade(StoryAnuncioEntity story, Alvo alvo) {
    UUID arquivoId = alvo.arquivo().getId();
    if (documentoRepository
        .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoId)) {
      throw falha("ARQUIVO_DOCUMENTAL_VINCULADO");
    }
    long referenciasDiretas = storyRepository.countByArquivoMidiaId(arquivoId);
    if (referenciasDiretas != (story.getArquivoMidiaId() == null ? 0 : 1)) {
      throw falha("ARQUIVO_COMPARTILHADO_COM_OUTRO_STORY");
    }
    List<AnuncioMidiaEntity> vinculos = midiaRepository.findByArquivoMidiaId(arquivoId);
    boolean compartilhado = vinculos.stream()
        .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
        .anyMatch(item -> alvo.vinculo() == null || !item.getId().equals(alvo.vinculo().getId()));
    if (compartilhado) {
      throw falha("ARQUIVO_COMPARTILHADO_COM_OUTRA_MIDIA");
    }
    if (alvo.vinculo() != null
        && storyRepository.countByAnuncioMidiaId(alvo.vinculo().getId()) != 1) {
      throw falha("VINCULO_COMPARTILHADO_COM_OUTRO_STORY");
    }
  }

  private void excluirObjeto(ArquivoMidiaEntity arquivo) {
    validarChavePrivada(arquivo);
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw falha("STORAGE_INDISPONIVEL");
    }
    String key = arquivo.getChaveObjeto();
    boolean existe = storage.exists(StorageArea.PRIVATE_MEDIA, key);
    if (existe) {
      storage.delete(StorageArea.PRIVATE_MEDIA, key);
    }
    if (storage.exists(StorageArea.PRIVATE_MEDIA, key)) {
      throw falha("OBJETO_PERMANECE_NO_R2");
    }
  }

  private void validarChaveDireta(StoryAnuncioEntity story, ArquivoMidiaEntity arquivo) {
    validarChavePrivada(arquivo);
    String prefixoEsperado = storageProperties.getPrivateMediaPrefix()
        + "stories/contas/" + story.getCriadoPor() + "/" + story.getId()
        + "/" + arquivo.getId() + "/";
    if (!arquivo.getChaveObjeto().startsWith(prefixoEsperado)) {
      throw falha("OWNERSHIP_STORAGE_INCONSISTENTE");
    }
  }

  private void validarChavePrivada(ArquivoMidiaEntity arquivo) {
    if (!"R2".equals(arquivo.getStorageProvider())
        || !Objects.equals(storageProperties.getPrivateMediaBucket(), arquivo.getBucket())
        || arquivo.getChaveObjeto() == null
        || !arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix())
        || arquivo.getChaveObjeto().length() <= storageProperties.getPrivateMediaPrefix().length()
        || arquivo.getChaveObjeto().startsWith(storageProperties.getDocumentPrefix())) {
      throw falha("REFERENCIA_R2_FORA_DA_AREA_STORY");
    }
  }

  private void registrarSucesso(
      StoryAnuncioEntity story,
      UUID atorId,
      String requestId) {
    if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
        ACAO_SUCESSO, story.getId(), requestId)) {
      return;
    }
    auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
        UUID.randomUUID(),
        atorId,
        ACAO_SUCESSO,
        "STORY_ANUNCIO",
        story.getId(),
        null,
        "{\"resultado\":\"CONCLUIDO\",\"escopo\":\"MIDIA_UPLOAD\"}",
        requestId,
        OffsetDateTime.now(ZoneOffset.UTC)));
  }

  private void registrarFalha(
      StoryAnuncioEntity story,
      UUID atorId,
      String requestId,
      String codigo) {
    if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
        ACAO_FALHA, story.getId(), requestId)) {
      return;
    }
    auditoriaRepository.save(AuditoriaEventoEntity.registrarErro(
        UUID.randomUUID(),
        atorId,
        ACAO_FALHA,
        "STORY_ANUNCIO",
        story.getId(),
        null,
        "{\"resultado\":\"ERRO\",\"codigo\":\"" + codigo + "\"}",
        requestId,
        OffsetDateTime.now(ZoneOffset.UTC)));
  }

  private String codigo(RuntimeException exception) {
    return exception instanceof CleanupException cleanup
        ? cleanup.codigo
        : "FALHA_CLEANUP_STORY";
  }

  private CleanupException falha(String codigo) {
    return new CleanupException(codigo);
  }

  public enum Resultado {
    CONCLUIDO,
    JA_LIMPO,
    FALHOU,
    NAO_APLICAVEL
  }

  private record Alvo(ArquivoMidiaEntity arquivo, AnuncioMidiaEntity vinculo) {
  }

  private static final class CleanupException extends RuntimeException {
    private final String codigo;

    private CleanupException(String codigo) {
      super(codigo);
      this.codigo = codigo;
    }
  }
}
