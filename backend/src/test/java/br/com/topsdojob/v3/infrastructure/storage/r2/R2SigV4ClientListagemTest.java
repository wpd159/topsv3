package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaOrcamento;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class R2SigV4ClientListagemTest {

  private static final String PREFIX = "hml/midias-aprovadas/restritas-borradas/v1/";
  private static final String BUCKET = "public-local-test";
  private static final String SECRET = "EXEMPLO_NAO_REAL";
  private static final String NAMESPACE = "http://s3.amazonaws.com/doc/2006-03-01/";

  @Test
  void inventarioPreservaPaginaInteiraMetadadosECursorComMesmoSignerNaRaiz() throws Exception {
    String prefix = "hml/midias-aprovadas/ação +*/";
    String cursor = "opaque+/=%?&*";
    List<String> objects = IntStream.range(0, 1358).mapToObj(index -> prefix + index + ".jpg").toList();
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    HttpServer server = server(exchange -> {
      try {
        calls.incrementAndGet();
        assertSignedList(exchange);
        Map<String, String> query = query(exchange.getRequestURI());
        assertThat(query).containsEntry("prefix", prefix).containsEntry("max-keys", "1000")
            .containsEntry("list-type", "2").doesNotContainKey("encoding-type");
        boolean first = !query.containsKey("continuation-token");
        if (!first) assertThat(query.get("continuation-token")).isEqualTo(cursor);
        StringBuilder xml = new StringBuilder("<ListBucketResult><IsTruncated>")
            .append(first).append("</IsTruncated>");
        for (int index = first ? 0 : 1000; index < (first ? 1000 : objects.size()); index++) {
          xml.append("<Contents><Key>").append(xmlText(objects.get(index))).append("</Key>")
              .append("<Size>").append(index + 10).append("</Size><ETag>etag-").append(index)
              .append("</ETag><LastModified>2026-08-27T12:00:00Z</LastModified></Contents>");
        }
        if (first) xml.append("<NextContinuationToken>").append(xmlText(cursor))
            .append("</NextContinuationToken>");
        respond(exchange, 200, xml.append("</ListBucketResult>").toString());
      } catch (Throwable error) { failure.set(error); exchange.close(); }
    });
    try {
      R2SigV4Client client = new R2SigV4Client(HttpClient.newHttpClient(),
          URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
          "auto", "access-key-sintetica", SECRET);
      var first = client.list(BUCKET, prefix, null, 1000);
      assertThat(first.objects()).hasSize(1000);
      assertThat(first.truncated()).isTrue();
      assertThat(first.nextContinuationToken()).isEqualTo(cursor);
      var second = client.list(BUCKET, prefix, first.nextContinuationToken(), 1000);
      assertThat(second.objects()).hasSize(358);
      assertThat(second.truncated()).isFalse();
      assertThat(second.nextContinuationToken()).isNull();
      var combined = java.util.stream.Stream.concat(first.objects().stream(), second.objects().stream()).toList();
      assertThat(combined).extracting(br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata::key)
          .containsExactlyElementsOf(objects);
      for (int index = 0; index < combined.size(); index++) {
        assertThat(combined.get(index).size()).isEqualTo(index + 10L);
        assertThat(combined.get(index).etag()).isEqualTo("etag-" + index);
        assertThat(combined.get(index).lastModified()).isEqualTo(java.time.Instant.parse("2026-08-27T12:00:00Z"));
      }
      assertThat(calls).hasValue(2);
      assertThat(failure.get()).isNull();
    } finally { server.stop(0); }
  }

  @Test
  void pagina1358ObjetosComAssinaturaExataETokenOpacoSemCacheEntreDescobertas() throws Exception {
    List<String> objects = IntStream.range(0, 1358).mapToObj(index -> PREFIX + index + ".jpg").toList();
    AtomicReference<List<String>> current = new AtomicReference<>(objects);
    AtomicInteger calls = new AtomicInteger();
    List<String> methods = new java.util.concurrent.CopyOnWriteArrayList<>();
    AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    String continuation = "opaque+/=%?&*";
    HttpServer server = server(exchange -> {
      try {
        calls.incrementAndGet();
        methods.add(exchange.getRequestMethod());
        assertSignedList(exchange);
        Map<String, String> query = query(exchange.getRequestURI());
        assertThat(query).containsEntry("prefix", PREFIX).containsEntry("encoding-type", "url")
            .containsEntry("max-keys", "1000").containsEntry("list-type", "2");
        assertThat(query).doesNotContainKeys("delimiter", "start-after");
        List<String> available = current.get();
        boolean first = !query.containsKey("continuation-token");
        if (!first) assertThat(query.get("continuation-token")).isEqualTo(continuation);
        int from = first ? 0 : 1000;
        int end = Math.min(available.size(), from + 1000);
        respond(exchange, 200, page(available.subList(from, end), end < available.size(),
            first ? continuation : null, first ? null : continuation));
      } catch (Throwable failure) {
        serverFailure.set(failure);
        exchange.close();
      }
    });
    try {
      R2VerificacaoAgrupadaPreviews verifier = helper(server);
      Set<String> requested = new LinkedHashSet<>(objects.subList(0, 1311));
      String missing = PREFIX + "missing.jpg";
      requested.add(missing);
      assertThat(verifier.verificar(requested)).containsExactlyInAnyOrderElementsOf(objects.subList(0, 1311));
      assertThat(calls).hasValue(2);
      current.set(objects.subList(1, objects.size()));
      assertThat(verifier.verificar(requested)).containsExactlyInAnyOrderElementsOf(objects.subList(1, 1311));
      assertThat(calls).hasValue(4);
      assertThat(methods).containsOnly("GET");
      assertThat(serverFailure.get()).isNull();
      assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
    } finally { server.stop(0); }
  }

  @ParameterizedTest
  @ValueSource(strings = {"error-root", "missing-truncated", "invalid-truncated", "missing-cursor",
      "unexpected-cursor", "duplicate-truncated", "incomplete-metadata", "duplicate-key",
      "wrong-bucket", "wrong-prefix", "grouped-prefix", "negative-size"})
  void inventario200MalformadoOuIncompletoNuncaViraPaginaVaziaValida(String scenario) throws Exception {
    String item = "<Contents><Key>" + PREFIX + "photo.jpg</Key><Size>100</Size><ETag>etag</ETag>"
        + "<LastModified>2026-08-27T12:00:00Z</LastModified></Contents>";
    String body = switch (scenario) {
      case "error-root" -> "<Error><Code>InternalError</Code></Error>";
      case "missing-truncated" -> "<ListBucketResult/>";
      case "invalid-truncated" -> "<ListBucketResult><IsTruncated>unknown</IsTruncated></ListBucketResult>";
      case "missing-cursor" -> "<ListBucketResult><IsTruncated>true</IsTruncated></ListBucketResult>";
      case "unexpected-cursor" -> "<ListBucketResult><IsTruncated>false</IsTruncated><NextContinuationToken>x</NextContinuationToken></ListBucketResult>";
      case "duplicate-truncated" -> "<ListBucketResult><IsTruncated>true</IsTruncated><IsTruncated>false</IsTruncated></ListBucketResult>";
      case "incomplete-metadata" -> "<ListBucketResult><IsTruncated>false</IsTruncated><Contents><Key>x</Key></Contents></ListBucketResult>";
      case "duplicate-key" -> "<ListBucketResult><IsTruncated>false</IsTruncated>" + item + item + "</ListBucketResult>";
      case "wrong-bucket" -> "<ListBucketResult><Name>different</Name><IsTruncated>false</IsTruncated></ListBucketResult>";
      case "wrong-prefix" -> "<ListBucketResult><Prefix>different</Prefix><IsTruncated>false</IsTruncated></ListBucketResult>";
      case "grouped-prefix" -> "<ListBucketResult><IsTruncated>false</IsTruncated><CommonPrefixes/></ListBucketResult>";
      case "negative-size" -> "<ListBucketResult><IsTruncated>false</IsTruncated>" + item.replace("<Size>100", "<Size>-1") + "</ListBucketResult>";
      default -> throw new AssertionError(scenario);
    };
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> { calls.incrementAndGet(); respond(exchange, 200, body); });
    try {
      R2SigV4Client client = new R2SigV4Client(HttpClient.newHttpClient(),
          URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "auto", "access-key-sintetica", SECRET);
      assertThatThrownBy(() -> client.list(BUCKET, PREFIX, null, 1000))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida")
          .hasMessageNotContaining(SECRET).hasMessageNotContaining(body);
      assertThat(calls).hasValue(1);
    } finally { server.stop(0); }
  }

  @Test
  void comparaChaveCompletaCaseSensitiveEUrlDecodeUmaVezSemConfundirOriginaisOuPrefixos() throws Exception {
    List<String> found = List.of(PREFIX + "foto.jpg", PREFIX + "a+b.jpg", PREFIX + "a b.jpg",
        PREFIX + "literal%2F.jpg", PREFIX + "ação.jpg", PREFIX + "original.jpg-extra");
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> {
      calls.incrementAndGet();
      String xml = page(found, false, null, null)
          .replace("a%2Bb.jpg", "a+b.jpg");
      respond(exchange, 200, xml);
    });
    try {
      Set<String> requested = new LinkedHashSet<>(found.subList(0, 5));
      requested.add(PREFIX + "FOTO.jpg");
      requested.add(PREFIX + "literal/.jpg");
      requested.add(PREFIX + "original.jpg");
      assertThat(helper(server).verificar(requested)).containsExactlyInAnyOrderElementsOf(found.subList(0, 5));
      assertThat(calls).hasValue(1);
    } finally { server.stop(0); }
  }

  @Test
  void encerraCedoSomenteQuandoTodasChavesExatasForamProvadasEmPaginaValida() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> {
      calls.incrementAndGet();
      respond(exchange, 200, page(List.of(PREFIX + "present.jpg"), true, "next", null));
    });
    try {
      assertThat(helper(server).verificar(Set.of(PREFIX + "present.jpg")))
          .containsExactly(PREFIX + "present.jpg");
      assertThat(calls).hasValue(1);
    } finally { server.stop(0); }
  }

  @ParameterizedTest
  @ValueSource(strings = {"root", "namespace", "bucket", "prefix", "encoding", "no-truncated",
      "truncated-invalid", "missing-token", "common-prefix", "duplicate-field", "nested-key",
      "bad-percent", "unicode-percent", "bad-utf8", "key-count", "duplicate-key", "max-keys", "out-of-prefix", "dtd", "malformed"})
  void corpo200InvalidoNaoProvaPresencaNemAusencia(String scenario) throws Exception {
    String key = PREFIX + "present.jpg";
    String normal = page(List.of(key), false, null, null);
    String xml = switch (scenario) {
      case "root" -> "<Error><Code>InternalError</Code></Error>";
      case "namespace" -> normal.replace(NAMESPACE, "urn:unexpected");
      case "bucket" -> normal.replace(BUCKET, "different-bucket");
      case "prefix" -> normal.replace("<Prefix>" + encoded(PREFIX), "<Prefix>wrong");
      case "encoding" -> normal.replace("<EncodingType>url</EncodingType>", "");
      case "no-truncated" -> normal.replace("<IsTruncated>false</IsTruncated>", "");
      case "truncated-invalid" -> normal.replace("<IsTruncated>false", "<IsTruncated>perhaps");
      case "missing-token" -> normal.replace("<IsTruncated>false", "<IsTruncated>true");
      case "common-prefix" -> normal.replace("</ListBucketResult>",
          "<CommonPrefixes><Prefix>" + encoded(key) + "</Prefix></CommonPrefixes></ListBucketResult>");
      case "duplicate-field" -> normal.replace("</ListBucketResult>", "<IsTruncated>false</IsTruncated></ListBucketResult>");
      case "nested-key" -> normal.replace("<Key>", "<Key><Nested>").replace("</Key>", "</Nested></Key>");
      case "bad-percent" -> normal.replace(encoded(key), "%ZZ");
      case "unicode-percent" -> normal.replace(encoded(key), encoded(PREFIX) + "%４１");
      case "bad-utf8" -> normal.replace(encoded(key), encoded(PREFIX) + "%C3%28");
      case "key-count" -> normal.replace("<KeyCount>1", "<KeyCount>2");
      case "duplicate-key" -> page(List.of(key, key), false, null, null);
      case "max-keys" -> normal.replace("<MaxKeys>1000", "<MaxKeys>999");
      case "out-of-prefix" -> normal.replace("<Key>" + encoded(PREFIX), "<Key>other/");
      case "dtd" -> "<!DOCTYPE ListBucketResult [<!ENTITY xxe SYSTEM 'file:///not-readable-synthetic'>]>" + normal;
      case "malformed" -> normal.substring(0, normal.length() - 4);
      default -> throw new AssertionError(scenario);
    };
    HttpServer server = server(exchange -> respond(exchange, 200, xml));
    try {
      assertThatThrownBy(() -> helper(server).verificar(Set.of(key)))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida");
      if ("duplicate-key".equals(scenario)) {
        assertThatThrownBy(() -> helper(server).verificar(Set.of(PREFIX + "absent.jpg")))
            .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida");
      }
    } finally { server.stop(0); }
  }

  @Test
  void tokenRepetidoFalhaMesmoQuandoAPaginaRepetidaContemAUltimaChave() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> {
      int call = calls.incrementAndGet();
      respond(exchange, 200, page(List.of(PREFIX + (call == 1 ? "other" : "wanted") + ".jpg"),
          true, "same-token", call == 1 ? null : "same-token"));
    });
    try {
      assertThatThrownBy(() -> helper(server).verificar(Set.of(PREFIX + "wanted.jpg")))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida");
      assertThat(calls).hasValue(2);
    } finally { server.stop(0); }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void segundaPaginaSemEcoOuComEcoDivergenteNaoProvaAusenciaNemRetornaProvasParciais(boolean missingEcho)
      throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> {
      if (calls.incrementAndGet() == 1) {
        respond(exchange, 200, page(List.of(PREFIX + "first-present.jpg"), true, "expected-token", null));
      } else {
        assertThat(query(exchange.getRequestURI())).containsEntry("continuation-token", "expected-token");
        respond(exchange, 200, page(List.of(), false, null, missingEcho ? null : "different-token"));
      }
    });
    try {
      assertThatThrownBy(() -> helper(server).verificar(Set.of(PREFIX + "first-present.jpg", PREFIX + "missing.jpg")))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida");
      assertThat(calls).hasValue(2);
    } finally { server.stop(0); }
  }

  @Test
  void oitoPaginasIncompletasNaoViraramAusenciaNemFallbackHead() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> {
      int call = calls.incrementAndGet();
      assertThat(exchange.getRequestMethod()).isEqualTo("GET");
      respond(exchange, 200, page(List.of(PREFIX + "unrelated.jpg"), true,
          "token-" + call, call == 1 ? null : "token-" + (call - 1)));
    });
    try {
      assertThatThrownBy(() -> helper(server).verificar(Set.of(PREFIX + "wanted.jpg")))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("Limite de paginas");
      assertThat(calls).hasValue(8);
    } finally { server.stop(0); }
  }

  @Test
  void paginaComMaisDeMilItensEhRejeitadaMesmoSeAChaveEstiverPresente() throws Exception {
    List<String> items = IntStream.range(0, 1001).mapToObj(index -> PREFIX + index + ".jpg").toList();
    HttpServer server = server(exchange -> respond(exchange, 200, page(items, false, null, null)));
    try {
      assertThatThrownBy(() -> helper(server).verificar(Set.of(items.get(0))))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida");
    } finally { server.stop(0); }
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 404, 429, 500, 503})
  void httpFalhoEhInconclusivoSemRetryEProximaDescobertaPodeRecuperar(int status) throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> respond(exchange, calls.incrementAndGet() == 1 ? status : 200,
        page(List.of(PREFIX + "present.jpg"), false, null, null)));
    try {
      R2VerificacaoAgrupadaPreviews verifier = helper(server);
      assertThatThrownBy(() -> verifier.verificar(Set.of(PREFIX + "present.jpg")))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("HTTP " + status)
          .hasMessageNotContaining(PREFIX).hasMessageNotContaining(SECRET);
      assertThat(calls).hasValue(1);
      assertThat(verifier.verificar(Set.of(PREFIX + "present.jpg"))).containsExactly(PREFIX + "present.jpg");
      assertThat(calls).hasValue(2);
    } finally { server.stop(0); }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void limitaBytesDuranteRecepcaoComContentLengthOuChunked(boolean declaredLength) throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch finished = new CountDownLatch(1);
    HttpServer server = server(exchange -> {
      calls.incrementAndGet();
      try {
        exchange.sendResponseHeaders(200, declaredLength ? 1024 * 1024 + 1 : 0);
        byte[] chunk = new byte[8192];
        for (int index = 0; index < 130; index++) exchange.getResponseBody().write(chunk);
      } catch (IOException expectedCancellation) {
        // The client cancels the subscriber instead of buffering this oversized body.
      } finally { exchange.close(); finished.countDown(); }
    });
    try {
      assertThatThrownBy(() -> helper(server).verificar(Set.of(PREFIX + "present.jpg")))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("invalida");
      assertThat(calls).hasValue(1);
      assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
    } finally { server.stop(0); }
  }

  @Test
  void prazoIncluiCorpoBloqueadoAposHeadersECancelaEsperaSemDeclararServidorEncerrado() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch headers = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    var executor = Executors.newCachedThreadPool();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(executor);
    server.createContext("/", exchange -> {
      int call = calls.incrementAndGet();
      if (call != 2) {
        respond(exchange, 200, page(List.of(PREFIX + "present.jpg"), false, null, null));
        return;
      }
      try {
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write("<ListBucketResult".getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
        headers.countDown();
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } finally { exchange.close(); finished.countDown(); }
    });
    server.start();
    try {
      R2VerificacaoAgrupadaPreviews verifier = helper(server);
      assertThat(verifier.verificar(Set.of(PREFIX + "present.jpg"))).hasSize(1);
      long start = System.nanoTime();
      assertThatThrownBy(() -> new LocalidadesConsultaOrcamento(Duration.ofMillis(250))
          .executar(() -> verifier.verificar(Set.of(PREFIX + "present.jpg"))))
          .isInstanceOfAny(R2StorageException.class, org.springframework.web.server.ResponseStatusException.class);
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertThat(headers.getCount()).isZero();
      assertThat(elapsed).isLessThan(1000);
      assertThat(finished.getCount()).isEqualTo(1);
      assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
      release.countDown();
      assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(verifier.verificar(Set.of(PREFIX + "present.jpg"))).hasSize(1);
      assertThat(calls).hasValue(3);
      System.out.printf("LOCALIDADES_LIST_BODY_TIMEOUT budgetMs=250 elapsedMs=%d requests=3 recovery=true%n", elapsed);
    } finally {
      release.countDown(); server.stop(0); executor.shutdownNow();
      assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void interrupcaoCancelaCorpoEmVooEPreservaFlagDoWorker() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean interrupted = new AtomicBoolean();
    HttpServer server = server(exchange -> {
      try {
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write('<');
        exchange.getResponseBody().flush();
        entered.countDown();
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
      finally { exchange.close(); }
    });
    Thread worker = new Thread(() -> {
      try {
        helper(server).verificar(Set.of(PREFIX + "present.jpg"));
      } catch (RuntimeException expected) {
        interrupted.set(Thread.currentThread().isInterrupted());
      }
    }, "local-list-interrupt-test");
    try {
      worker.start();
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      worker.interrupt();
      worker.join(1500);
      assertThat(worker.isAlive()).isFalse();
      assertThat(interrupted).isTrue();
    } finally { release.countDown(); worker.interrupt(); worker.join(2000); server.stop(0); }
  }

  @Test
  void helperPreservaValidacaoCompletaLazyNamespaceELimiteSemIoOuTransacao() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = server(exchange -> { calls.incrementAndGet(); respond(exchange, 500, ""); });
    try {
      R2VerificacaoAgrupadaPreviews disabled = new R2VerificacaoAgrupadaPreviews(new R2StorageProperties());
      assertThat(disabled.verificar(Set.of())).isEmpty();
      assertThatThrownBy(() -> disabled.verificar(Set.of(PREFIX + "a"))).isInstanceOf(R2StorageException.class);
      R2StorageProperties invalidHttp = properties(server, false);
      assertThatThrownBy(() -> new R2VerificacaoAgrupadaPreviews(invalidHttp, HttpClient.newHttpClient())
          .verificar(Set.of(PREFIX + "a"))).isInstanceOf(R2StorageException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
      R2VerificacaoAgrupadaPreviews verifier = helper(server);
      for (String bad : List.of("/" + PREFIX + "a", "hml/private/a", PREFIX + "../a",
          PREFIX + "a\\b", PREFIX + "a?b", PREFIX + "a#b", PREFIX)) {
        assertThatThrownBy(() -> verifier.verificar(Set.of(bad))).isInstanceOf(R2StorageException.class);
      }
      Set<String> tooMany = IntStream.range(0, 4097).mapToObj(index -> PREFIX + index)
          .collect(java.util.stream.Collectors.toSet());
      assertThatThrownBy(() -> verifier.verificar(tooMany)).isInstanceOf(R2StorageException.class);
      TransactionSynchronizationManager.setActualTransactionActive(true);
      try {
        assertThatThrownBy(() -> verifier.verificar(Set.of(PREFIX + "a")))
            .isInstanceOf(R2StorageException.class).hasMessageContaining("transacao encerrada");
      } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
      assertThat(calls).hasValue(0);
    } finally { server.stop(0); }
  }

  private static R2VerificacaoAgrupadaPreviews helper(HttpServer server) {
    return new R2VerificacaoAgrupadaPreviews(properties(server, true), HttpClient.newHttpClient());
  }

  private static R2StorageProperties properties(HttpServer server, boolean localHttpTest) {
    R2StorageProperties result = new R2StorageProperties();
    result.setEnabled(true);
    result.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
    result.setAccessKey("access-key-sintetica");
    result.setSigningValue(SECRET);
    result.setPublicMediaBucket(BUCKET);
    result.setPrivateMediaBucket("private-local-test");
    result.setDocumentBucket("documents-local-test");
    result.setPublicBaseUrl("https://public-local.invalid");
    if (localHttpTest) {
      result = spy(result);
      doNothing().when(result).validateConfigured();
    }
    return result;
  }

  private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", handler); server.start(); return server;
  }

  private static void respond(HttpExchange exchange, int status, String xml) throws IOException {
    byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
    try {
      exchange.getResponseHeaders().set("Content-Type", "application/xml");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    } finally { exchange.close(); }
  }

  private static String page(List<String> keys, boolean truncated, String next, String token) {
    StringBuilder xml = new StringBuilder("<ListBucketResult xmlns=\"").append(NAMESPACE).append("\">")
        .append("<Name>").append(BUCKET).append("</Name><Prefix>").append(encoded(PREFIX))
        .append("</Prefix><EncodingType>url</EncodingType><MaxKeys>1000</MaxKeys><KeyCount>")
        .append(keys.size()).append("</KeyCount><IsTruncated>").append(truncated).append("</IsTruncated>");
    for (String key : keys) xml.append("<Contents><Key>").append(encoded(key))
        .append("</Key><ETag>synthetic</ETag><Size>100</Size></Contents>");
    if (token != null) xml.append("<ContinuationToken>").append(xmlText(token)).append("</ContinuationToken>");
    if (next != null) xml.append("<NextContinuationToken>").append(xmlText(next)).append("</NextContinuationToken>");
    return xml.append("</ListBucketResult>").toString();
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        .replace("%7E", "~").replace("*", "%2A");
  }

  private static String xmlText(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static Map<String, String> query(URI uri) {
    Map<String, String> result = new java.util.TreeMap<>();
    for (String field : uri.getRawQuery().split("&")) {
      String[] parts = field.split("=", 2);
      result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    return result;
  }

  private static void assertSignedList(HttpExchange exchange) throws Exception {
    String auth = exchange.getRequestHeaders().getFirst("Authorization");
    String date = exchange.getRequestHeaders().getFirst("x-amz-date");
    String payloadHash = exchange.getRequestHeaders().getFirst("x-amz-content-sha256");
    String host = exchange.getRequestHeaders().getFirst("Host");
    String scope = date.substring(0, 8) + "/auto/s3/aws4_request";
    String headers = "host;x-amz-content-sha256;x-amz-date";
    String rawQuery = exchange.getRequestURI().getRawQuery();
    List<String> fields = List.of(rawQuery.split("&"));
    assertThat(fields).isSorted();
    assertThat(rawQuery).doesNotContain("+", "*", " ");
    assertThat(exchange.getRequestMethod()).isEqualTo("GET");
    assertThat(exchange.getRequestURI().getRawPath()).isEqualTo("/" + BUCKET + "/");
    String canonical = "GET\n/" + BUCKET + "/\n" + rawQuery + "\nhost:" + host
        + "\nx-amz-content-sha256:" + payloadHash + "\nx-amz-date:" + date + "\n\n" + headers + "\n" + payloadHash;
    String stringToSign = "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n" + sha256(canonical);
    byte[] key = hmac(("AWS4" + SECRET).getBytes(StandardCharsets.UTF_8), date.substring(0, 8));
    key = hmac(hmac(hmac(key, "auto"), "s3"), "aws4_request");
    assertThat(auth).isEqualTo("AWS4-HMAC-SHA256 Credential=access-key-sintetica/" + scope
        + ", SignedHeaders=" + headers + ", Signature=" + HexFormat.of().formatHex(hmac(key, stringToSign)));
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] hmac(byte[] key, String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }
}
