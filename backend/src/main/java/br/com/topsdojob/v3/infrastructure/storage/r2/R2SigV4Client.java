package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class R2SigV4Client implements R2Operations {

  private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter
      .ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter
      .ofPattern("yyyyMMdd")
      .withZone(ZoneOffset.UTC);
  private static final String EMPTY_PAYLOAD_HASH = sha256Hex(new byte[0]);

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
    RequestSignature signature = signRequest("HEAD", bucket, key, EMPTY_PAYLOAD_HASH, Map.of());
    HttpRequest request = requestBuilder(signature.uri())
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
        .header("Authorization", signature.authorization())
        .build();
    HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding(), "HEAD");
    if (response.statusCode() == 200) {
      return true;
    }
    if (response.statusCode() == 404) {
      return false;
    }
    throw statusException("HEAD", response.statusCode());
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

  @Override
  public StoredObjectPage list(
      String bucket,
      String prefix,
      String continuationToken,
      int maxKeys) {
    Map<String, String> query = new TreeMap<>();
    query.put("list-type", "2");
    query.put("max-keys", Integer.toString(maxKeys));
    query.put("prefix", prefix);
    if (continuationToken != null && !continuationToken.isBlank()) {
      query.put("continuation-token", continuationToken);
    }
    RequestSignature signature = signRequest(
        "GET", bucket, null, EMPTY_PAYLOAD_HASH, Map.of(), query);
    HttpRequest request = requestBuilder(signature.uri())
        .GET()
        .header("x-amz-date", signature.amzDate())
        .header("x-amz-content-sha256", EMPTY_PAYLOAD_HASH)
        .header("Authorization", signature.authorization())
        .build();
    HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray(), "LIST");
    requireStatus(response.statusCode(), "LIST", 200);
    return parseListResponse(response.body());
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
      String method,
      String bucket,
      String key,
      String payloadHash,
      Map<String, String> additionalHeaders,
      Map<String, String> query) {
    Instant now = Instant.now();
    String amzDate = AMZ_DATE.format(now);
    String dateStamp = DATE_STAMP.format(now);
    String canonicalPath = rawPath(bucket, key);
    Map<String, String> headers = new TreeMap<>();
    headers.put("host", host);
    headers.put("x-amz-content-sha256", payloadHash);
    headers.put("x-amz-date", amzDate);
    additionalHeaders.forEach((name, value) -> headers.put(name.toLowerCase(), value.trim()));
    String signedHeaders = String.join(";", headers.keySet());
    StringBuilder canonicalHeaders = new StringBuilder();
    headers.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));
    String canonicalQuery = canonicalQuery(new TreeMap<>(query));
    String canonicalRequest = method + "\n"
        + canonicalPath + "\n"
        + canonicalQuery + "\n"
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
    return new RequestSignature(objectUri(bucket, key, canonicalQuery), amzDate, authHeaderValue);
  }

  private HttpRequest.Builder requestBuilder(URI uri) {
    return HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3));
  }

  private URI objectUri(String bucket, String key) {
    return objectUri(bucket, key, "");
  }

  private URI objectUri(String bucket, String key, String canonicalQuery) {
    String suffix = canonicalQuery == null || canonicalQuery.isBlank() ? "" : "?" + canonicalQuery;
    return URI.create(endpoint.getScheme() + "://" + host + rawPath(bucket, key) + suffix);
  }

  private String rawPath(String bucket, String key) {
    String base = "/" + R2UrlCodec.encodeQueryValue(bucket) + "/";
    return key == null || key.isBlank() ? base : base + R2UrlCodec.encodePath(key);
  }

  private StoredObjectPage parseListResponse(byte[] body) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
      List<StoredObjectMetadata> objects = new ArrayList<>();
      NodeList contents = document.getElementsByTagName("Contents");
      for (int index = 0; index < contents.getLength(); index++) {
        Element element = (Element) contents.item(index);
        objects.add(new StoredObjectMetadata(
            childText(element, "Key"),
            Long.parseLong(childText(element, "Size")),
            childText(element, "ETag"),
            Instant.parse(childText(element, "LastModified"))));
      }
      String nextCursor = firstText(document.getElementsByTagName("NextContinuationToken"));
      boolean truncated = Boolean.parseBoolean(firstText(document.getElementsByTagName("IsTruncated")));
      return new StoredObjectPage(objects, nextCursor, truncated);
    } catch (Exception exception) {
      throw new R2StorageException("Resposta XML invalida na listagem R2", exception);
    }
  }

  private String childText(Element parent, String tag) {
    return firstText(parent.getElementsByTagName(tag));
  }

  private String firstText(NodeList nodes) {
    return nodes == null || nodes.getLength() == 0 || nodes.item(0) == null
        ? null
        : nodes.item(0).getTextContent();
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
