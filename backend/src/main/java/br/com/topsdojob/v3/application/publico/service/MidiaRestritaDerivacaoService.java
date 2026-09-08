package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoRestritaDerivada;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MidiaRestritaDerivacaoService {

  public static final String PENDENTE_DERIVACAO_RESTRITA = "PENDENTE_DERIVACAO_RESTRITA";

  private static final Logger LOGGER = LoggerFactory.getLogger(MidiaRestritaDerivacaoService.class);
  private static final String DERIVATION_VERSION = "v1";
  private static final String DERIVATION_DIRECTORY = "restritas-borradas/" + DERIVATION_VERSION + "/";
  private static final int MAX_PREVIEWS_LOCALIDADES = 4096;
  private static final ThreadLocal<PreviewsLocalidades> PREVIEWS_LOCALIDADES = new ThreadLocal<>();

  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties properties;
  private final FotoUploadProcessor processor;
  private final R2VerificacaoAgrupadaPreviews verificacaoAgrupada;
  private final Set<String> previewsConfirmados = ConcurrentHashMap.newKeySet();

  @Autowired
  public MidiaRestritaDerivacaoService(
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties properties,
      FotoUploadProcessor processor) {
    this(storageProvider, properties, processor, new R2VerificacaoAgrupadaPreviews(properties));
  }

  public MidiaRestritaDerivacaoService(
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties properties,
      FotoUploadProcessor processor,
      R2VerificacaoAgrupadaPreviews verificacaoAgrupada) {
    this.storageProvider = storageProvider;
    this.properties = properties;
    this.processor = processor;
    this.verificacaoAgrupada = verificacaoAgrupada;
  }

  public ResultadoPreview resolverPreviewPublica(ArquivoMidiaEntity arquivo) {
    return resolverPreviewPublicaLeitura(arquivo == null ? null : arquivo.getId(),
        arquivo == null ? null : arquivo.getSha256());
  }

  public void coletarPreviewLocalidades(java.util.UUID id, String checksum) {
    PreviewsLocalidades operacao = PREVIEWS_LOCALIDADES.get();
    if (operacao == null || operacao.fase != FasePreviewsLocalidades.COLETA) {
      throw indisponivelLocalidades("coleta fora da operacao de localidades");
    }
    operacao.resolver(this, id, checksum);
  }

  public ResultadoPreview resolverPreviewPublicaLeitura(java.util.UUID id, String checksum) {
    PreviewsLocalidades operacao = PREVIEWS_LOCALIDADES.get();
    if (operacao != null) {
      return operacao.resolver(this, id, checksum);
    }
    return resolverPreviewGeral(id, checksum);
  }

  private ResultadoPreview resolverPreviewGeral(java.util.UUID id, String checksum) {
    String key = chavePublicaOuNula(id, checksum);
    ObjectStorage storage = storage();
    if (key == null || storage == null) {
      return pendente(id, "configuracao");
    }
    try {
      if (!previewsConfirmados.contains(key) && !storage.exists(StorageArea.PUBLIC_MEDIA, key)) {
        return pendente(id, "ausente");
      }
      previewsConfirmados.add(key);
      return storage.publicUrl(StorageArea.PUBLIC_MEDIA, key)
          .map(uri -> new ResultadoPreview(uri.toString(), null))
          .orElseGet(() -> pendente(id, "url_publica"));
    } catch (RuntimeException exception) {
      return pendente(id, "storage");
    }
  }

  /**
   * Cards collect only displayed previews. Keep the existing HEAD/cache/pending
   * contract, but perform that I/O between two independent database reads.
   * No completed DTO or new cross-request cache is retained by this operation.
   */
  public static <T> T comPreviewsParaCards(Supplier<T> leituraTransacional) {
    if (PREVIEWS_LOCALIDADES.get() != null
        || TransactionSynchronizationManager.isActualTransactionActive()) {
      throw indisponivelLocalidades("consulta de cards exige operacao sem transacao externa");
    }
    PreviewsLocalidades operacao = new PreviewsLocalidades(true);
    PREVIEWS_LOCALIDADES.set(operacao);
    try {
      T inicial = leituraTransacional.get();
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        throw indisponivelLocalidades("verificacao remota exige transacao encerrada");
      }
      if (operacao.pedidos.isEmpty()) return inicial;
      operacao.fase = FasePreviewsLocalidades.VERIFICACAO_REMOTA;
      for (var pedido : operacao.identidades.entrySet()) {
        var identidade = pedido.getValue();
        operacao.verificados.put(pedido.getKey(), operacao.responsavel.resolverPreviewGeral(
            identidade.id(), identidade.checksum()));
      }
      operacao.fase = FasePreviewsLocalidades.FINAL;
      // Re-read selection, visibility, owners, benefits and file identities. A new
      // unverified preview fails explicitly rather than triggering I/O inside JDBC.
      return leituraTransacional.get();
    } finally {
      PREVIEWS_LOCALIDADES.remove();
    }
  }

  /** Shared position selection in both reads; storage I/O only between transactions. */
  public static <T> T comPreviewsDeLocalidades(Runnable coleta, Supplier<T> validacaoFinal) {
    if (PREVIEWS_LOCALIDADES.get() != null) {
      throw indisponivelLocalidades("operacao de previews ja iniciada");
    }
    PreviewsLocalidades operacao = new PreviewsLocalidades();
    PREVIEWS_LOCALIDADES.set(operacao);
    try {
      conferirOrcamentoLocalidades();
      coleta.run();
      conferirOrcamentoLocalidades();
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        throw indisponivelLocalidades("verificacao remota exige transacao encerrada");
      }
      operacao.fase = FasePreviewsLocalidades.VERIFICACAO_REMOTA;
      Supplier<Void> verificacao = () -> {
        if (!operacao.pedidos.isEmpty()) {
          operacao.verificados.putAll(operacao.responsavel.verificarPreviewsLocalidades(operacao.pedidos));
        }
        return null;
      };
      LocalidadesConsultaOrcamento orcamento = LocalidadesConsultaOrcamento.atualOuNulo();
      if (orcamento == null) verificacao.get();
      else orcamento.medir("previews_verificacao_remota", verificacao);
      operacao.fase = FasePreviewsLocalidades.FINAL;
      T resultado = validacaoFinal.get();
      conferirOrcamentoLocalidades();
      return resultado;
    } finally {
      PREVIEWS_LOCALIDADES.remove();
    }
  }

  private Map<String, ResultadoPreview> verificarPreviewsLocalidades(Set<String> chaves) {
    conferirOrcamentoLocalidades();
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw indisponivelLocalidades("verificacao remota exige transacao encerrada");
    }
    try {
      ObjectStorage storage = storage();
      if (storage == null || verificacaoAgrupada == null) {
        throw indisponivelLocalidades("storage de preview indisponivel");
      }
      // Only this operation's requested identities may receive a proof. The helper
      // never retains an inventory or falls back to per-object HEAD requests.
      Set<String> solicitadas = Set.copyOf(chaves);
      Set<String> presentes = verificacaoAgrupada.verificar(solicitadas);
      conferirOrcamentoLocalidades();
      if (presentes == null || !solicitadas.containsAll(presentes)) {
        throw indisponivelLocalidades("prova agrupada de preview invalida");
      }
      Map<String, ResultadoPreview> resultados = new LinkedHashMap<>();
      for (String key : solicitadas) {
        conferirOrcamentoLocalidades();
        ResultadoPreview resultado = presentes.contains(key)
            ? storage.publicUrl(StorageArea.PUBLIC_MEDIA, key)
                .map(uri -> new ResultadoPreview(uri.toString(), null))
                .orElseThrow(() -> indisponivelLocalidades("url de preview indisponivel"))
            : new ResultadoPreview(null, PENDENTE_DERIVACAO_RESTRITA);
        resultados.put(key, resultado);
      }
      conferirOrcamentoLocalidades();
      return resultados;
    } catch (RuntimeException exception) {
      if (exception instanceof ResponseStatusException status && status.getStatusCode().value() == 503) {
        throw status;
      }
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "verificacao de preview de localidades indisponivel", exception);
    }
  }

  private static void conferirOrcamentoLocalidades() {
    LocalidadesConsultaOrcamento orcamento = LocalidadesConsultaOrcamento.atualOuNulo();
    if (orcamento != null) orcamento.conferir();
  }

  private static ResponseStatusException indisponivelLocalidades(String motivo) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, motivo);
  }

  private enum FasePreviewsLocalidades { COLETA, VERIFICACAO_REMOTA, FINAL }

  private static final class PreviewsLocalidades {
    private final boolean cards;
    private FasePreviewsLocalidades fase = FasePreviewsLocalidades.COLETA;
    private MidiaRestritaDerivacaoService responsavel;
    private final Set<String> pedidos = new LinkedHashSet<>();
    private final Map<String, ResultadoPreview> verificados = new LinkedHashMap<>();
    private final Map<String, IdentidadePreview> identidades = new LinkedHashMap<>();

    private PreviewsLocalidades() { this(false); }
    private PreviewsLocalidades(boolean cards) { this.cards = cards; }

    private ResultadoPreview resolver(MidiaRestritaDerivacaoService service, java.util.UUID id, String checksum) {
      conferirOrcamentoLocalidades();
      String key = service.chavePublicaOuNula(id, checksum);
      if (key == null && cards) return service.pendente(id, "configuracao");
      if (key == null) throw indisponivelLocalidades("preview sem identidade canonica");
      if (responsavel != null && responsavel != service) {
        throw indisponivelLocalidades("origem de preview alterada durante consulta de localidades");
      }
      if (fase == FasePreviewsLocalidades.COLETA) {
        responsavel = service;
        if (!pedidos.contains(key)) {
          if (pedidos.size() >= MAX_PREVIEWS_LOCALIDADES) {
            throw indisponivelLocalidades("limite de previews por consulta excedido");
          }
          pedidos.add(key);
          if (cards) identidades.put(key, new IdentidadePreview(id, checksum));
        }
        // Legacy callers may still request an intermediate result; collection needs none.
        return PREVIEW_PENDENTE;
      }
      if (fase != FasePreviewsLocalidades.FINAL || !verificados.containsKey(key)) {
        throw indisponivelLocalidades(cards
            ? "preview alterado durante consulta de cards"
            : "preview alterado durante consulta de localidades");
      }
      return verificados.get(key);
    }
  }

  private record IdentidadePreview(java.util.UUID id, String checksum) { }

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

    previewsConfirmados.add(key);
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
  }

  public String chavePublica(ArquivoMidiaEntity arquivo) {
    String key = chavePublicaOuNula(arquivo);
    if (key == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "midia restrita sem identidade canonica");
    }
    return key;
  }

  private String chavePublicaOuNula(ArquivoMidiaEntity arquivo) {
    return arquivo == null ? null : chavePublicaOuNula(arquivo.getId(), arquivo.getSha256());
  }

  private String chavePublicaOuNula(java.util.UUID id, String sha256) {
    if (id == null || properties == null) {
      return null;
    }
    String prefix = properties.getPublicMediaPrefix();
    if (prefix == null || prefix.isBlank()) {
      return null;
    }
    String checksum = sha256 == null
        ? "sem-checksum"
        : sha256.trim().toLowerCase(Locale.ROOT);
    String identificadorDerivado = sha256((id + ":" + checksum + ":" + DERIVATION_VERSION)
        .getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    return prefix + DERIVATION_DIRECTORY + identificadorDerivado + ".jpg";
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
        && !key.contains("/" + DERIVATION_DIRECTORY)) {
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
          previewsConfirmados.remove(key);
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

  private ResultadoPreview pendente(java.util.UUID id, String motivo) {
    LOGGER.warn(
        "Derivacao restrita indisponivel para arquivo {} ({})",
        id == null ? "desconhecido" : id,
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

  private static final ResultadoPreview PREVIEW_PENDENTE =
      new ResultadoPreview(null, PENDENTE_DERIVACAO_RESTRITA);

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
}
