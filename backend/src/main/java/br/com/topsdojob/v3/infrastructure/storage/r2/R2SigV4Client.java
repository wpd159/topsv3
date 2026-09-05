package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaOrcamento;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

final class R2SigV4Client implements R2Operations {

  private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter
      .ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter
      .ofPattern("yyyyMMdd")
      .withZone(ZoneOffset.UTC);
  private static final String EMPTY_PAYLOAD_HASH = sha256Hex(new byte[0]);
  private static final String S3_XML_NAMESPACE = "http://s3.amazonaws.com/doc/2006-03-01/";
  private static final int LIST_MAX_PAGES = 8;
  private static final int LIST_PAGE_ITEMS = 1000;
  private static final int LIST_PAGE_BYTES = 1024 * 1024;

  private final HttpClient httpClient;
  private final URI endpoint;
  private final String region;
  private final String accessKey;
  private final String signingValue;
  private final String host;

  R2SigV4Client(
      HttpClient httpClient,
      URI endpoint,
      String region,
      String accessKey,
      String signingValue) {
    this.httpClient = httpClient;
    this.endpoint = endpoint;
    this.region = region == null || region.isBlank() ? "auto" : region;
    this.accessKey = accessKey;
    this.signingValue = signingValue;
    this.host = endpoint.getRawAuthority();
  }

  @Override
  public void put(String bucket, String key, byte[] content, String contentType) {
    String payloadHash = sha256Hex(content);
    RequestSignature signature = signRequest("PUT", bucket, key, payloadHash, Map.of());
    HttpRequest request = requestBuilder(signature.uri())
        .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", payloadHash)
        .header("Authorization", signature.authorization())
        .header("Content-Type", normalizeContentType(contentType))
        .build();
    HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding(), "PUT");
    requireStatus(response.statusCode(), "PUT", 200);
  }

  @Override
  public ObjectWriteResult putIfAbsent(String bucket, String key, byte[] content, String contentType) {
    String payloadHash = sha256Hex(content);
    RequestSignature signature = signRequest(
        "PUT", bucket, key, payloadHash, Map.of("if-none-match", "*"));
    HttpRequest request = requestBuilder(signature.uri())
        .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
        .header("If-None-Match", "*")
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", payloadHash)
        .header("Authorization", signature.authorization())
        .header("Content-Type", normalizeContentType(contentType))
        .build();
    HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding(), "PUT_IF_ABSENT");
    if (response.statusCode() == 200) return ObjectWriteResult.CREATED;
    if (response.statusCode() == 412) return ObjectWriteResult.ALREADY_EXISTS;
    if (response.statusCode() == 409 && exists(bucket, key)) return ObjectWriteResult.ALREADY_EXISTS;
    throw statusException("PUT_IF_ABSENT", response.statusCode());
  }

  @Override
  public boolean exists(String bucket, String key) {
    LocalidadesConsultaOrcamento orcamento = LocalidadesConsultaOrcamento.atualOuNulo();
    if (orcamento != null) orcamento.conferir();
    RequestSignature signature = signRequest("HEAD", bucket, key, EMPTY_PAYLOAD_HASH, Map.of());
    HttpRequest.Builder builder = requestBuilder(signature.uri());
    if (orcamento != null) {
      orcamento.conferir();
      builder.timeout(Duration.ofMillis(Math.min(Duration.ofMinutes(3).toMillis(), orcamento.restanteMillis())));
    }
    HttpRequest request = builder
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
        .header("Authorization", signature.authorization())
        .build();
    HttpResponse<Void> response = orcamento == null
        ? send(request, HttpResponse.BodyHandlers.discarding(), "HEAD")
        : orcamento.medir("storage_head", () -> send(request, HttpResponse.BodyHandlers.discarding(), "HEAD"));
    if (orcamento != null) orcamento.conferir();
    if (response.statusCode() == 200) {
      return true;
    }
    if (response.statusCode() == 404) {
      return false;
    }
    throw statusException("HEAD", response.statusCode());
  }

  /** No result survives this invocation; a failed or incomplete scan proves no absence. */
  Set<String> listarExistentes(String bucket, String prefix, Set<String> requested) {
    LocalidadesConsultaOrcamento budget = LocalidadesConsultaOrcamento.atualOuNulo();
    if (budget == null) {
      return new LocalidadesConsultaOrcamento(Duration.ofMillis(3500))
          .executar(() -> listarExistentes(bucket, prefix, requested));
    }
    budget.conferir();
    if (bucket == null || bucket.isBlank() || prefix == null || prefix.isBlank()
        || requested == null || requested.size() > 4096) throw invalidList();
    Set<String> pending = new LinkedHashSet<>();
    for (String key : requested) {
      budget.conferir();
      if (key == null || !key.startsWith(prefix) || key.length() == prefix.length()) throw invalidList();
      pending.add(key);
    }
    if (pending.isEmpty()) return Set.of();
    Set<String> found = new LinkedHashSet<>();
    Set<String> tokens = new HashSet<>();
    String token = null;
    int totalItems = 0;
    int totalBytes = 0;
    for (int pageNumber = 0; pageNumber < LIST_MAX_PAGES; pageNumber++) {
      budget.conferir();
      Map<String, String> query = new TreeMap<>();
      query.put("list-type", "2");
      query.put("encoding-type", "url");
      query.put("max-keys", Integer.toString(LIST_PAGE_ITEMS));
      query.put("prefix", prefix);
      if (token != null) query.put("continuation-token", token);
      RequestSignature signature = signRequest("GET", bucket, "", EMPTY_PAYLOAD_HASH, Map.of(), query);
      HttpRequest request = HttpRequest.newBuilder(signature.uri())
          .timeout(Duration.ofMillis(budget.restanteMillis()))
          .GET().header("x-amz-date", signature.amzDate())
          .header("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
          .header("Authorization", signature.authorization()).build();
      byte[] body = budget.medir("storage_list", () -> receiveList(request, budget));
      totalBytes += body.length;
      if (totalBytes > LIST_MAX_PAGES * LIST_PAGE_BYTES) throw invalidList();
      ListPage page = parseList(body, bucket, prefix, token, budget);
      totalItems += page.keys().size();
      if (totalItems > LIST_MAX_PAGES * LIST_PAGE_ITEMS) throw invalidList();
      if (page.truncated() && (page.nextToken() == null || page.nextToken().isBlank()
          || !tokens.add(page.nextToken()))) throw invalidList();
      for (String key : page.keys()) {
        budget.conferir();
        if (pending.remove(key)) found.add(key);
      }
      budget.conferir();
      // A complete set of positive proofs can stop early; a missing key cannot.
      if (pending.isEmpty() || !page.truncated()) return Set.copyOf(found);
      token = page.nextToken();
    }
    throw new R2StorageException("Limite de paginas da listagem R2 excedido");
  }

  private byte[] receiveList(HttpRequest request, LocalidadesConsultaOrcamento budget) {
    LimitedListBody body = new LimitedListBody(budget);
    CompletableFuture<HttpResponse<byte[]>> future = null;
    boolean completed = false;
    try {
      budget.conferir();
      future = httpClient.sendAsync(request, info -> {
        if (info.statusCode() != 200) {
          body.fail(statusException("LIST", info.statusCode()));
        } else {
          List<String> lengths = info.headers().allValues("content-length");
          try {
            if (lengths.size() > 1 || (!lengths.isEmpty()
                && (Long.parseLong(lengths.get(0)) < 0
                    || Long.parseLong(lengths.get(0)) > LIST_PAGE_BYTES))) body.fail(invalidList());
          } catch (NumberFormatException exception) {
            body.fail(invalidList());
          }
        }
        return body;
      });
      HttpResponse<byte[]> response = future.get(budget.restanteMillis(), TimeUnit.MILLISECONDS);
      budget.conferir();
      requireStatus(response.statusCode(), "LIST", 200);
      completed = true;
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new R2StorageException("Listagem R2 interrompida");
    } catch (TimeoutException exception) {
      throw new R2StorageException("Prazo da listagem R2 excedido");
    } catch (ExecutionException exception) {
      // Never expose an opaque continuation token, response body or signed request in errors.
      R2StorageException failure = body.failure();
      if (failure != null) throw failure;
      throw new R2StorageException("Falha de transporte na listagem R2");
    } finally {
      if (!completed) {
        body.fail(new R2StorageException("Listagem R2 cancelada"));
        if (future != null) future.cancel(true);
      }
    }
  }

  private static ListPage parseList(byte[] body, String bucket, String prefix, String token,
      LocalidadesConsultaOrcamento budget) {
    budget.conferir();
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setAttribute("jdk.xml.maxElementDepth", "16");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var parser = factory.newDocumentBuilder();
      parser.setErrorHandler(new DefaultHandler() {
        @Override public void error(SAXParseException exception) throws SAXException { throw exception; }
        @Override public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
      });
      Element root = parser.parse(new ByteArrayInputStream(body)).getDocumentElement();
      budget.conferir();
      if (!"ListBucketResult".equals(root.getLocalName())
          || !S3_XML_NAMESPACE.equals(root.getNamespaceURI())) throw invalidList();
      Map<String, String> fields = new TreeMap<>();
      List<String> keys = new java.util.ArrayList<>();
      for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
        budget.conferir();
        if (!(node instanceof Element element)) continue;
        if (!S3_XML_NAMESPACE.equals(element.getNamespaceURI())) throw invalidList();
        String name = element.getLocalName();
        if ("Contents".equals(name)) {
          if (keys.size() >= LIST_PAGE_ITEMS) throw invalidList();
          String key = null;
          for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            budget.conferir();
            if (child instanceof Element field && "Key".equals(field.getLocalName())) {
              if (key != null || !S3_XML_NAMESPACE.equals(field.getNamespaceURI())) throw invalidList();
              key = decodeListValue(simpleText(field));
            }
          }
          if (key == null || !key.startsWith(prefix)) throw invalidList();
          keys.add(key);
        } else if ("CommonPrefixes".equals(name) || "Delimiter".equals(name)) {
          // This request never uses a delimiter; grouped prefixes cannot prove absence.
          throw invalidList();
        } else if (Set.of("Name", "Prefix", "EncodingType", "IsTruncated", "KeyCount",
            "MaxKeys", "ContinuationToken", "NextContinuationToken").contains(name)) {
          if (fields.putIfAbsent(name, simpleText(element)) != null) throw invalidList();
        } else {
          throw invalidList();
        }
      }
      if (!bucket.equals(fields.get("Name")) || !"url".equals(fields.get("EncodingType"))
          || !prefix.equals(decodeListValue(fields.get("Prefix")))) throw invalidList();
      String truncatedValue = fields.get("IsTruncated");
      if (!"true".equals(truncatedValue) && !"false".equals(truncatedValue)) throw invalidList();
      boolean truncated = "true".equals(truncatedValue);
      String next = fields.get("NextContinuationToken");
      if (truncated && (next == null || next.isBlank() || next.length() > 16384)) throw invalidList();
      if (!truncated && next != null && !next.isEmpty()) throw invalidList();
      if (!java.util.Objects.equals(token, fields.get("ContinuationToken"))) {
        throw invalidList();
      }
      if (fields.containsKey("MaxKeys") && Integer.parseInt(fields.get("MaxKeys")) != LIST_PAGE_ITEMS) {
        throw invalidList();
      }
      if (fields.containsKey("KeyCount") && Integer.parseInt(fields.get("KeyCount")) != keys.size()) {
        throw invalidList();
      }
      budget.conferir();
      return new ListPage(keys, truncated, next);
    } catch (R2StorageException exception) {
      throw exception;
    } catch (org.springframework.web.server.ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalidList();
    }
  }

  private static String simpleText(Element element) {
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element || child.getNodeType() == Node.ENTITY_REFERENCE_NODE) throw invalidList();
    }
    return element.getTextContent();
  }

  private static String decodeListValue(String value) {
    if (value == null) throw invalidList();
    ByteArrayOutputStream decoded = new ByteArrayOutputStream();
    for (int offset = 0; offset < value.length();) {
      char character = value.charAt(offset++);
      if (character == '%') {
        if (offset + 1 >= value.length()) throw invalidList();
        char highCharacter = value.charAt(offset++);
        char lowCharacter = value.charAt(offset++);
        if (highCharacter > 127 || lowCharacter > 127) throw invalidList();
        int high = Character.digit(highCharacter, 16);
        int low = Character.digit(lowCharacter, 16);
        if (high < 0 || low < 0) throw invalidList();
        decoded.write(high * 16 + low);
      } else {
        // URL-encoded XML is ASCII; a literal plus stays plus, not form-encoded space.
        if (character > 127) throw invalidList();
        decoded.write(character);
      }
    }
    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(decoded.toByteArray())).toString();
    } catch (CharacterCodingException exception) {
      throw invalidList();
    }
  }

  private static R2StorageException invalidList() {
    return new R2StorageException("Resposta ou identidade da listagem R2 invalida");
  }

  private record ListPage(List<String> keys, boolean truncated, String nextToken) { }

  /** Bounded while receiving, including chunked bodies without Content-Length. */
  private static final class LimitedListBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final LocalidadesConsultaOrcamento budget;
    private Flow.Subscription subscription;
    private R2StorageException failure;

    private LimitedListBody(LocalidadesConsultaOrcamento budget) { this.budget = budget; }
    @Override public CompletionStage<byte[]> getBody() { return result; }
    @Override public synchronized void onSubscribe(Flow.Subscription incoming) {
      if (subscription != null || result.isDone()) { incoming.cancel(); return; }
      subscription = incoming;
      incoming.request(1);
    }
    @Override public synchronized void onNext(List<ByteBuffer> buffers) {
      if (result.isDone()) return;
      try {
        budget.conferir();
        for (ByteBuffer buffer : buffers) {
          if (buffer.remaining() > LIST_PAGE_BYTES - bytes.size()) throw invalidList();
          byte[] chunk = new byte[buffer.remaining()];
          buffer.get(chunk);
          bytes.writeBytes(chunk);
        }
        subscription.request(1);
      } catch (RuntimeException exception) {
        fail(invalidList());
      }
    }
    @Override public synchronized void onError(Throwable error) {
      fail(new R2StorageException("Falha de transporte na listagem R2"));
    }
    @Override public synchronized void onComplete() {
      if (!result.isDone()) result.complete(bytes.toByteArray());
    }
    private synchronized void fail(R2StorageException exception) {
      if (result.isDone()) return;
      failure = exception;
      if (subscription != null) subscription.cancel();
      result.completeExceptionally(exception);
    }
    private synchronized R2StorageException failure() { return failure; }
  }

  @Override
  public StoredObject get(String bucket, String key) {
    RequestSignature signature = signRequest("GET", bucket, key, EMPTY_PAYLOAD_HASH, Map.of());
    HttpRequest request = requestBuilder(signature.uri())
        .GET()
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
        .header("Authorization", signature.authorization())
        .build();
    HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray(), "GET");
    requireStatus(response.statusCode(), "GET", 200);
    String contentType = response.headers()
        .firstValue("content-type")
        .orElse("application/octet-stream");
    return new StoredObject(response.body(), contentType);
  }

  @Override
  public void delete(String bucket, String key) {
    RequestSignature signature = signRequest("DELETE", bucket, key, EMPTY_PAYLOAD_HASH, Map.of());
    HttpRequest request = requestBuilder(signature.uri())
        .method("DELETE", HttpRequest.BodyPublishers.noBody())
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
        .header("Authorization", signature.authorization())
        .build();
    HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding(), "DELETE");
    if (response.statusCode() != 200
        && response.statusCode() != 204
        && response.statusCode() != 404) {
      throw statusException("DELETE", response.statusCode());
    }
  }

  @Override
  public URI presignGet(String bucket, String key, Duration ttl) {
    long expires = Math.max(1, Math.min(ttl.toSeconds(), Duration.ofDays(7).toSeconds()));
    Instant now = Instant.now();
    String amzDate = AMZ_DATE.format(now);
    String dateStamp = DATE_STAMP.format(now);
    String canonicalPath = rawPath(bucket, key);
    String scope = scope(dateStamp);

    Map<String, String> query = new TreeMap<>();
    query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
    query.put("X-Amz-Credential", accessKey + "/" + scope);
    query.put("X-Amz-Date", amzDate);
    query.put("X-Amz-Expires", Long.toString(expires));
    query.put("X-Amz-SignedHeaders", "host");

    String canonicalQuery = canonicalQuery(query);
    String canonicalRequest = "GET\n"
        + canonicalPath + "\n"
        + canonicalQuery + "\n"
        + "host:" + host + "\n\n"
        + "host\n"
        + "UNSIGNED-PAYLOAD";
    String stringToSign = "AWS4-HMAC-SHA256\n"
        + amzDate + "\n"
        + scope + "\n"
        + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    String signedDigest = hmacHex(signingKey(dateStamp), stringToSign);
    return URI.create(endpoint.getScheme() + "://" + host + canonicalPath
        + "?" + canonicalQuery + "&X-Amz-Signature=" + signedDigest);
  }

  private RequestSignature signRequest(
      String method,
      String bucket,
      String key,
      String payloadHash,
      Map<String, String> additionalHeaders) {
    return signRequest(method, bucket, key, payloadHash, additionalHeaders, Map.of());
  }

  private RequestSignature signRequest(
      String method, String bucket, String key, String payloadHash,
      Map<String, String> additionalHeaders, Map<String, String> query) {
    Instant now = Instant.now();
    String amzDate = AMZ_DATE.format(now);
    String dateStamp = DATE_STAMP.format(now);
    String canonicalPath = rawPath(bucket, key);
    String encodedQuery = canonicalQuery(new TreeMap<>(query)).replace("*", "%2A");
    Map<String, String> headers = new TreeMap<>();
    headers.put("host", host);
    headers.put("x-amz-content-sha256", payloadHash);
    headers.put("x-amz-date", amzDate);
    additionalHeaders.forEach((name, value) -> headers.put(name.toLowerCase(), value.trim()));
    String signedHeaders = String.join(";", headers.keySet());
    StringBuilder canonicalHeaders = new StringBuilder();
    headers.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));
    String canonicalRequest = method + "\n"
        + canonicalPath + "\n" + encodedQuery + "\n"
        + canonicalHeaders + "\n"
        + signedHeaders + "\n"
        + payloadHash;
    String scope = scope(dateStamp);
    String stringToSign = "AWS4-HMAC-SHA256\n"
        + amzDate + "\n"
        + scope + "\n"
        + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    String signedDigest = hmacHex(signingKey(dateStamp), stringToSign);
    String authHeaderValue = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
        + ", SignedHeaders=" + signedHeaders
        + ", Signature=" + signedDigest;
    URI uri = query.isEmpty() ? objectUri(bucket, key)
        : URI.create(objectUri(bucket, key).toString() + "?" + encodedQuery);
    return new RequestSignature(uri, amzDate, authHeaderValue);
  }

  private HttpRequest.Builder requestBuilder(URI uri) {
    return HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3));
  }

  private URI objectUri(String bucket, String key) {
    return URI.create(endpoint.getScheme() + "://" + host + rawPath(bucket, key));
  }

  private String rawPath(String bucket, String key) {
    return "/" + R2UrlCodec.encodeQueryValue(bucket) + "/" + R2UrlCodec.encodePath(key);
  }

  private String scope(String dateStamp) {
    return dateStamp + "/" + region + "/s3/aws4_request";
  }

  private byte[] signingKey(String dateStamp) {
    byte[] dateKey = hmac(("AWS4" + signingValue).getBytes(StandardCharsets.UTF_8), dateStamp);
    byte[] regionKey = hmac(dateKey, region);
    byte[] serviceKey = hmac(regionKey, "s3");
    return hmac(serviceKey, "aws4_request");
  }

  private static String canonicalQuery(Map<String, String> query) {
    StringBuilder builder = new StringBuilder();
    query.forEach((key, value) -> {
      if (!builder.isEmpty()) {
        builder.append('&');
      }
      builder.append(R2UrlCodec.encodeQueryValue(key))
          .append('=')
          .append(R2UrlCodec.encodeQueryValue(value));
    });
    return builder.toString();
  }

  private static String normalizeContentType(String contentType) {
    return contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType;
  }

  private static <T> HttpResponse<T> send(
      HttpClient client,
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      String operation) {
    try {
      return client.send(request, handler);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new R2StorageException("Operacao R2 interrompida: " + operation, exception);
    } catch (Exception exception) {
      throw new R2StorageException("Falha de transporte R2: " + operation, exception);
    }
  }

  private <T> HttpResponse<T> send(
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      String operation) {
    return send(httpClient, request, handler, operation);
  }

  private static void requireStatus(int status, String operation, int expected) {
    if (status != expected) {
      throw statusException(operation, status);
    }
  }

  private static R2StorageException statusException(String operation, int status) {
    return new R2StorageException("R2 " + operation + " retornou HTTP " + status);
  }

  private static String sha256Hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception exception) {
      throw new R2StorageException("SHA-256 indisponivel", exception);
    }
  }

  private static byte[] hmac(byte[] key, String content) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new R2StorageException("HMAC-SHA256 indisponivel", exception);
    }
  }

  private static String hmacHex(byte[] key, String content) {
    return HexFormat.of().formatHex(hmac(key, content));
  }

  private record RequestSignature(URI uri, String amzDate, String authorization) {
  }
}
