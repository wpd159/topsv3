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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MotorCopiaMidiaFaseCincoS3CompatIntegrationTest {

  private static final byte[] JPEG = new byte[] {
      (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 7, 6, 5, 4
  };

  @Test
  void usaClienteR2RealContraHarnessS3SinteticoComPutCondicional() throws Exception {
    S3Harness harness = new S3Harness();
    harness.start();
    try {
      R2StorageProperties properties = properties();
      R2SigV4Client operations = new R2SigV4Client(
          HttpClient.newHttpClient(),
          harness.endpoint(),
          "auto",
          "credencial-sintetica-nao-secreta",
          "assinatura-sintetica-nao-secreta");
      ObjectStorage storage = new R2ObjectStorage(properties, operations);
      String sourceKey = properties.getPrivateMediaPrefix() + "origem/foto.jpg";
      String destinationKey = properties.getPrivateMediaPrefix() + "importacao/foto.jpg";
      harness.seed(properties.getPrivateMediaBucket(), sourceKey, JPEG, "image/jpeg");
      Item item = item(properties, sourceKey, destinationKey);
      MotorCopiaMidiaFaseCinco engine = new MotorCopiaMidiaFaseCinco(
          FonteMidiaMigracao.objectStorage(storage),
          storage,
          new NoopCheckpoint(),
          new Config(2, 2, Duration.ZERO));

      var first = engine.executar(
          UUID.fromString("22222222-2222-4222-8222-222222222222"),
          new ManifestoMidiaFaseCinco(java.util.List.of(item)),
          false);
      var second = engine.executar(
          UUID.fromString("33333333-3333-4333-8333-333333333333"),
          new ManifestoMidiaFaseCinco(java.util.List.of(item)),
          false);

      assertThat(first.resultados()).extracting(result -> result.status())
          .containsExactly(StatusResultado.COPIADA);
      assertThat(second.resultados()).extracting(result -> result.status())
          .containsExactly(StatusResultado.PRESERVADA);
      assertThat(harness.conditionalPuts.get()).isEqualTo(1);
      assertThat(harness.deleteRequests.get()).isZero();
      assertThat(harness.authorizationHeaders.get()).isPositive();
      assertThat(storage.get(StorageArea.PRIVATE_MEDIA, destinationKey).content())
          .containsExactly(JPEG);
    } finally {
      harness.stop();
    }
  }

  private Item item(R2StorageProperties properties, String sourceKey, String destinationKey) {
    return new Item(
        "midia-sintetica",
        EntidadeTipo.ANUNCIO,
        "anuncio-origem",
        "anuncio-v3",
        "usuario-origem",
        "usuario-v3",
        "referencia-sintetica",
        Finalidade.GALERIA,
        TipoMidia.FOTO,
        Visibilidade.RESTRITA_18,
        EstadoModeracao.APROVADA,
        true,
        false,
        1,
        "image/jpeg",
        JPEG.length,
        sha256(JPEG),
        new Origem(TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_MEDIA, sourceKey),
        new Destino(
            StorageArea.PRIVATE_MEDIA,
            properties.getPrivateMediaBucket(),
            destinationKey),
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

  private static final class NoopCheckpoint implements CheckpointMidiaMigracao {

    @Override
    public Optional<Registro> buscar(UUID executionId, String itemFingerprint) {
      return Optional.empty();
    }

    @Override
    public void registrarConcluido(
        UUID executionId, Item item, String checksum, long size, String mimeType) {
      // O harness valida a escrita; persistencia de checkpoint e coberta separadamente.
    }
  }

  private static final class S3Harness {

    private final Map<String, Value> objects = new ConcurrentHashMap<>();
    private final AtomicInteger conditionalPuts = new AtomicInteger();
    private final AtomicInteger deleteRequests = new AtomicInteger();
    private final AtomicInteger authorizationHeaders = new AtomicInteger();
    private HttpServer server;

    void start() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", this::handle);
      server.start();
    }

    void stop() {
      if (server != null) {
        server.stop(0);
      }
    }

    URI endpoint() {
      return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void seed(String bucket, String key, byte[] content, String contentType) {
      objects.put("/" + bucket + "/" + key, new Value(content.clone(), contentType));
    }

    private void handle(HttpExchange exchange) throws IOException {
      if (exchange.getRequestHeaders().getFirst("Authorization") != null) {
        authorizationHeaders.incrementAndGet();
      }
      String path = exchange.getRequestURI().getRawPath();
      switch (exchange.getRequestMethod()) {
        case "HEAD" -> respondHead(exchange, path);
        case "GET" -> respondGet(exchange, path);
        case "PUT" -> respondPut(exchange, path);
        case "DELETE" -> {
          deleteRequests.incrementAndGet();
          objects.remove(path);
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        }
        default -> {
          exchange.sendResponseHeaders(405, -1);
          exchange.close();
        }
      }
    }

    private void respondHead(HttpExchange exchange, String path) throws IOException {
      Value value = objects.get(path);
      if (value == null) {
        exchange.sendResponseHeaders(404, -1);
      } else {
        exchange.getResponseHeaders().set("Content-Type", value.contentType());
        exchange.sendResponseHeaders(200, -1);
      }
      exchange.close();
    }

    private void respondGet(HttpExchange exchange, String path) throws IOException {
      Value value = objects.get(path);
      if (value == null) {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
        return;
      }
      exchange.getResponseHeaders().set("Content-Type", value.contentType());
      exchange.sendResponseHeaders(200, value.content().length);
      exchange.getResponseBody().write(value.content());
      exchange.close();
    }

    private void respondPut(HttpExchange exchange, String path) throws IOException {
      byte[] content = exchange.getRequestBody().readAllBytes();
      String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      boolean conditional = "*".equals(exchange.getRequestHeaders().getFirst("If-None-Match"));
      if (conditional) {
        conditionalPuts.incrementAndGet();
      }
      if (conditional && objects.putIfAbsent(path, new Value(content, contentType)) != null) {
        exchange.sendResponseHeaders(412, -1);
      } else {
        if (!conditional) {
          objects.put(path, new Value(content, contentType));
        }
        exchange.sendResponseHeaders(200, -1);
      }
      exchange.close();
    }

    private record Value(byte[] content, String contentType) {
    }
  }
}
