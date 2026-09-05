package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaOrcamento;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class R2SigV4ClientLocalidadesDeadlineTest {

  @Test
  void headRealDistinguePresenteAusenteEErroSemVazarOrcamento() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger calls = new AtomicInteger();
    server.createContext("/", exchange -> {
      calls.incrementAndGet();
      int status = switch (exchange.getRequestURI().getPath()) {
        case "/bucket/ausente" -> 404;
        case "/bucket/erro" -> 500;
        default -> 200;
      };
      assertThat(exchange.getRequestMethod()).isEqualTo("HEAD");
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    server.start();
    try {
      R2SigV4Client client = client(HttpClient.newHttpClient(), server);
      assertThat(new LocalidadesConsultaOrcamento(Duration.ofSeconds(2))
          .executar(() -> client.exists("bucket", "presente"))).isTrue();
      assertThat(new LocalidadesConsultaOrcamento(Duration.ofSeconds(2))
          .executar(() -> client.exists("bucket", "ausente"))).isFalse();
      assertThatThrownBy(() -> new LocalidadesConsultaOrcamento(Duration.ofSeconds(2))
          .executar(() -> client.exists("bucket", "erro")))
          .isInstanceOf(R2StorageException.class).hasMessageContaining("HTTP 500");
      assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
      assertThat(client.exists("bucket", "presente")).isTrue();
      assertThat(calls).hasValue(4);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void headLentoEhCanceladoPeloHttpClientDentroDoSaldoEProximaConsultaFunciona() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    CountDownLatch received = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch handlerFinished = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    server.createContext("/", exchange -> {
      boolean slow = exchange.getRequestURI().getPath().endsWith("/lento");
      try {
        calls.incrementAndGet();
        if (slow) {
          received.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
          }
        }
        exchange.sendResponseHeaders(200, -1);
      } finally {
        exchange.close();
        if (slow) handlerFinished.countDown();
      }
    });
    server.start();
    try {
      R2SigV4Client client = client(HttpClient.newHttpClient(), server);
      assertThat(client.exists("bucket", "aquecimento")).isTrue();
      long started = System.nanoTime();
      assertThatThrownBy(() -> new LocalidadesConsultaOrcamento(Duration.ofMillis(250))
          .executar(() -> client.exists("bucket", "lento")))
          .isInstanceOf(R2StorageException.class).hasCauseInstanceOf(HttpTimeoutException.class);
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      System.out.printf("LOCALIDADES_HEAD_TIMEOUT budgetMs=250 elapsedMs=%d httpRequests=%d remoteHandlerPending=%d%n",
          elapsedMillis, calls.get(), handlerFinished.getCount());
      assertThat(received.getCount()).isZero();
      assertThat(elapsedMillis).isLessThanOrEqualTo(750);
      assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
      // Client timeout ends its wait; it does not stop the deliberately blocked server handler.
      assertThat(handlerFinished.getCount()).isEqualTo(1);
      release.countDown();
      assertThat(handlerFinished.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(new LocalidadesConsultaOrcamento(Duration.ofSeconds(2))
          .executar(() -> client.exists("bucket", "seguinte"))).isTrue();
      assertThat(calls).hasValue(3);
      System.out.printf("LOCALIDADES_HEAD_RECOVERY httpRequests=%d remoteHandlerPending=%d%n",
          calls.get(), handlerFinished.getCount());
      assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
    } finally {
      release.countDown();
      server.stop(0);
      executor.shutdownNow();
      assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void somenteHeadNoContextoRecebeTimeoutReduzido() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(new byte[] {1});
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
    List<HttpRequest> requests = new ArrayList<>();
    doAnswer(invocation -> {
      requests.add(invocation.getArgument(0));
      return response;
    }).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    R2SigV4Client client = new R2SigV4Client(http, URI.create("http://127.0.0.1:1"),
        "auto", "access-key-ficticia", "assinatura-ficticia");

    new LocalidadesConsultaOrcamento(Duration.ofSeconds(2)).executar(() -> {
      client.exists("bucket", "foto");
      client.put("bucket", "foto", new byte[] {1}, "image/jpeg");
      client.putIfAbsent("bucket", "foto", new byte[] {1}, "image/jpeg");
      client.get("bucket", "foto");
      client.delete("bucket", "foto");
      return null;
    });
    client.exists("bucket", "fora-contexto");

    assertThat(requests).extracting(HttpRequest::method)
        .containsExactly("HEAD", "PUT", "PUT", "GET", "DELETE", "HEAD");
    assertThat(requests.get(0).timeout().orElseThrow()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(2));
    assertThat(requests.subList(1, requests.size())).allSatisfy(request ->
        assertThat(request.timeout()).contains(Duration.ofMinutes(3)));
    assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
  }

  private R2SigV4Client client(HttpClient http, HttpServer server) {
    return new R2SigV4Client(http, URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        "auto", "access-key-ficticia", "assinatura-ficticia");
  }
}
