package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.midia.CheckpointMidiaMigracao.Registro;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao.ObjetoOrigemAusenteException;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao.OrigemMidiaInvalidaException;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class MotorCopiaMidiaFaseCinco {

  private final FonteMidiaMigracao source;
  private final ObjectStorage destination;
  private final CheckpointMidiaMigracao checkpoint;
  private final Config config;
  private final Sleeper sleeper;
  private final ProgressoListener progresso;
  private final DetectorConteudoMidia detector = new DetectorConteudoMidia();
  private final Map<String, Object> destinationLocks = new ConcurrentHashMap<>();

  public MotorCopiaMidiaFaseCinco(
      FonteMidiaMigracao source,
      ObjectStorage destination,
      CheckpointMidiaMigracao checkpoint,
      Config config) {
    this(source, destination, checkpoint, config, Thread::sleep, ignored -> { });
  }

  MotorCopiaMidiaFaseCinco(
      FonteMidiaMigracao source,
      ObjectStorage destination,
      CheckpointMidiaMigracao checkpoint,
      Config config,
      Sleeper sleeper) {
    this(source, destination, checkpoint, config, sleeper, ignored -> { });
  }

  MotorCopiaMidiaFaseCinco(
      FonteMidiaMigracao source,
      ObjectStorage destination,
      CheckpointMidiaMigracao checkpoint,
      Config config,
      Sleeper sleeper,
      ProgressoListener progresso) {
    this.source = Objects.requireNonNull(source, "fonte obrigatoria");
    this.destination = Objects.requireNonNull(destination, "destino obrigatorio");
    this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint obrigatorio");
    this.config = Objects.requireNonNull(config, "config obrigatoria");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper obrigatorio");
    this.progresso = Objects.requireNonNull(progresso, "progresso obrigatorio");
  }

  public RelatorioExecucao executar(
      UUID executionId,
      ManifestoMidiaFaseCinco manifest,
      boolean dryRun) {
    Objects.requireNonNull(executionId, "execucaoId obrigatorio");
    Objects.requireNonNull(manifest, "manifesto obrigatorio");
    ExecutorService executor = Executors.newFixedThreadPool(config.concorrencia());
    try {
      List<Callable<ResultadoItem>> tasks = manifest.itens().stream()
          .<Callable<ResultadoItem>>map(item -> () -> {
            ResultadoItem result = processar(executionId, item, dryRun);
            registrarProgresso(result);
            return result;
          })
          .toList();
      List<Future<ResultadoItem>> futures = executor.invokeAll(
          tasks, config.timeoutExecucao().toMillis(), TimeUnit.MILLISECONDS);
      List<ResultadoItem> results = new ArrayList<>(futures.size());
      for (int index = 0; index < futures.size(); index++) {
        results.add(obter(futures.get(index), manifest.itens().get(index)));
      }
      return new RelatorioExecucao(manifest.sha256(), dryRun, results);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return interrompido(manifest, dryRun);
    } finally {
      executor.shutdownNow();
    }
  }

  private void registrarProgresso(ResultadoItem result) {
    try {
      progresso.registrar(result);
    } catch (RuntimeException ignored) {
      // Progresso e observabilidade nao alteram o resultado da copia.
    }
  }

  private ResultadoItem processar(UUID executionId, Item item, boolean dryRun) {
    if (item.decisao() == Decisao.DESCARTAR) {
      return ResultadoItem.decisao(item, StatusResultado.DESCARTADA);
    }
    if (item.decisao() == Decisao.QUARENTENA) {
      return ResultadoItem.decisao(item, StatusResultado.QUARENTENA);
    }
    if (Thread.currentThread().isInterrupted()) {
      return ResultadoItem.falha(item, StatusResultado.INTERROMPIDA, "EXECUCAO_INTERROMPIDA");
    }

    String destinationFingerprint = CheckpointMidiaMigracaoJdbc.destinoFingerprint(item);
    Object lock = destinationLocks.computeIfAbsent(destinationFingerprint, ignored -> new Object());
    synchronized (lock) {
      return copiarComRetry(executionId, item, destinationFingerprint, dryRun);
    }
  }

  private ResultadoItem copiarComRetry(
      UUID executionId,
      Item item,
      String destinationFingerprint,
      boolean dryRun) {
    for (int attempt = 1; attempt <= config.maxTentativas(); attempt++) {
      try {
        if (checkpointValido(executionId, item, destinationFingerprint)) {
          ResultadoItem verified = validarDestino(item, StatusResultado.PRESERVADA);
          if (verified.sucesso()) {
            return verified;
          }
        }

        StoredObject sourceObject = source.carregar(item.origem());
        ResultadoItem sourceValidation = validarConteudo(
            item, sourceObject, StatusResultado.VALIDADA, false);
        if (!sourceValidation.sucesso()) {
          return sourceValidation;
        }
        if (dryRun) {
          return sourceValidation;
        }

        if (destination.exists(item.destino().area(), item.destino().chave())) {
          ResultadoItem preserved = validarDestino(item, StatusResultado.PRESERVADA);
          if (preserved.sucesso()) {
            salvarCheckpoint(executionId, item);
          }
          return preserved;
        }

        ObjectWriteResult write = destination.putIfAbsent(
            item.destino().area(),
            item.destino().chave(),
            sourceObject.content(),
            item.mimeType());
        StatusResultado expected = write == ObjectWriteResult.CREATED
            ? StatusResultado.COPIADA
            : StatusResultado.PRESERVADA;
        ResultadoItem copied = validarDestino(item, expected);
        if (copied.sucesso()) {
          salvarCheckpoint(executionId, item);
        }
        return copied;
      } catch (ObjetoOrigemAusenteException exception) {
        return ResultadoItem.falha(
            item, StatusResultado.QUARENTENA, "OBJETO_ORIGEM_AUSENTE");
      } catch (OrigemMidiaInvalidaException exception) {
        return ResultadoItem.falha(
            item, StatusResultado.QUARENTENA, "ORIGEM_FORA_DA_POLITICA");
      } catch (RuntimeException exception) {
        if (attempt == config.maxTentativas()) {
          return ResultadoItem.falha(
              item, StatusResultado.FALHA, "FALHA_TRANSITORIA_ESGOTADA");
        }
        if (!aguardar(attempt)) {
          return ResultadoItem.falha(
              item, StatusResultado.INTERROMPIDA, "EXECUCAO_INTERROMPIDA");
        }
      }
    }
    return ResultadoItem.falha(item, StatusResultado.FALHA, "FALHA_NAO_CLASSIFICADA");
  }

  private boolean checkpointValido(
      UUID executionId,
      Item item,
      String destinationFingerprint) {
    Optional<Registro> registered = checkpoint.buscar(executionId, item.fingerprint());
    return registered.filter(value ->
        destinationFingerprint.equals(value.destinoFingerprint())
            && item.sha256().equals(value.checksum())
            && item.tamanhoBytes() == value.tamanhoBytes()
            && detector.mimeEquivalente(item.mimeType(), value.mimeType()))
        .isPresent();
  }

  private ResultadoItem validarDestino(Item item, StatusResultado successStatus) {
    if (!destination.exists(item.destino().area(), item.destino().chave())) {
      return ResultadoItem.falha(
          item, StatusResultado.BLOQUEADA, "OBJETO_DESTINO_AUSENTE");
    }
    StoredObject stored = destination.get(item.destino().area(), item.destino().chave());
    ResultadoItem result = validarConteudo(item, stored, successStatus, true);
    if (result.sucesso()
        && item.destino().area() != StorageArea.PUBLIC_MEDIA
        && destination.publicUrl(item.destino().area(), item.destino().chave()).isPresent()) {
      return ResultadoItem.falha(
          item, StatusResultado.BLOQUEADA, "OBJETO_PRIVADO_COM_URL_PUBLICA");
    }
    return result;
  }

  private ResultadoItem validarConteudo(
      Item item,
      StoredObject object,
      StatusResultado successStatus,
      boolean requireCanonicalMetadata) {
    byte[] content = object.content();
    Optional<DetectorConteudoMidia.Detectado> detected = detector.detectar(content);
    if (detected.isEmpty()
        || !detector.mimeEquivalente(item.mimeType(), detected.get().mimeType())) {
      return ResultadoItem.falha(
          item, StatusResultado.BLOQUEADA, "ASSINATURA_OU_MIME_DIVERGENTE");
    }
    boolean metadataPresent = !"application/octet-stream".equalsIgnoreCase(object.contentType());
    if ((requireCanonicalMetadata || metadataPresent)
        && !detector.mimeEquivalente(item.mimeType(), object.contentType())) {
      return ResultadoItem.falha(
          item, StatusResultado.BLOQUEADA, "METADATA_MIME_DIVERGENTE");
    }
    if (content.length != item.tamanhoBytes()) {
      return ResultadoItem.falha(
          item, StatusResultado.BLOQUEADA, "TAMANHO_DIVERGENTE");
    }
    if (!item.sha256().equals(MidiaMigracaoHashes.sha256(content))) {
      return ResultadoItem.falha(
          item, StatusResultado.BLOQUEADA, "CHECKSUM_DIVERGENTE");
    }
    return ResultadoItem.sucesso(item, successStatus);
  }

  private void salvarCheckpoint(UUID executionId, Item item) {
    checkpoint.registrarConcluido(
        executionId, item, item.sha256(), item.tamanhoBytes(), item.mimeType());
  }

  private boolean aguardar(int attempt) {
    try {
      long delay = Math.multiplyExact(config.backoffInicial().toMillis(), attempt);
      sleeper.sleep(delay);
      return !Thread.currentThread().isInterrupted();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private ResultadoItem obter(Future<ResultadoItem> future, Item item) {
    try {
      return future.get();
    } catch (CancellationException exception) {
      return ResultadoItem.falha(item, StatusResultado.INTERROMPIDA, "TIMEOUT_EXECUCAO");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return ResultadoItem.falha(
          item, StatusResultado.INTERROMPIDA, "EXECUCAO_INTERROMPIDA");
    } catch (ExecutionException exception) {
      return ResultadoItem.falha(
          item, StatusResultado.FALHA, "FALHA_INTERNA_SANITIZADA");
    }
  }

  private RelatorioExecucao interrompido(ManifestoMidiaFaseCinco manifest, boolean dryRun) {
    List<ResultadoItem> results = manifest.itens().stream()
        .map(item -> ResultadoItem.falha(
            item, StatusResultado.INTERROMPIDA, "EXECUCAO_INTERROMPIDA"))
        .toList();
    return new RelatorioExecucao(manifest.sha256(), dryRun, results);
  }

  public record Config(
      int concorrencia,
      int maxTentativas,
      Duration backoffInicial,
      Duration timeoutExecucao) {

    public Config(int concorrencia, int maxTentativas, Duration backoffInicial) {
      this(concorrencia, maxTentativas, backoffInicial, Duration.ofMinutes(30));
    }

    public Config {
      if (concorrencia < 1 || concorrencia > 16) {
        throw new IllegalArgumentException("concorrencia fora do intervalo permitido");
      }
      if (maxTentativas < 1 || maxTentativas > 10) {
        throw new IllegalArgumentException("tentativas fora do intervalo permitido");
      }
      if (backoffInicial == null || backoffInicial.isNegative()) {
        throw new IllegalArgumentException("backoff invalido");
      }
      if (timeoutExecucao == null || timeoutExecucao.isZero() || timeoutExecucao.isNegative()
          || timeoutExecucao.compareTo(Duration.ofHours(24)) > 0) {
        throw new IllegalArgumentException("timeout de execucao invalido");
      }
    }
  }

  public record RelatorioExecucao(
      String manifestoSha256,
      boolean dryRun,
      List<ResultadoItem> resultados) {

    public RelatorioExecucao {
      resultados = List.copyOf(resultados);
    }
  }

  public record ResultadoItem(
      String itemFingerprint,
      StatusResultado status,
      long tamanhoBytes,
      String motivo) {

    static ResultadoItem sucesso(Item item, StatusResultado status) {
      return new ResultadoItem(item.fingerprint(), status, item.tamanhoBytes(), null);
    }

    static ResultadoItem decisao(Item item, StatusResultado status) {
      return new ResultadoItem(item.fingerprint(), status, 0, item.motivo());
    }

    static ResultadoItem falha(Item item, StatusResultado status, String reason) {
      return new ResultadoItem(item.fingerprint(), status, 0, reason);
    }

    public boolean sucesso() {
      return status == StatusResultado.VALIDADA
          || status == StatusResultado.COPIADA
          || status == StatusResultado.PRESERVADA;
    }
  }

  public enum StatusResultado {
    VALIDADA,
    COPIADA,
    PRESERVADA,
    DESCARTADA,
    QUARENTENA,
    BLOQUEADA,
    INTERROMPIDA,
    FALHA
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  @FunctionalInterface
  interface ProgressoListener {
    void registrar(ResultadoItem resultado);
  }
}
