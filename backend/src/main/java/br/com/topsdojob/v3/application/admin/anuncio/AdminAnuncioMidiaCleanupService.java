package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminAnuncioMidiaCleanupService {

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final DocumentoUsuarioRepository documentoUsuarioRepository;
  private final StoryAnuncioRepository storyRepository;
  private final StorySelecaoAdministrativaRepository storyAdminRepository;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;

  public AdminAnuncioMidiaCleanupService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      DocumentoUsuarioRepository documentoUsuarioRepository,
      StoryAnuncioRepository storyRepository,
      StorySelecaoAdministrativaRepository storyAdminRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.documentoUsuarioRepository = documentoUsuarioRepository;
    this.storyRepository = storyRepository;
    this.storyAdminRepository = storyAdminRepository;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
  }

  public Resultado limpar(UUID anuncioId, OffsetDateTime agora) {
    List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId);
    return limparVinculos(anuncioId, vinculos, agora, true);
  }

  public Resultado limparMidia(UUID anuncioId, UUID midiaId, OffsetDateTime agora) {
    List<AnuncioMidiaEntity> vinculosDoAnuncio =
        anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId);
    AnuncioMidiaEntity alvo = vinculosDoAnuncio.stream()
        .filter(item -> midiaId.equals(item.getId()))
        .findFirst()
        .orElseThrow(() -> conflito("MIDIA_NAO_PERTENCE_AO_ANUNCIO"));
    if (alvo.getTipo() != TipoAnuncioMidia.FOTO) {
      throw conflito("SOMENTE_FOTO_PODE_SER_EXCLUIDA_PELO_LOTE");
    }
    if (alvo.getStatus() != StatusAnuncioMidia.PENDENTE
        && alvo.getStatus() != StatusAnuncioMidia.AJUSTE_SOLICITADO
        && alvo.getStatus() != StatusAnuncioMidia.REMOVIDA) {
      throw conflito("TRANSICAO_DE_EXCLUSAO_INCOMPATIVEL");
    }
    if (alvo.getArquivoMidiaId() == null) {
      throw conflito("ARQUIVO_DE_MIDIA_AUSENTE");
    }

    List<AnuncioMidiaEntity> vinculosDaFoto = vinculosDoAnuncio.stream()
        .filter(item -> alvo.getArquivoMidiaId().equals(item.getArquivoMidiaId()))
        .toList();
    return limparVinculos(anuncioId, vinculosDaFoto, agora, false);
  }

  private Resultado limparVinculos(
      UUID anuncioId,
      List<AnuncioMidiaEntity> vinculos,
      OffsetDateTime agora,
      boolean encerrarStoryAdministrativo) {
    List<UUID> arquivoIds = vinculos.stream()
        .map(AnuncioMidiaEntity::getArquivoMidiaId)
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();

    Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
    if (!arquivoIds.isEmpty()) {
      arquivoMidiaRepository.findByIdInForUpdate(arquivoIds).stream()
          .sorted(Comparator.comparing(ArquivoMidiaEntity::getId))
          .forEach(item -> arquivos.put(item.getId(), item));
    }
    if (arquivos.size() != arquivoIds.size()
        || vinculos.stream().anyMatch(item -> item.getArquivoMidiaId() == null)) {
      throw conflito("ARQUIVO_DE_MIDIA_AUSENTE");
    }

    Set<UUID> arquivosCompartilhados = new LinkedHashSet<>();
    Set<UUID> arquivosExclusivos = new LinkedHashSet<>();
    Set<ObjetoStorage> objetos = new LinkedHashSet<>();
    for (ArquivoMidiaEntity arquivo : arquivos.values()) {
      if (documentoUsuarioRepository
          .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivo.getId())) {
        throw conflito("ARQUIVO_DOCUMENTAL_VINCULADO");
      }
      boolean compartilhado = anuncioMidiaRepository.findByArquivoMidiaId(arquivo.getId()).stream()
          .anyMatch(item -> !anuncioId.equals(item.getAnuncioId())
              && item.getStatus() != StatusAnuncioMidia.REMOVIDA);
      if (compartilhado) {
        arquivosCompartilhados.add(arquivo.getId());
      } else {
        arquivosExclusivos.add(arquivo.getId());
        objetos.addAll(objetosDoArquivo(arquivo));
      }
    }

    ResultadoStorage storageResult = excluirEConfirmar(objetos);

    List<AnuncioMidiaEntity> vinculosAlterados = vinculos.stream()
        .filter(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA)
        .toList();
    vinculosAlterados.forEach(item -> item.removerLogicamente(agora));
    if (!vinculosAlterados.isEmpty()) {
      anuncioMidiaRepository.saveAll(vinculosAlterados);
    }

    List<ArquivoMidiaEntity> arquivosAlterados = arquivosExclusivos.stream()
        .map(arquivos::get)
        .filter(Objects::nonNull)
        .filter(item -> item.getStatusArquivo() != StatusArquivoMidia.REMOVIDO)
        .toList();
    arquivosAlterados.forEach(item -> item.aplicarDecisao(StatusArquivoMidia.REMOVIDO));
    if (!arquivosAlterados.isEmpty()) {
      arquivoMidiaRepository.saveAll(arquivosAlterados);
    }

    StoryResult storyResult = encerrarStories(
        anuncioId,
        vinculos.stream().map(AnuncioMidiaEntity::getId).toList(),
        agora,
        encerrarStoryAdministrativo);
    return new Resultado(
        vinculosAlterados.size(),
        storageResult.excluidos(),
        storageResult.jaAusentes(),
        arquivosCompartilhados.size(),
        storyResult.encerrados(),
        storyResult.administrativoEncerrado());
  }

  private ResultadoStorage excluirEConfirmar(Set<ObjetoStorage> objetos) {
    if (objetos.isEmpty()) {
      return new ResultadoStorage(0, 0);
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw falhaStorage("STORAGE_INDISPONIVEL");
    }

    int excluidos = 0;
    int jaAusentes = 0;
    for (ObjetoStorage objeto : objetos) {
      try {
        boolean existia = storage.exists(objeto.area(), objeto.key());
        if (existia) {
          storage.delete(objeto.area(), objeto.key());
        }
        if (storage.exists(objeto.area(), objeto.key())) {
          throw falhaStorage("OBJETO_PERMANECE_NO_R2");
        }
        if (existia) {
          excluidos++;
        } else {
          jaAusentes++;
        }
      } catch (CleanupException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        throw new CleanupException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FALHA_OPERACIONAL_R2",
            exception);
      }
    }
    return new ResultadoStorage(excluidos, jaAusentes);
  }

  private Set<ObjetoStorage> objetosDoArquivo(ArquivoMidiaEntity arquivo) {
    if (!"R2".equals(arquivo.getStorageProvider())) {
      return Set.of();
    }
    String bucket = arquivo.getBucket();
    String key = arquivo.getChaveObjeto();
    if (bucket == null || key == null) {
      throw conflito("REFERENCIA_R2_INCOMPLETA");
    }
    if (bucket.equals(storageProperties.getDocumentBucket())
        || key.startsWith(storageProperties.getDocumentPrefix())) {
      throw conflito("ARQUIVO_DOCUMENTAL_VINCULADO");
    }

    String relativePath;
    if (bucket.equals(storageProperties.getPublicMediaBucket())
        && chaveCanonica(key, storageProperties.getPublicMediaPrefix())) {
      relativePath = key.substring(storageProperties.getPublicMediaPrefix().length());
    } else if (bucket.equals(storageProperties.getPrivateMediaBucket())
        && chaveCanonica(key, storageProperties.getPrivateMediaPrefix())) {
      relativePath = key.substring(storageProperties.getPrivateMediaPrefix().length());
    } else {
      throw conflito("REFERENCIA_R2_FORA_DA_AREA_CANONICA");
    }
    return Set.of(
        new ObjetoStorage(
            StorageArea.PUBLIC_MEDIA,
            storageProperties.getPublicMediaPrefix() + relativePath),
        new ObjetoStorage(
            StorageArea.PRIVATE_MEDIA,
            storageProperties.getPrivateMediaPrefix() + relativePath));
  }

  private boolean chaveCanonica(String key, String prefix) {
    return prefix != null
        && !prefix.isBlank()
        && key.startsWith(prefix)
        && key.length() > prefix.length();
  }

  private StoryResult encerrarStories(
      UUID anuncioId,
      List<UUID> anuncioMidiaIds,
      OffsetDateTime agora,
      boolean encerrarStoryAdministrativo) {
    List<StoryAnuncioEntity> stories = anuncioMidiaIds.isEmpty()
        ? List.of()
        : storyRepository.findByAnuncioMidiaIdInForUpdate(anuncioMidiaIds);
    List<StoryAnuncioEntity> alterados = new ArrayList<>();
    for (StoryAnuncioEntity story : stories) {
      if (story.suspenderPorBloqueio(agora)) {
        alterados.add(story);
      }
    }
    if (!alterados.isEmpty()) {
      storyRepository.saveAll(alterados);
    }

    boolean administrativoEncerrado = false;
    if (encerrarStoryAdministrativo) {
      storyAdminRepository.bloquearOperacao();
      StorySelecaoAdministrativaEntity selecao =
          storyAdminRepository.bloquearSingleton().orElse(null);
      administrativoEncerrado = selecao != null
          && selecao.isAtiva()
          && anuncioId.equals(selecao.getAnuncioId());
      if (administrativoEncerrado) {
        selecao.desativar(agora);
        storyAdminRepository.save(selecao);
      }
    }
    return new StoryResult(alterados.size(), administrativoEncerrado);
  }

  private CleanupException conflito(String codigo) {
    return new CleanupException(HttpStatus.CONFLICT, codigo, null);
  }

  private CleanupException falhaStorage(String codigo) {
    return new CleanupException(HttpStatus.SERVICE_UNAVAILABLE, codigo, null);
  }

  public record Resultado(
      int midiasRemovidas,
      int objetosExcluidos,
      int objetosJaAusentes,
      int objetosCompartilhadosPreservados,
      int storiesEncerrados,
      boolean storyAdministrativoEncerrado) {
  }

  public static final class CleanupException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    CleanupException(HttpStatus status, String codigo, Throwable cause) {
      super(codigo, cause);
      this.status = status;
      this.codigo = codigo;
    }

    public HttpStatus status() {
      return status;
    }

    public String codigo() {
      return codigo;
    }
  }

  private record ObjetoStorage(StorageArea area, String key) {
  }

  private record ResultadoStorage(int excluidos, int jaAusentes) {
  }

  private record StoryResult(int encerrados, boolean administrativoEncerrado) {
  }
}
