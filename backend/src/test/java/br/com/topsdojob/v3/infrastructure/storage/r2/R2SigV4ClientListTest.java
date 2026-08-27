package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class R2SigV4ClientListTest {

  @Test
  void assinaListObjectsV2EPreservaPaginacaoEMetadados() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> query = new AtomicReference<>();
    AtomicReference<String> signedRequestHeader = new AtomicReference<>();
    server.createContext("/", exchange -> {
      query.set(exchange.getRequestURI().getRawQuery());
      signedRequestHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] body = """
          <?xml version="1.0" encoding="UTF-8"?>
          <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
            <IsTruncated>true</IsTruncated>
            <Contents>
              <Key>hml/midias-aprovadas/restritas-borradas/v1/a.jpg</Key>
              <LastModified>2026-08-27T12:00:00Z</LastModified>
              <ETag>&quot;etag-a&quot;</ETag>
              <Size>321</Size>
            </Contents>
            <NextContinuationToken>proxima/pagina+segura=</NextContinuationToken>
          </ListBucketResult>
          """.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
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

      var page = client.list(
          "bucket",
          "hml/midias-aprovadas/restritas-borradas/v1/",
          "pagina/anterior+=",
          500);

      assertThat(page.truncated()).isTrue();
      assertThat(page.nextContinuationToken()).isEqualTo("proxima/pagina+segura=");
      assertThat(page.objects()).singleElement().satisfies(item -> {
        assertThat(item.key()).endsWith("/a.jpg");
        assertThat(item.size()).isEqualTo(321L);
        assertThat(item.etag()).isEqualTo("\"etag-a\"");
        assertThat(item.lastModified()).isEqualTo(Instant.parse("2026-08-27T12:00:00Z"));
      });
      assertThat(query.get())
          .contains("list-type=2")
          .contains("max-keys=500")
          .contains("prefix=hml%2Fmidias-aprovadas%2Frestritas-borradas%2Fv1%2F")
          .contains("continuation-token" + "=pagina%2Fanterior%2B%3D");
      assertThat(signedRequestHeader.get()).contains(
          "SignedHeaders=host;x-amz-content-sha256;x-amz-date");
    } finally {
      server.stop(0);
    }
  }
}
