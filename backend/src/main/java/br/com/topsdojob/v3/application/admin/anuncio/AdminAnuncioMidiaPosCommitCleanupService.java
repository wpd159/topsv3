package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRevisaoRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnuncioMidiaPosCommitCleanupService {

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final DocumentoUsuarioRepository documentoUsuarioRepository;
  private final AnuncioMidiaRevisaoRepository revisaoRepository;
  private final StoryAnuncioRepository storyRepository;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;

  public AdminAnuncioMidiaPosCommitCleanupService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      DocumentoUsuarioRepository documentoUsuarioRepository,
      AnuncioMidiaRevisaoRepository revisaoRepository,
      StoryAnuncioRepository storyRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.documentoUsuarioRepository = documentoUsuarioRepository;
    this.revisaoRepository = revisaoRepository;
    this.storyRepository = storyRepository;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Preparacao preparar(
      Collection<UUID> arquivoIds,
      Collection<UUID> anuncioMidiaIdsDesvinculados) {
    Plano plano = planejar(arquivoIds, anuncioMidiaIdsDesvinculados);
    if (plano.removivel()) {
      plano.arquivos().stream()
          .filter(item -> item.getPreviewRestritoTipo() != null
              || item.getPreviewRestritoChave() != null)
          .forEach(ArquivoMidiaEntity::marcarPreviewRestritoRemovido);
      arquivoMidiaRepository.saveAllAndFlush(plano.arquivos());
    }
    return new Preparacao(
        plano.arquivos().stream().map(ArquivoMidiaEntity::getId).collect(
            java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
        plano.objetos().size(),
        plano.removivel(),
        plano.preservado());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Resultado limparSeContinuarOrfao(
      Collection<UUID> arquivoIds,
      Collection<UUID> anuncioMidiaIdsDesvinculados) {
    Plano plano = planejar(arquivoIds, anuncioMidiaIdsDesvinculados);
    if (!plano.removivel()) {
      return new Resultado(0, 0, true);
    }

    ResultadoStorage storage = excluirEConfirmar(plano.objetos());
    List<ArquivoMidiaEntity> alterados = plano.arquivos().stream()
        .filter(item -> item.getStatusArquivo() != StatusArquivoMidia.REMOVIDO)
        .toList();
    alterados.forEach(item -> item.aplicarDecisao(StatusArquivoMidia.REMOVIDO));
    if (!alterados.isEmpty()) {
      arquivoMidiaRepository.saveAll(alterados);
    }
    return new Resultado(storage.excluidos(), storage.jaAusentes(), false);
  }

  private Plano planejar(
      Collection<UUID> arquivoIds,
      Collection<UUID> anuncioMidiaIdsDesvinculados) {
    Set<UUID> idsSolicitados = arquivoIds == null
        ? Set.of()
        : arquivoIds.stream().filter(Objects::nonNull).collect(
            java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<UUID> vinculosDesvinculados = anuncioMidiaIdsDesvinculados == null
        ? Set.of()
        : anuncioMidiaIdsDesvinculados.stream().filter(Objects::nonNull).collect(
            java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    if (idsSolicitados.isEmpty()) {
      return new Plano(List.of(), Set.of(), false, true);
    }

    Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
    List<UUID> idsOrdenados = idsSolicitados.stream().sorted().toList();
    arquivoMidiaRepository.findByIdInForUpdate(idsOrdenados).stream()
        .sorted(Comparator.comparing(ArquivoMidiaEntity::getId))
        .forEach(item -> arquivos.put(item.getId(), item));
    if (arquivos.size() != idsSolicitados.size()) {
      return new Plano(List.copyOf(arquivos.values()), Set.of(), false, true);
    }

    Set<ObjetoStorage> objetos = new LinkedHashSet<>();
    ArrayDeque<ArquivoMidiaEntity> fila = new ArrayDeque<>(arquivos.values());
    boolean protegido = false;
    while (!fila.isEmpty()) {
      ArquivoMidiaEntity arquivo = fila.removeFirst();
      ResolucaoObjetos resolucao = objetosDoArquivo(arquivo);
      if (!resolucao.removivel()) {
        protegido = true;
        continue;
      }
      for (ObjetoStorage objeto : resolucao.objetos()) {
        objetos.add(objeto);
        arquivoMidiaRepository.findByStorageIdentityForUpdate(
                "R2", bucket(objeto.area()), objeto.key())
            .filter(item -> !arquivos.containsKey(item.getId()))
            .ifPresent(item -> {
              arquivos.put(item.getId(), item);
              fila.addLast(item);
            });
      }
    }

    Set<UUID> idsRelacionados = new LinkedHashSet<>(arquivos.keySet());
    boolean referenciado = protegido
        || temReferenciaLegitima(idsRelacionados, vinculosDesvinculados);
    return new Plano(
        List.copyOf(arquivos.values()),
        Set.copyOf(objetos),
        !referenciado,
        referenciado);
  }

  private boolean temReferenciaLegitima(
      Collection<UUID> arquivoIds,
      Collection<UUID> anuncioMidiaIdsDesvinculados) {
    if (documentoUsuarioRepository
        .existsByArquivoMidiaIdInAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoIds)) {
      return true;
    }
    Set<UUID> ignorados = new LinkedHashSet<>(anuncioMidiaIdsDesvinculados);
    List<AnuncioMidiaEntity> vinculosRelacionados =
        anuncioMidiaRepository.findByArquivoMidiaIdIn(arquivoIds);
    Set<UUID> ignoradosRelacionados = vinculosRelacionados.stream()
        .map(AnuncioMidiaEntity::getId)
        .filter(ignorados::contains)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    boolean outraMidiaAtiva = vinculosRelacionados.stream()
        .anyMatch(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA
            && !ignorados.contains(item.getId()));
    if (outraMidiaAtiva || revisaoRepository.existsByArquivoMidiaIdIn(arquivoIds)
        || storyRepository.existsByArquivoMidiaIdIn(arquivoIds)) {
      return true;
    }
    if (ignoradosRelacionados.isEmpty()) {
      return false;
    }
    return revisaoRepository.existsByAnuncioMidiaIdIn(ignoradosRelacionados)
        || storyRepository.existsByAnuncioMidiaIdIn(ignoradosRelacionados);
  }

  private ResolucaoObjetos objetosDoArquivo(ArquivoMidiaEntity arquivo) {
    if (!"R2".equals(arquivo.getStorageProvider())) {
      return new ResolucaoObjetos(true, Set.of());
    }
    String bucket = arquivo.getBucket();
    String key = arquivo.getChaveObjeto();
    if (bucket == null || key == null
        || bucket.equals(storageProperties.getDocumentBucket())
        || possuiPrefixo(key, storageProperties.getDocumentPrefix())) {
      return new ResolucaoObjetos(false, Set.of());
    }

    String relativePath;
    if (bucket.equals(storageProperties.getPublicMediaBucket())
        && chaveCanonica(key, storageProperties.getPublicMediaPrefix())) {
      relativePath = key.substring(storageProperties.getPublicMediaPrefix().length());
    } else if (bucket.equals(storageProperties.getPrivateMediaBucket())
        && chaveCanonica(key, storageProperties.getPrivateMediaPrefix())) {
      relativePath = key.substring(storageProperties.getPrivateMediaPrefix().length());
    } else {
      return new ResolucaoObjetos(false, Set.of());
    }
    Set<ObjetoStorage> objetos = new LinkedHashSet<>();
    objetos.add(new ObjetoStorage(
        StorageArea.PUBLIC_MEDIA,
        storageProperties.getPublicMediaPrefix() + relativePath));
    objetos.add(new ObjetoStorage(
        StorageArea.PRIVATE_MEDIA,
        storageProperties.getPrivateMediaPrefix() + relativePath));
    if (arquivo.getPreviewRestritoChave() != null
        && chaveCanonica(
            arquivo.getPreviewRestritoChave(), storageProperties.getPublicMediaPrefix())) {
      objetos.add(new ObjetoStorage(
          StorageArea.PUBLIC_MEDIA, arquivo.getPreviewRestritoChave()));
    }
    return new ResolucaoObjetos(true, Set.copyOf(objetos));
  }

  private ResultadoStorage excluirEConfirmar(Set<ObjetoStorage> objetos) {
    if (objetos.isEmpty()) {
      return new ResultadoStorage(0, 0);
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw new CleanupPosCommitException("STORAGE_INDISPONIVEL");
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
          throw new CleanupPosCommitException("OBJETO_PERMANECE_NO_R2");
        }
        if (existia) {
          excluidos++;
        } else {
          jaAusentes++;
        }
      } catch (CleanupPosCommitException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        throw new CleanupPosCommitException("FALHA_OPERACIONAL_R2", exception);
      }
    }
    return new ResultadoStorage(excluidos, jaAusentes);
  }

  private String bucket(StorageArea area) {
    return switch (area) {
      case PUBLIC_MEDIA -> storageProperties.getPublicMediaBucket();
      case PRIVATE_MEDIA -> storageProperties.getPrivateMediaBucket();
      case PRIVATE_DOCUMENT -> storageProperties.getDocumentBucket();
      case PRESERVED_PUBLIC_MEDIA -> throw new IllegalArgumentException(
          "arquivo publico legado preservado nao pode ser limpo");
    };
  }

  private boolean chaveCanonica(String key, String prefix) {
    return possuiPrefixo(key, prefix) && key.length() > prefix.length();
  }

  private boolean possuiPrefixo(String key, String prefix) {
    return key != null && prefix != null && !prefix.isBlank() && key.startsWith(prefix);
  }

  public record Preparacao(
      Set<UUID> arquivoIds,
      int objetosCandidatos,
      boolean cleanupNecessario,
      boolean compartilhadoOuProtegido) {
  }

  public record Resultado(int excluidos, int jaAusentes, boolean preservado) {
  }

  public static final class CleanupPosCommitException extends RuntimeException {
    private final String codigo;

    CleanupPosCommitException(String codigo) {
      super(codigo);
      this.codigo = codigo;
    }

    CleanupPosCommitException(String codigo, Throwable cause) {
      super(codigo, cause);
      this.codigo = codigo;
    }

    public String codigo() {
      return codigo;
    }
  }

  private record Plano(
      List<ArquivoMidiaEntity> arquivos,
      Set<ObjetoStorage> objetos,
      boolean removivel,
      boolean preservado) {
  }

  private record ResolucaoObjetos(boolean removivel, Set<ObjetoStorage> objetos) {
  }

  private record ObjetoStorage(StorageArea area, String key) {
  }

  private record ResultadoStorage(int excluidos, int jaAusentes) {
  }
}
