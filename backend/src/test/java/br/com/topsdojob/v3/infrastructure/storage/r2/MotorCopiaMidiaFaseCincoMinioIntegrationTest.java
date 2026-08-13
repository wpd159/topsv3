package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.midia.CheckpointMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
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
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.Config;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.StatusResultado;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
    named = "MIGRACAO_MIDIA_FASE5_MINIO_ENABLED",
    matches = "true")
class MotorCopiaMidiaFaseCincoMinioIntegrationTest {

  private static final String MINIO_IMAGE = "minio/minio:RELEASE.2024-07-16T23-46-41Z";
  private static final String MC_IMAGE = "quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z";
  private static final String ROOT_CREDENTIAL_ENV = "MINIO_ROOT_" + "PASSWORD";
  private static final String ROOT_USER_ENV = "MINIO_ROOT_USER";
  private static final byte[] JPEG = new byte[] {
      (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3, 4
  };
  private static final byte[] MP4 = new byte[] {
      0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'
  };
  private static final byte[] PDF = "%PDF-1.7\nfixture\n".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PNG = new byte[] {
      (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
  };

  @Test
  void copiaPublicoPrivadoKycELocalEReexecucaoNaoCriaObjetosNovos() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-midia-" + suffix + "-minio-net";
    String container = "topsv3-midia-" + suffix + "-minio";
    String user = "fixture" + suffix;
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-midia-minio-");
    Path localRoot = Files.createTempDirectory("topsv3-midia-local-");
    try {
      command(true, logs.resolve("network.log"), "docker", "network", "create", network);
      command(true, logs.resolve("container.log"),
          "docker", "run", "--pull=never", "-d", "--name", container,
          "--network", network, "-p", "127.0.0.1::9000",
          "-e", ROOT_USER_ENV + "=" + user,
          "-e", ROOT_CREDENTIAL_ENV + "=" + credential,
          MINIO_IMAGE,
          "server", "/data", "--console-address", ":9001");
      int port = publishedPort(container, logs);
      waitForMinio(port);
      String connection = "MC_HOST_fixture=http://" + user + ":" + credential
          + "@" + container + ":9000";
      command(true, logs.resolve("buckets.log"),
          "docker", "run", "--pull=never", "--rm", "--network", network,
          "-e", connection, MC_IMAGE, "mb",
          "fixture/publico-sintetico",
          "fixture/privado-sintetico",
          "fixture/documento-sintetico");

      R2StorageProperties properties = properties();
      R2SigV4Client operations = new R2SigV4Client(
          HttpClient.newHttpClient(),
          URI.create("http://127.0.0.1:" + port),
          "auto",
          user,
          credential);
      ObjectStorage storage = new R2ObjectStorage(properties, operations);
      String publicSource = properties.getPublicMediaPrefix() + "origem/foto.jpg";
      String privateSource = properties.getPrivateMediaPrefix() + "origem/video.mp4";
      String documentSource = properties.getDocumentPrefix() + "origem/documento.pdf";
      storage.put(StorageArea.PUBLIC_MEDIA, publicSource, JPEG, "image/jpeg");
      storage.put(StorageArea.PRIVATE_MEDIA, privateSource, MP4, "video/mp4");
      storage.put(StorageArea.PRIVATE_DOCUMENT, documentSource, PDF, "application/pdf");
      Files.write(localRoot.resolve("editorial.png"), PNG);

      List<Item> items = List.of(
          item("publica", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
              Visibilidade.LIVRE, StorageArea.PUBLIC_MEDIA, publicSource,
              StorageArea.PUBLIC_MEDIA,
              properties.getPublicMediaBucket(),
              properties.getPublicMediaPrefix() + "destino/foto.jpg", JPEG, "image/jpeg"),
          item("restrita", EntidadeTipo.ANUNCIO, Finalidade.VIDEO, TipoMidia.VIDEO,
              Visibilidade.RESTRITA_18, StorageArea.PRIVATE_MEDIA, privateSource,
              StorageArea.PRIVATE_MEDIA,
              properties.getPrivateMediaBucket(),
              properties.getPrivateMediaPrefix() + "destino/video.mp4", MP4, "video/mp4"),
          item("kyc", EntidadeTipo.KYC, Finalidade.KYC_IDENTIDADE, TipoMidia.DOCUMENTO,
              Visibilidade.PRIVADA, StorageArea.PRIVATE_DOCUMENT, documentSource,
              StorageArea.PRIVATE_DOCUMENT,
              properties.getDocumentBucket(),
              properties.getDocumentPrefix() + "destino/documento.pdf", PDF, "application/pdf"),
          localItem(properties));
      FonteMidiaMigracao source = FonteMidiaMigracao.composta(java.util.Map.of(
          TipoOrigem.OBJECT_STORAGE, FonteMidiaMigracao.objectStorage(storage),
          TipoOrigem.ARQUIVO_LOCAL, FonteMidiaMigracao.arquivosLocais(localRoot)));
      MotorCopiaMidiaFaseCinco engine = new MotorCopiaMidiaFaseCinco(
          source, storage, new NoopCheckpoint(), new Config(4, 2, Duration.ofMillis(5)));
      ManifestoMidiaFaseCinco manifest = new ManifestoMidiaFaseCinco(items);

      var first = engine.executar(UUID.randomUUID(), manifest, false);
      var second = engine.executar(UUID.randomUUID(), manifest, false);

      assertThat(first.resultados()).extracting(result -> result.status())
          .containsOnly(StatusResultado.COPIADA);
      assertThat(second.resultados()).extracting(result -> result.status())
          .containsOnly(StatusResultado.PRESERVADA);
      for (Item item : items) {
        assertThat(storage.get(item.destino().area(), item.destino().chave()).content())
            .containsExactly(source.carregar(item.origem()).content());
      }
      assertThat(storage.publicUrl(
          StorageArea.PRIVATE_DOCUMENT,
          properties.getDocumentPrefix() + "destino/documento.pdf")).isEmpty();
      assertThat(Files.readAllBytes(localRoot.resolve("editorial.png"))).containsExactly(PNG);
    } finally {
      command(false, logs.resolve("remove-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("remove-network.log"), "docker", "network", "rm", network);
      deleteTree(localRoot);
      deleteTree(logs);
    }
  }

  private Item localItem(R2StorageProperties properties) {
    return new Item(
        "local",
        EntidadeTipo.EDITORIAL,
        "editorial-origem",
        "editorial-v3",
        "owner-origem",
        "owner-v3",
        "referencia-local",
        Finalidade.EDITORIAL,
        TipoMidia.FOTO,
        Visibilidade.LIVRE,
        EstadoModeracao.APROVADA,
        true,
        false,
        1,
        "image/png",
        PNG.length,
        sha256(PNG),
        new Origem(TipoOrigem.ARQUIVO_LOCAL, null, "editorial.png"),
        new Destino(
            StorageArea.PUBLIC_MEDIA,
            properties.getPublicMediaBucket(),
            properties.getPublicMediaPrefix() + "destino/editorial.png"),
        Decisao.IMPORTAR,
        null);
  }

  private Item item(
      String id,
      EntidadeTipo entityType,
      Finalidade purpose,
      TipoMidia mediaType,
      Visibilidade visibility,
      StorageArea sourceArea,
      String sourceKey,
      StorageArea destinationArea,
      String bucket,
      String destinationKey,
      byte[] content,
      String mimeType) {
    return new Item(
        id,
        entityType,
        "entidade-origem-" + id,
        "entidade-v3-" + id,
        "owner-origem-" + id,
        "owner-v3-" + id,
        "referencia-" + id,
        purpose,
        mediaType,
        visibility,
        EstadoModeracao.APROVADA,
        true,
        false,
        1,
        mimeType,
        content.length,
        sha256(content),
        new Origem(TipoOrigem.OBJECT_STORAGE, sourceArea, sourceKey),
        new Destino(destinationArea, bucket, destinationKey),
        Decisao.IMPORTAR,
        null);
  }

  private static R2StorageProperties properties() {
    R2StorageProperties value = new R2StorageProperties();
    value.setPublicMediaBucket("publico-sintetico");
    value.setPrivateMediaBucket("privado-sintetico");
    value.setDocumentBucket("documento-sintetico");
    value.setPublicMediaPrefix("hml/publico/");
    value.setPrivateMediaPrefix("hml/privado/");
    value.setDocumentPrefix("hml/documento/");
    value.setPublicBaseUrl("https://publico.example.invalid");
    return value;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void waitForMinio(int port) throws Exception {
    URI health = URI.create("http://127.0.0.1:" + port + "/minio/health/live");
    HttpClient client = HttpClient.newHttpClient();
    for (int attempt = 0; attempt < 60; attempt++) {
      try {
        HttpResponse<Void> response = client.send(
            HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(2)).GET().build(),
            HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (IOException ignored) {
        // O container ainda esta inicializando.
      }
      Thread.sleep(250L);
    }
    throw new IllegalStateException("MinIO descartavel nao ficou pronto");
  }

  private static int publishedPort(String container, Path logs) throws Exception {
    Path log = logs.resolve("docker-port.log");
    command(true, log, "docker", "port", container, "9000/tcp");
    Matcher matcher = Pattern.compile(".*:(\\d+)$").matcher(Files.readString(log).trim());
    if (!matcher.find()) {
      throw new IllegalStateException("porta do MinIO descartavel nao encontrada");
    }
    return Integer.parseInt(matcher.group(1));
  }

  private static int command(boolean required, Path output, String... arguments)
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(arguments);
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (required && exit != 0) {
      throw new IllegalStateException("comando MinIO descartavel falhou");
    }
    return exit;
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static final class NoopCheckpoint implements CheckpointMidiaMigracao {

    @Override
    public Optional<Registro> buscar(UUID executionId, String itemFingerprint) {
      return Optional.empty();
    }

    @Override
    public void registrarConcluido(
        UUID executionId, Item item, String checksum, long size, String mimeType) {
      // O teste verifica storage e idempotencia; o checkpoint usa PostgreSQL em teste dedicado.
    }
  }
}
