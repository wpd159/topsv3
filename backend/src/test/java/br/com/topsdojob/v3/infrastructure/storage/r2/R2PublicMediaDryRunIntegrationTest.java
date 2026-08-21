package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "R2_PUBLIC_MEDIA_DRY_RUN_ENABLED", matches = "true")
class R2PublicMediaDryRunIntegrationTest {

  private static final int CONCURRENCY = 8;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

  @Test
  void migraManifestoPublicoComChecksumEIdempotencia() throws Exception {
    R2StorageProperties properties = propertiesFromEnvironment();
    properties.validateConfigured();
    assertThat(properties.getPublicMediaPrefix()).startsWith("hml/");

    HttpClient sourceClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    ObjectStorage storage = new ReadOnlyObjectStorage(new R2ObjectStorage(
        properties,
        new R2SigV4Client(
            sourceClient,
            URI.create(properties.getEndpoint()),
            properties.getRegion(),
            properties.getAccessKey(),
            properties.getSigningValue())));
    MidiaUploadProperties uploadProperties = new MidiaUploadProperties();
    MidiaUploadValidator validator = new MidiaUploadValidator(uploadProperties);

    Path input = Path.of(required("R2_PUBLIC_MEDIA_DRY_RUN_INPUT"));
    Path output = Path.of(required("R2_PUBLIC_MEDIA_DRY_RUN_OUTPUT"));
    List<Candidate> candidates = readCandidates(input);
    Map<String, Object> checksumLocks = new ConcurrentHashMap<>();
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
    List<Result> results = new ArrayList<>();
    try {
      List<Future<Result>> futures = candidates.stream()
          .map(candidate -> executor.submit(() -> plan(
              candidate,
              properties,
              sourceClient,
              storage,
              validator,
              checksumLocks)))
          .toList();
      for (Future<Result> future : futures) {
        results.add(future.get());
      }
    } finally {
      executor.shutdownNow();
    }

    results.sort(Comparator.comparingLong(Result::sourceAdId).thenComparing(Result::referenceHash));
    writeResults(output, results);
    long planned = count(results, "PLANEJADA");
    long preserved = count(results, "PRESERVADA");
    long quarantined = count(results, "QUARENTENA");
    long blocked = count(results, "BLOQUEADA");
    System.out.printf(
        "R2_PUBLIC_MEDIA_DRY_RUN total=%d planejadas=%d preservadas=%d quarentena=%d bloqueadas=%d%n",
        results.size(), planned, preserved, quarantined, blocked);
    assertThat(blocked).as("checksum divergente deve bloquear o dry-run").isZero();
    assertThat(planned + preserved).as("ao menos uma midia publica deve ser validada").isPositive();
  }

  private Result plan(
      Candidate candidate,
      R2StorageProperties properties,
      HttpClient sourceClient,
      ObjectStorage storage,
      MidiaUploadValidator validator,
      Map<String, Object> checksumLocks) {
    try {
      if (!md5(candidate.sourceUrl()).equals(candidate.referenceHash())) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "REFERENCIA_HASH_INVALIDO");
      }
      if (isGenericAsset(candidate.sourceUrl())) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "ASSET_GENERICO_OU_PLACEHOLDER");
      }
      HttpResponse<byte[]> response = sourceClient.send(
          HttpRequest.newBuilder(candidate.sourceUrl())
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "image/jpeg,image/png,image/webp")
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "HTTP_NAO_200");
      }
      String declaredMime = normalizeMime(response.headers().firstValue("Content-Type").orElse(""));
      String extension = extensionFor(declaredMime);
      if (extension == null) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "MIME_NAO_PERMITIDO");
      }
      byte[] content = response.body();
      MidiaUploadValidator.MidiaValidada validated;
      try {
        validated = validator.validar(new MockMultipartFile(
            "arquivo", "origem." + extension, declaredMime, content));
      } catch (ResponseStatusException exception) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "BINARIO_INVALIDO");
      }
      if (validated.video() || !validated.mimeType().equals(declaredMime)) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "TIPO_REAL_DIVERGENTE");
      }
      if (validated.largura() == null || validated.altura() == null
          || validated.largura() < 1 || validated.altura() < 1) {
        return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "DIMENSOES_INVALIDAS");
      }
      String checksum = validated.sha256();
      String key = properties.getPublicMediaPrefix()
          + "importacao/sha256/" + checksum.substring(0, 2) + "/"
          + checksum + "." + validated.extensao();
      synchronized (checksumLocks.computeIfAbsent(checksum, ignored -> new Object())) {
        if (storage.exists(StorageArea.PUBLIC_MEDIA, key)) {
          byte[] existing = storage.get(StorageArea.PUBLIC_MEDIA, key).content();
          if (!sha256(existing).equals(checksum)) {
            return Result.blocked(
                candidate.sourceAdId(), candidate.referenceHash(), key, checksum, content.length,
                validated.mimeType(), validated.largura(), validated.altura(),
                "CHECKSUM_DESTINO_DIVERGENTE");
          }
          return Result.preserved(
              candidate.sourceAdId(), candidate.referenceHash(), key, checksum, content.length,
              validated.mimeType(), validated.largura(), validated.altura());
        }
        return Result.planned(
            candidate.sourceAdId(), candidate.referenceHash(), key, checksum, content.length,
            validated.mimeType(), validated.largura(), validated.altura());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "OPERACAO_INTERROMPIDA");
    } catch (Exception exception) {
      return Result.quarantine(candidate.sourceAdId(), candidate.referenceHash(), "FALHA_INDIVIDUAL_SANITIZADA");
    }
  }

  private static List<Candidate> readCandidates(Path input) throws Exception {
    List<Candidate> candidates = new ArrayList<>();
    for (String line : Files.readAllLines(input, StandardCharsets.US_ASCII)) {
      if (line.isBlank()) continue;
      String[] fields = line.split("\\t", -1);
      if (fields.length != 5
          || !fields[0].matches("[1-9][0-9]*")
          || !fields[1].matches("[0-9a-f]{32}")
          || !("SAFE_PUBLIC".equals(fields[3]) || "ADULT_NON_EXPLICIT".equals(fields[3]))
          || !"PUBLIC_API_ANONYMOUS_NO_AGE_GATE".equals(fields[4])) {
        throw new IllegalArgumentException("Linha invalida no manifesto operacional");
      }
      String decoded = new String(Base64.getDecoder().decode(fields[2]), StandardCharsets.UTF_8);
      URI source = URI.create(decoded);
      if (!"https".equalsIgnoreCase(source.getScheme()) || source.getHost() == null) {
        throw new IllegalArgumentException("Origem publica deve usar HTTPS");
      }
      candidates.add(new Candidate(Long.parseLong(fields[0]), fields[1], source));
    }
    return candidates;
  }

  private static void writeResults(Path output, List<Result> results) throws Exception {
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) Files.createDirectories(parent);
    Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
    List<String> lines = results.stream().map(Result::toTsv).toList();
    Files.write(temporary, lines, StandardCharsets.UTF_8);
    try {
      Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
      Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static long count(List<Result> results, String status) {
    return results.stream().filter(result -> status.equals(result.status())).count();
  }

  private static String normalizeMime(String value) {
    String mime = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return "image/jpg".equals(mime) ? "image/jpeg" : mime;
  }

  private static String extensionFor(String mime) {
    return switch (mime) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      default -> null;
    };
  }

  private static boolean isGenericAsset(URI source) {
    String path = source.getPath() == null ? "" : source.getPath().toLowerCase(Locale.ROOT);
    return path.matches(".*(logo|placeholder|sem[-_]?foto|default|favicon|2151117281).*");
  }

  private static String md5(URI value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
        .digest(value.toASCIIString().getBytes(StandardCharsets.UTF_8)));
  }

  private static String sha256(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private static R2StorageProperties propertiesFromEnvironment() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint(required("R2_ENDPOINT"));
    properties.setRegion(environment("R2_REGION", "auto"));
    properties.setAccessKey(required("R2_ACCESS_KEY"));
    properties.setSigningValue(required("R2_SIGNING_VALUE"));
    properties.setPublicMediaBucket(required("R2_PUBLIC_MEDIA_BUCKET"));
    properties.setPrivateMediaBucket(required("R2_PRIVATE_MEDIA_BUCKET"));
    properties.setDocumentBucket(required("R2_DOCUMENT_BUCKET"));
    properties.setPublicMediaPrefix(environment(
        "R2_PUBLIC_MEDIA_PREFIX", "hml/midias-aprovadas/"));
    properties.setPrivateMediaPrefix(environment(
        "R2_PRIVATE_MEDIA_PREFIX", "hml/midias-pendentes/"));
    properties.setDocumentPrefix(environment("R2_DOCUMENT_PREFIX", "hml/documentos/"));
    properties.setPublicBaseUrl(System.getenv("R2_PUBLIC_BASE_URL"));
    properties.setSignedUrlTtlSeconds(300);
    return properties;
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Variavel obrigatoria ausente: " + name);
    }
    return value;
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private record Candidate(long sourceAdId, String referenceHash, URI sourceUrl) {
  }

  private record Result(
      long sourceAdId,
      String referenceHash,
      String objectKey,
      String checksum,
      long size,
      String mimeType,
      int width,
      int height,
      String status,
      String reason) {

    private static Result planned(
        long sourceAdId, String referenceHash, String key, String checksum, long size,
        String mime, int width, int height) {
      return new Result(sourceAdId, referenceHash, key, checksum, size, mime, width, height, "PLANEJADA", "");
    }

    private static Result preserved(
        long sourceAdId, String referenceHash, String key, String checksum, long size,
        String mime, int width, int height) {
      return new Result(sourceAdId, referenceHash, key, checksum, size, mime, width, height, "PRESERVADA", "");
    }

    private static Result quarantine(long sourceAdId, String referenceHash, String reason) {
      return new Result(sourceAdId, referenceHash, "", "", 0, "", 0, 0, "QUARENTENA", reason);
    }

    private static Result blocked(
        long sourceAdId, String referenceHash, String key, String checksum, long size,
        String mime, int width, int height, String reason) {
      return new Result(sourceAdId, referenceHash, key, checksum, size, mime, width, height, "BLOQUEADA", reason);
    }

    private String toTsv() {
      return String.join("\t",
          Long.toString(sourceAdId), referenceHash, objectKey, checksum, Long.toString(size),
          mimeType, Integer.toString(width), Integer.toString(height), status, reason);
    }
  }
}
