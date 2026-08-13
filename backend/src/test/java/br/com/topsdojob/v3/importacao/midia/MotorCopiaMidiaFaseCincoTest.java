package br.com.topsdojob.v3.importacao.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.midia.CheckpointMidiaMigracao.Registro;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.Config;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.RelatorioExecucao;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.StatusResultado;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MotorCopiaMidiaFaseCincoTest {

  private static final byte[] JPEG = new byte[] {
      (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3, 4
  };
  private static final UUID EXECUTION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  @TempDir
  Path temporaryDirectory;

  @Test
  void copiaUmaVezRetomaDoCheckpointEReexecucaoNaoDuplica() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    InMemoryCheckpoint checkpoint = new InMemoryCheckpoint();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");
    MotorCopiaMidiaFaseCinco engine = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, checkpoint, 3);

    RelatorioExecucao first = engine.executar(
        EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);
    RelatorioExecucao second = engine.executar(
        EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(first.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.COPIADA);
    assertThat(second.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.PRESERVADA);
    assertThat(storage.conditionalWrites.get()).isEqualTo(1);
    assertThat(storage.deletes.get()).isZero();
    assertThat(checkpoint.registrations.get()).isEqualTo(1);
  }

  @Test
  void colisaoDivergenteBloqueiaSemSobrescreverOuApagar() {
    InMemoryStorage storage = new InMemoryStorage();
    byte[] divergent = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 9, 9, 9, 9, 9};
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    storage.seed(StorageArea.PRIVATE_MEDIA, "destino/foto.jpg", divergent, "image/jpeg");
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, new InMemoryCheckpoint(), 2)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).singleElement().satisfies(result -> {
      assertThat(result.status()).isEqualTo(StatusResultado.BLOQUEADA);
      assertThat(result.motivo()).isEqualTo("CHECKSUM_DIVERGENTE");
    });
    assertThat(storage.get(StorageArea.PRIVATE_MEDIA, "destino/foto.jpg").content())
        .containsExactly(divergent);
    assertThat(storage.conditionalWrites.get()).isZero();
    assertThat(storage.deletes.get()).isZero();
  }

  @Test
  void arquivoLocalValidoECopiadoParaDestinoSinteticoSemAlterarOrigem() throws Exception {
    Path localFile = temporaryDirectory.resolve("foto-local.jpg");
    Files.write(localFile, JPEG);
    InMemoryStorage destination = new InMemoryStorage();
    Item item = withOrigin(
        item("local", StorageArea.PRIVATE_MEDIA, "origem/ignorada.jpg", "destino/local.jpg"),
        new Origem(TipoOrigem.ARQUIVO_LOCAL, null, "foto-local.jpg"));

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.arquivosLocais(temporaryDirectory),
        destination,
        new InMemoryCheckpoint(),
        1).executar(
            EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.COPIADA);
    assertThat(destination.get(StorageArea.PRIVATE_MEDIA, "destino/local.jpg").content())
        .containsExactly(JPEG);
    assertThat(destination.get(StorageArea.PRIVATE_MEDIA, "destino/local.jpg").contentType())
        .isEqualTo("image/jpeg");
    assertThat(Files.readAllBytes(localFile)).containsExactly(JPEG);
  }

  @Test
  void origemFisicaAusenteVaiParaQuarentena() {
    InMemoryStorage storage = new InMemoryStorage();
    Item item = item("ausente", StorageArea.PRIVATE_MEDIA, "origem/ausente.jpg", "destino/foto.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, new InMemoryCheckpoint(), 1)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).singleElement().satisfies(result -> {
      assertThat(result.status()).isEqualTo(StatusResultado.QUARENTENA);
      assertThat(result.motivo()).isEqualTo("OBJETO_ORIGEM_AUSENTE");
    });
  }

  @Test
  void retryEsgotadoEFalhaSemCheckpoint() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    storage.transientPutFailures.set(10);
    InMemoryCheckpoint checkpoint = new InMemoryCheckpoint();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, checkpoint, 2)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).singleElement().satisfies(result -> {
      assertThat(result.status()).isEqualTo(StatusResultado.FALHA);
      assertThat(result.motivo()).isEqualTo("FALHA_TRANSITORIA_ESGOTADA");
    });
    assertThat(storage.putAttempts.get()).isEqualTo(2);
    assertThat(checkpoint.registrations.get()).isZero();
  }

  @Test
  void interrupcaoNaoConcluiItemERetomadaCopiaSomenteOPendente() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    storage.transientPutFailures.set(1);
    InMemoryCheckpoint checkpoint = new InMemoryCheckpoint();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");
    MotorCopiaMidiaFaseCinco interrupted = new MotorCopiaMidiaFaseCinco(
        FonteMidiaMigracao.objectStorage(storage),
        storage,
        checkpoint,
        new Config(1, 3, Duration.ofMillis(1)),
        ignored -> {
          throw new InterruptedException("interrupcao sintetica");
        });

    RelatorioExecucao first = interrupted.executar(
        EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);
    storage.transientPutFailures.set(0);
    RelatorioExecucao resumed = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, checkpoint, 2)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(first.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.INTERROMPIDA);
    assertThat(resumed.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.COPIADA);
    assertThat(checkpoint.registrations.get()).isEqualTo(1);
  }

  @Test
  void retryTransitorioMantemMesmoDestinoECheckpointSoAposValidacao() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    storage.transientPutFailures.set(2);
    InMemoryCheckpoint checkpoint = new InMemoryCheckpoint();
    AtomicInteger sleeps = new AtomicInteger();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");
    MotorCopiaMidiaFaseCinco engine = new MotorCopiaMidiaFaseCinco(
        FonteMidiaMigracao.objectStorage(storage),
        storage,
        checkpoint,
        new Config(2, 3, Duration.ofMillis(1)),
        ignored -> sleeps.incrementAndGet());

    RelatorioExecucao report = engine.executar(
        EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.COPIADA);
    assertThat(storage.putAttempts.get()).isEqualTo(3);
    assertThat(sleeps.get()).isEqualTo(2);
    assertThat(checkpoint.registrations.get()).isEqualTo(1);
  }

  @Test
  void conteudoCorrompidoNoDestinoNuncaECheckpointado() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    storage.corruptWrites = true;
    InMemoryCheckpoint checkpoint = new InMemoryCheckpoint();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, checkpoint, 1)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.BLOQUEADA);
    assertThat(checkpoint.registrations.get()).isZero();
    assertThat(storage.deletes.get()).isZero();
  }

  @Test
  void dryRunValidaSemEscreverEOrigemSemMetadataEPermitidaPelaAssinatura() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(
        StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "application/octet-stream");
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, new InMemoryCheckpoint(), 1)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), true);

    assertThat(report.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.VALIDADA);
    assertThat(storage.exists(StorageArea.PRIVATE_MEDIA, "destino/foto.jpg")).isFalse();
  }

  @Test
  void bloqueiaUrlPublicaParaObjetoPrivado() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    storage.exposePrivateUrl = true;
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, new InMemoryCheckpoint(), 1)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).singleElement()
        .satisfies(result -> assertThat(result.motivo()).isEqualTo("OBJETO_PRIVADO_COM_URL_PUBLICA"));
  }

  @Test
  void arquivoLocalFicaConfinadoARaizEOriginalNaoEAlterado() throws Exception {
    Path source = temporaryDirectory.resolve("referenciada.jpg");
    Files.write(source, JPEG);
    FonteMidiaMigracao local = FonteMidiaMigracao.arquivosLocais(temporaryDirectory);

    StoredObject loaded = local.carregar(new Origem(
        TipoOrigem.ARQUIVO_LOCAL, null, "referenciada.jpg"));

    assertThat(loaded.content()).containsExactly(JPEG);
    assertThat(Files.readAllBytes(source)).containsExactly(JPEG);
    assertThatThrownBy(() -> local.carregar(new Origem(
        TipoOrigem.ARQUIVO_LOCAL, null, "../fora.jpg")))
        .isInstanceOf(FonteMidiaMigracao.OrigemMidiaInvalidaException.class);
  }

  @Test
  void itensComMesmoDestinoConcorrenteGeramUmaUnicaEscritaCondicional() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    Item first = item("foto-1", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/unico.jpg");
    Item second = item("foto-2", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/unico.jpg");

    RelatorioExecucao report = engine(
        FonteMidiaMigracao.objectStorage(storage), storage, new InMemoryCheckpoint(), 1)
        .executar(EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(first, second)), false);

    assertThat(report.resultados()).extracting(result -> result.status())
        .containsExactlyInAnyOrder(StatusResultado.COPIADA, StatusResultado.PRESERVADA);
    assertThat(storage.conditionalWrites.get()).isEqualTo(1);
  }

  @Test
  void publicaProgressoSemPermitirQueObservabilidadeQuebreACopia() {
    InMemoryStorage storage = new InMemoryStorage();
    storage.seed(StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", JPEG, "image/jpeg");
    AtomicInteger progress = new AtomicInteger();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");
    MotorCopiaMidiaFaseCinco engine = new MotorCopiaMidiaFaseCinco(
        FonteMidiaMigracao.objectStorage(storage),
        storage,
        new InMemoryCheckpoint(),
        new Config(1, 1, Duration.ZERO),
        Thread::sleep,
        ignored -> {
          progress.incrementAndGet();
          throw new IllegalStateException("listener sintetico");
        });

    RelatorioExecucao report = engine.executar(
        EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).extracting(result -> result.status())
        .containsExactly(StatusResultado.COPIADA);
    assertThat(progress.get()).isEqualTo(1);
  }

  @Test
  void timeoutCancelaItemSemMarcaLoComoConcluido() {
    InMemoryStorage storage = new InMemoryStorage();
    InMemoryCheckpoint checkpoint = new InMemoryCheckpoint();
    Item item = item("foto", StorageArea.PRIVATE_MEDIA, "origem/foto.jpg", "destino/foto.jpg");
    FonteMidiaMigracao slowSource = ignored -> {
      try {
        Thread.sleep(5_000L);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("origem sintetica interrompida", exception);
      }
      return new StoredObject(JPEG, "image/jpeg");
    };
    MotorCopiaMidiaFaseCinco engine = new MotorCopiaMidiaFaseCinco(
        slowSource,
        storage,
        checkpoint,
        new Config(1, 1, Duration.ZERO, Duration.ofMillis(20)));

    RelatorioExecucao report = engine.executar(
        EXECUTION_ID, new ManifestoMidiaFaseCinco(java.util.List.of(item)), false);

    assertThat(report.resultados()).singleElement().satisfies(result -> {
      assertThat(result.status()).isEqualTo(StatusResultado.INTERROMPIDA);
      assertThat(result.motivo()).isEqualTo("TIMEOUT_EXECUCAO");
    });
    assertThat(checkpoint.registrations.get()).isZero();
  }

  private MotorCopiaMidiaFaseCinco engine(
      FonteMidiaMigracao source,
      ObjectStorage destination,
      CheckpointMidiaMigracao checkpoint,
      int attempts) {
    return new MotorCopiaMidiaFaseCinco(
        source, destination, checkpoint, new Config(4, attempts, Duration.ZERO));
  }

  private Item item(String id, StorageArea sourceArea, String sourceKey, String destinationKey) {
    return new Item(
        id,
        EntidadeTipo.ANUNCIO,
        "anuncio-origem",
        "anuncio-v3",
        "usuario-origem",
        "usuario-v3",
        "referencia-" + id,
        Finalidade.GALERIA,
        TipoMidia.FOTO,
        Visibilidade.RESTRITA_18,
        EstadoModeracao.APROVADA,
        true,
        false,
        1,
        "image/jpeg",
        JPEG.length,
        MidiaMigracaoHashes.sha256(JPEG),
        new Origem(TipoOrigem.OBJECT_STORAGE, sourceArea, sourceKey),
        new Destino(StorageArea.PRIVATE_MEDIA, "bucket-privado-sintetico", destinationKey),
        Decisao.IMPORTAR,
        null);
  }

  private Item withOrigin(Item item, Origem origin) {
    return new Item(
        item.idOrigem(), item.entidadeTipo(), item.entidadeOrigemId(), item.entidadeV3Id(),
        item.proprietarioOrigemId(), item.proprietarioV3Id(), item.referenciaOrigemId(),
        item.finalidade(), item.tipoMidia(), item.visibilidade(), item.estadoModeracao(),
        item.originalExiste(), item.capaValida(), item.ordem(), item.mimeType(),
        item.tamanhoBytes(), item.sha256(), origin, item.destino(), item.decisao(), item.motivo());
  }

  private static final class InMemoryCheckpoint implements CheckpointMidiaMigracao {

    private final Map<String, Registro> values = new ConcurrentHashMap<>();
    private final AtomicInteger registrations = new AtomicInteger();

    @Override
    public Optional<Registro> buscar(UUID executionId, String itemFingerprint) {
      return Optional.ofNullable(values.get(executionId + ":" + itemFingerprint));
    }

    @Override
    public void registrarConcluido(
        UUID executionId, Item item, String checksum, long size, String mimeType) {
      registrations.incrementAndGet();
      values.put(
          executionId + ":" + item.fingerprint(),
          new Registro(
              item.fingerprint(),
              CheckpointMidiaMigracaoJdbc.destinoFingerprint(item),
              checksum,
              size,
              mimeType));
    }
  }

  private static final class InMemoryStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final AtomicInteger conditionalWrites = new AtomicInteger();
    private final AtomicInteger putAttempts = new AtomicInteger();
    private final AtomicInteger transientPutFailures = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();
    private volatile boolean corruptWrites;
    private volatile boolean exposePrivateUrl;

    void seed(StorageArea area, String key, byte[] content, String contentType) {
      objects.put(index(area, key), new StoredObject(content, contentType));
    }

    @Override
    public void put(StorageArea area, String key, byte[] content, String contentType) {
      objects.put(index(area, key), new StoredObject(content, contentType));
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        StorageArea area, String key, byte[] content, String contentType) {
      putAttempts.incrementAndGet();
      if (transientPutFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
        throw new IllegalStateException("falha transitoria sintetica");
      }
      byte[] value = corruptWrites ? Arrays.copyOf(content, content.length - 1) : content;
      StoredObject previous = objects.putIfAbsent(
          index(area, key), new StoredObject(value, contentType));
      if (previous == null) {
        conditionalWrites.incrementAndGet();
        return ObjectWriteResult.CREATED;
      }
      return ObjectWriteResult.ALREADY_EXISTS;
    }

    @Override
    public boolean exists(StorageArea area, String key) {
      return objects.containsKey(index(area, key));
    }

    @Override
    public StoredObject get(StorageArea area, String key) {
      StoredObject value = objects.get(index(area, key));
      if (value == null) {
        throw new IllegalStateException("ausente");
      }
      return value;
    }

    @Override
    public void delete(StorageArea area, String key) {
      deletes.incrementAndGet();
      objects.remove(index(area, key));
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      return URI.create("https://example.invalid/temporaria");
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      if (area == StorageArea.PUBLIC_MEDIA || exposePrivateUrl) {
        return Optional.of(URI.create("https://example.invalid/publica"));
      }
      return Optional.empty();
    }

    private String index(StorageArea area, String key) {
      return area + ":" + key;
    }
  }
}
