package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class R2SigV4ClientConditionalPutTest {

  @Test
  void usaIfNoneMatchAssinadoENuncaSobrescreve() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger calls = new AtomicInteger();
    List<String> authorizations = new ArrayList<>();
    List<String> conditions = new ArrayList<>();
    server.createContext("/", exchange -> {
      conditions.add(exchange.getRequestHeaders().getFirst("If-None-Match"));
      authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
      int status = calls.incrementAndGet() == 1 ? 200 : 412;
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    server.start();
    try {
      R2SigV4Client client = new R2SigV4Client(
          HttpClient.newHttpClient(),
          URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
          "auto",
          "access-key-ficticia",
          "assinatura-ficticia");

      assertThat(client.putIfAbsent("bucket", "hml/midias-pendentes/foto.jpg", new byte[] {1}, "image/jpeg"))
          .isEqualTo(ObjectWriteResult.CREATED);
      assertThat(client.putIfAbsent("bucket", "hml/midias-pendentes/foto.jpg", new byte[] {1}, "image/jpeg"))
          .isEqualTo(ObjectWriteResult.ALREADY_EXISTS);
      assertThat(conditions).containsExactly("*", "*");
      assertThat(authorizations).allSatisfy(value ->
          assertThat(value).contains("SignedHeaders=host;if-none-match;x-amz-content-sha256;x-amz-date"));
    } finally {
      server.stop(0);
    }
  }
}
