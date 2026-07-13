package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
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
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    this.host = endpoint.getHost();
  }

  @Override
  public void put(String bucket, String key, byte[] content, String contentType) {
    String payloadHash = sha256Hex(content);
    RequestSignature signature = signRequest("PUT", bucket, key, payloadHash);
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
  public boolean exists(String bucket, String key) {
    RequestSignature signature = signRequest("HEAD", bucket, key, EMPTY_PAYLOAD_HASH);
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
    RequestSignature signature = signRequest("GET", bucket, key, EMPTY_PAYLOAD_HASH);
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
    RequestSignature signature = signRequest("DELETE", bucket, key, EMPTY_PAYLOAD_HASH);
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

  private RequestSignature signRequest(String method, String bucket, String key, String payloadHash) {
    Instant now = Instant.now();
    String amzDate = AMZ_DATE.format(now);
    String dateStamp = DATE_STAMP.format(now);
    String canonicalPath = rawPath(bucket, key);
    String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
    String canonicalHeaders = "host:" + host + "\n"
        + "x-amz-content-sha256:" + payloadHash + "\n"
        + "x-amz-date:" + amzDate + "\n";
    String canonicalRequest = method + "\n"
        + canonicalPath + "\n\n"
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
    return new RequestSignature(objectUri(bucket, key), amzDate, authHeaderValue);
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
