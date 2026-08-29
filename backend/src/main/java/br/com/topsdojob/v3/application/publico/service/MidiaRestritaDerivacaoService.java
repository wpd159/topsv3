package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaPreviewIdentity;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoRestritaDerivada;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MidiaRestritaDerivacaoService {

  public static final String PENDENTE_DERIVACAO_RESTRITA = "PENDENTE_DERIVACAO_RESTRITA";

  private static final Logger LOGGER = LoggerFactory.getLogger(MidiaRestritaDerivacaoService.class);

  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties properties;
  private final FotoUploadProcessor processor;
  private final ArquivoMidiaRepository arquivoRepository;
  private final MidiaRestritaPreviewIdentity previewIdentity;
  private final Clock clock;

  @Autowired
  public MidiaRestritaDerivacaoService(
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties properties,
      FotoUploadProcessor processor,
      ArquivoMidiaRepository arquivoRepository,
      MidiaRestritaPreviewIdentity previewIdentity) {
    this(storageProvider, properties, processor, arquivoRepository, previewIdentity, Clock.systemUTC());
  }

  MidiaRestritaDerivacaoService(
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties properties,
      FotoUploadProcessor processor,
      ArquivoMidiaRepository arquivoRepository) {
    this(
        storageProvider,
        properties,
        processor,
        arquivoRepository,
        new MidiaRestritaPreviewIdentity(properties),
        Clock.systemUTC());
  }

  MidiaRestritaDerivacaoService(
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties properties,
      FotoUploadProcessor processor,
      ArquivoMidiaRepository arquivoRepository,
      Clock clock) {
    this(
        storageProvider,
        properties,
        processor,
        arquivoRepository,
        new MidiaRestritaPreviewIdentity(properties),
        clock);
  }

  private MidiaRestritaDerivacaoService(
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties properties,
      FotoUploadProcessor processor,
      ArquivoMidiaRepository arquivoRepository,
      MidiaRestritaPreviewIdentity previewIdentity,
      Clock clock) {
    this.storageProvider = storageProvider;
    this.properties = properties;
    this.processor = processor;
    this.arquivoRepository = arquivoRepository;
    this.previewIdentity = previewIdentity;
    this.clock = clock;
  }

  public ResultadoPreview resolverPreviewPublica(ArquivoMidiaEntity arquivo) {
    String key = chavePublicaOuNula(arquivo);
    if (key == null || arquivo == null || !arquivo.previewRestritoDisponivel()
        || !previewIdentity.versaoPipeline().equals(arquivo.getPreviewRestritoPipelineVersao())
        || !key.equals(arquivo.getPreviewRestritoChave())) {
      return pendente(arquivo, "estado_persistido");
    }
    String base = properties == null ? null : properties.getPublicBaseUrl();
    if (base == null || base.isBlank()) {
      return pendente(arquivo, "url_publica");
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return new ResultadoPreview(normalizedBase + "/" + key, null);
  }

  public ResultadoGeracao garantir(ArquivoMidiaEntity arquivo) {
    if (!fotoR2Elegivel(arquivo)) {
      return ResultadoGeracao.ignorado();
    }
    ObjectStorage storage = storage();
    if (storage == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de midia indisponivel");
    }

    long inicio = System.nanoTime();
    String key = chavePublica(arquivo);
    arquivo.marcarPreviewRestritoPendente(key, previewIdentity.versaoPipeline());
    arquivoRepository.saveAndFlush(arquivo);
    try {
      StorageArea sourceArea = areaOrigem(arquivo);
      StoredObject source = storage.get(sourceArea, arquivo.getChaveObjeto());
      long fimLeitura = System.nanoTime();
      validarOrigem(arquivo, source);
      FotoRestritaDerivada derivada = processor.gerarDerivacaoRestrita(
          source.content(),
          mime(arquivo.getMimeType(), source.contentType()));
      long fimDerivacao = System.nanoTime();

      ObjectWriteResult writeResult = storage.putIfAbsent(
          StorageArea.PUBLIC_MEDIA,
          key,
          derivada.bytes(),
          derivada.mimeType());
      long fimEscrita = System.nanoTime();
      if (writeResult == ObjectWriteResult.ALREADY_EXISTS) {
        StoredObject persisted = storage.get(StorageArea.PUBLIC_MEDIA, key);
        if (!derivada.sha256().equals(sha256(persisted.content()))
            || !derivada.mimeType().equals(mime(null, persisted.contentType()))) {
          throw new ResponseStatusException(HttpStatus.CONFLICT, "derivacao publica restrita divergente");
        }
      }
      long fimValidacao = System.nanoTime();

      arquivo.marcarPreviewRestritoDisponivel(
          key, previewIdentity.versaoPipeline(), OffsetDateTime.now(clock));
      arquivoRepository.saveAndFlush(arquivo);
      compensarRollback(storage, key, writeResult == ObjectWriteResult.CREATED);
      LOGGER.info(
          "Derivacao restrita concluida: leituraR2Ms={}, derivacaoMs={}, escritaR2Ms={}, validacaoR2Ms={}, totalMs={}, resultado={}",
          millis(inicio, fimLeitura),
          millis(fimLeitura, fimDerivacao),
          millis(fimDerivacao, fimEscrita),
          millis(fimEscrita, fimValidacao),
          millis(inicio, fimValidacao),
          writeResult);
      return new ResultadoGeracao(
          writeResult == ObjectWriteResult.CREATED,
          key,
          derivada.sha256(),
          derivada.largura(),
          derivada.altura(),
          derivada.mimeType());
    } catch (RuntimeException exception) {
      arquivo.marcarPreviewRestritoFalha(key, previewIdentity.versaoPipeline());
      arquivoRepository.saveAndFlush(arquivo);
      throw new PreviewGenerationException(exception);
    }
  }

  public String prefixoPreviews() {
    return previewIdentity.prefixoPreviews();
  }

  public String versaoPipeline() {
    return previewIdentity.versaoPipeline();
  }

  public String chavePublica(ArquivoMidiaEntity arquivo) {
    return previewIdentity.chavePublica(arquivo);
  }

  private String chavePublicaOuNula(ArquivoMidiaEntity arquivo) {
    return previewIdentity.chavePublicaOuNula(arquivo);
  }

  private boolean fotoR2Elegivel(ArquivoMidiaEntity arquivo) {
    return arquivo != null
        && "R2".equals(arquivo.getStorageProvider())
        && arquivo.getMimeType() != null
        && arquivo.getMimeType().toLowerCase(Locale.ROOT).startsWith("image/");
  }

  private StorageArea areaOrigem(ArquivoMidiaEntity arquivo) {
    String key = arquivo.getChaveObjeto();
    if (key == null || key.isBlank()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "midia restrita sem objeto canonico");
    }
    if (properties.getPrivateMediaBucket().equals(arquivo.getBucket())
        && key.startsWith(properties.getPrivateMediaPrefix())) {
      return StorageArea.PRIVATE_MEDIA;
    }
    if (properties.getPublicMediaBucket().equals(arquivo.getBucket())
        && key.startsWith(properties.getPublicMediaPrefix())
        && !previewIdentity.isPreviewKey(key)) {
      return StorageArea.PUBLIC_MEDIA;
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT, "midia restrita fora da area canonica");
  }

  private void validarOrigem(ArquivoMidiaEntity arquivo, StoredObject source) {
    if (source == null || source.content() == null || source.content().length == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "objeto original restrito indisponivel");
    }
    String esperado = arquivo.getSha256();
    if (esperado != null && esperado.matches("(?i)[0-9a-f]{64}")
        && !esperado.equalsIgnoreCase(sha256(source.content()))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "checksum da midia restrita divergente");
    }
  }

  private void compensarRollback(ObjectStorage storage, String key, boolean criado) {
    if (!criado || !TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
          return;
        }
        try {
          storage.delete(StorageArea.PUBLIC_MEDIA, key);
        } catch (RuntimeException ignored) {
          LOGGER.warn("Falha sanitizada ao compensar derivacao restrita");
        }
      }
    });
  }

  private ObjectStorage storage() {
    return properties != null && properties.isEnabled() && storageProvider != null
        ? storageProvider.getIfAvailable()
        : null;
  }

  private ResultadoPreview pendente(ArquivoMidiaEntity arquivo, String motivo) {
    LOGGER.warn(
        "Derivacao restrita indisponivel para arquivo {} ({})",
        arquivo == null || arquivo.getId() == null ? "desconhecido" : arquivo.getId(),
        motivo);
    return new ResultadoPreview(null, PENDENTE_DERIVACAO_RESTRITA);
  }

  private String mime(String cadastrado, String armazenado) {
    String value = cadastrado == null || cadastrado.isBlank() ? armazenado : cadastrado;
    return value == null
        ? "application/octet-stream"
        : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private long millis(long inicio, long fim) {
    return Math.max(0L, (fim - inicio) / 1_000_000L);
  }

  public record ResultadoPreview(String previewUrl, String pendencia) {
  }

  public record ResultadoGeracao(
      boolean criada,
      String chavePublica,
      String sha256,
      int largura,
      int altura,
      String mimeType) {

    static ResultadoGeracao ignorado() {
      return new ResultadoGeracao(false, null, null, 0, 0, null);
    }
  }

  public static final class PreviewGenerationException extends ResponseStatusException {
    public PreviewGenerationException(Throwable cause) {
      super(HttpStatus.SERVICE_UNAVAILABLE, "preview restrito indisponivel", cause);
    }
  }
}
