package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadProperties;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.net.URLDecoder;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "R2_KYC_DRY_RUN_ENABLED", matches = "true")
class R2PrivateKycDryRunIntegrationTest {

  private static final int CONCURRENCY = 4;
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
  private static final Set<String> HISTORICAL_STATES = Set.of("PENDENTE", "SEM_EVIDENCIA");

  @Test
  void validaOrigemPrivadaEMigraSomenteDocumentoComParteComprovada() throws Exception {
    SourceConfig sourceConfig = sourceConfig();
    R2StorageProperties destinationProperties = destinationProperties();
    destinationProperties.validateConfigured();
    assertThat(destinationProperties.getDocumentPrefix()).isEqualTo("hml/documentos/");

    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    R2Operations source = new R2SigV4Client(
        http,
        sourceConfig.endpoint(),
        sourceConfig.region(),
        sourceConfig.accessKey(),
        sourceConfig.signingValue());
    ObjectStorage destination = new R2ObjectStorage(
        destinationProperties,
        new R2SigV4Client(
            http,
            URI.create(destinationProperties.getEndpoint()),
            destinationProperties.getRegion(),
            destinationProperties.getAccessKey(),
            destinationProperties.getSigningValue()));
    DocumentoUploadValidator validator = new DocumentoUploadValidator(new DocumentoUploadProperties());

    List<Candidate> candidates = readCandidates(Path.of(required("R2_KYC_DRY_RUN_INPUT")));
    assertThat(candidates).extracting(Candidate::uniqueKey).doesNotHaveDuplicates();

    Map<String, Object> checksumLocks = new ConcurrentHashMap<>();
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
    List<Result> results = new ArrayList<>();
    try {
      List<Future<Result>> futures = candidates.stream()
          .map(candidate -> executor.submit(() -> migrate(
              candidate,
              sourceConfig,
              source,
              destinationProperties,
              destination,
              validator,
              http,
              checksumLocks)))
          .toList();
      for (Future<Result> future : futures) {
        results.add(future.get());
      }
    } finally {
      executor.shutdownNow();
    }

    results.sort(Comparator.comparing(Result::v3UserId).thenComparing(Result::referenceHash));
    writeResults(Path.of(required("R2_KYC_DRY_RUN_OUTPUT")), results);

    long migrated = count(results, "MIGRADA");
    long preserved = count(results, "PRESERVADA");
    long quarantined = count(results, "QUARENTENA");
    long blocked = count(results, "BLOQUEADA");
    assertThat(blocked).as("checksum divergente bloqueia o dry-run").isZero();
    assertThat(migrated + preserved).as("ao menos um PDF privado deve ser validado").isPositive();
    assertThat(results.stream()
        .filter(result -> Set.of("MIGRADA", "PRESERVADA").contains(result.status()))
        .allMatch(result -> "UNICO".equals(result.part())
            && "PENDENTE".equals(result.kycStatus())
            && "application/pdf".equals(result.mimeType())))
        .isTrue();
    assertTemporaryPrivateAccess(results, destination, http);

    System.out.printf(
        "R2_KYC_DRY_RUN total=%d migradas=%d preservadas=%d quarentena=%d bloqueadas=%d fingerprint=%s%n",
        results.size(), migrated, preserved, quarantined, blocked, fingerprint(results));
  }

  private Result migrate(
      Candidate candidate,
      SourceConfig sourceConfig,
      R2Operations source,
      R2StorageProperties destinationProperties,
      ObjectStorage destination,
      DocumentoUploadValidator validator,
      HttpClient http,
      Map<String, Object> checksumLocks) {
    try {
      if (!md5(candidate.sourceReference()).equals(candidate.referenceHash())) {
        return Result.quarantine(candidate, "REFERENCIA_HASH_INVALIDO");
      }
      ParsedReference parsed = parse(candidate, sourceConfig.bucket());
      if (isPubliclyReadable(http, sourceConfig.endpoint(), parsed)) {
        return Result.quarantine(candidate, "ORIGEM_PUBLICAMENTE_ACESSIVEL");
      }
      if (!source.exists(parsed.bucket(), parsed.key())) {
        return Result.quarantine(candidate, "ARQUIVO_INEXISTENTE");
      }
      String extension = extension(parsed.key());
      if (!Set.of("jpg", "jpeg", "png", "pdf").contains(extension)) {
        return Result.quarantine(candidate, "TIPO_OU_CONTEUDO_INVALIDO");
      }
      if (!"pdf".equals(extension)) {
        return Result.quarantine(candidate, "PARTE_DOCUMENTAL_NAO_COMPROVADA");
      }
      StoredObject sourceObject = source.get(parsed.bucket(), parsed.key());
      DocumentoUploadValidator.DocumentoValidado validated;
      try {
        validated = validator.validar(new MockMultipartFile(
            "arquivo",
            "origem." + extension,
            sourceObject.contentType(),
            sourceObject.content()));
      } catch (ResponseStatusException exception) {
        return Result.quarantine(candidate, "TIPO_OU_CONTEUDO_INVALIDO");
      }

      String checksum = validated.sha256();
      String key = destinationProperties.getDocumentPrefix()
          + "importacao/sha256/" + checksum.substring(0, 2) + "/"
          + checksum + "." + validated.extensao();
      synchronized (checksumLocks.computeIfAbsent(checksum, ignored -> new Object())) {
        if (destination.exists(StorageArea.PRIVATE_DOCUMENT, key)) {
          byte[] existing = destination.get(StorageArea.PRIVATE_DOCUMENT, key).content();
          if (!sha256(existing).equals(checksum)) {
            return Result.blocked(candidate, key, validated, "CHECKSUM_DESTINO_DIVERGENTE");
          }
          return Result.preserved(candidate, key, validated);
        }
        destination.put(StorageArea.PRIVATE_DOCUMENT, key, validated.bytes(), validated.mimeType());
        if (!destination.exists(StorageArea.PRIVATE_DOCUMENT, key)) {
          return Result.quarantine(candidate, "HEAD_DESTINO_FALHOU");
        }
        byte[] stored = destination.get(StorageArea.PRIVATE_DOCUMENT, key).content();
        if (!sha256(stored).equals(checksum)) {
          return Result.blocked(candidate, key, validated, "CHECKSUM_POS_GRAVACAO_DIVERGENTE");
        }
        assertThat(destination.publicUrl(StorageArea.PRIVATE_DOCUMENT, key)).isEmpty();
        return Result.migrated(candidate, key, validated);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return Result.quarantine(candidate, "OPERACAO_INTERROMPIDA");
    } catch (Exception exception) {
      return Result.quarantine(candidate, "FALHA_INDIVIDUAL_SANITIZADA");
    }
  }

  private static ParsedReference parse(Candidate candidate, String expectedBucket) {
    URI reference = URI.create(candidate.sourceReference());
    String bucket = reference.getHost();
    String key = URLDecoder.decode(reference.getRawPath().replaceFirst("^/", ""), StandardCharsets.UTF_8);
    if (!"r2".equalsIgnoreCase(reference.getScheme())
        || bucket == null
        || !bucket.equals(expectedBucket)
        || key.isBlank()
        || key.contains("..")
        || !key.startsWith("usuarios/" + candidate.sourceUserId() + "/documentos/")) {
      throw new IllegalArgumentException("Referencia privada fora do contrato");
    }
    return new ParsedReference(bucket, key);
  }

  private static boolean isPubliclyReadable(
      HttpClient http,
      URI endpoint,
      ParsedReference reference) throws Exception {
    URI unsigned = URI.create(endpoint.getScheme() + "://" + endpoint.getHost()
        + "/" + R2UrlCodec.encodeQueryValue(reference.bucket())
        + "/" + R2UrlCodec.encodePath(reference.key()));
    HttpResponse<Void> response = http.send(
        HttpRequest.newBuilder(unsigned)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.discarding());
    return response.statusCode() >= 200 && response.statusCode() < 300;
  }

  private static List<Candidate> readCandidates(Path input) throws Exception {
    List<Candidate> candidates = new ArrayList<>();
    for (String line : Files.readAllLines(input, StandardCharsets.US_ASCII)) {
      if (line.isBlank()) continue;
      String[] fields = line.split("\\t", -1);
      if (fields.length != 4
          || !fields[0].matches("[1-9][0-9]*")
          || !fields[1].matches("[0-9a-f]{32}")
          || !HISTORICAL_STATES.contains(fields[3])) {
        throw new IllegalArgumentException("Linha invalida no manifesto operacional KYC");
      }
      String decoded = new String(Base64.getDecoder().decode(fields[2]), StandardCharsets.UTF_8);
      candidates.add(new Candidate(Long.parseLong(fields[0]), fields[1], decoded, fields[3]));
    }
    return candidates;
  }

  private static void writeResults(Path output, List<Result> results) throws Exception {
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) Files.createDirectories(parent);
    Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
    Files.write(temporary, results.stream().map(Result::toTsv).toList(), StandardCharsets.US_ASCII);
    try {
      Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
      Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void assertTemporaryPrivateAccess(
      List<Result> results,
      ObjectStorage storage,
      HttpClient http) throws Exception {
    Result sample = results.stream()
        .filter(result -> Set.of("MIGRADA", "PRESERVADA").contains(result.status()))
        .findFirst()
        .orElseThrow();
    assertThat(storage.publicUrl(StorageArea.PRIVATE_DOCUMENT, sample.objectKey())).isEmpty();
    URI temporary = storage.temporaryGetUrl(
        StorageArea.PRIVATE_DOCUMENT,
        sample.objectKey(),
        Duration.ofMinutes(2));
    HttpResponse<byte[]> response = http.send(
        HttpRequest.newBuilder(temporary).timeout(REQUEST_TIMEOUT).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(sha256(response.body())).isEqualTo(sample.checksum());
  }

  private static String fingerprint(List<Result> results) throws Exception {
    String normalized = results.stream()
        .map(Result::fingerprintValue)
        .sorted()
        .reduce((left, right) -> left + "|" + right)
        .orElse("");
    return sha256(normalized.getBytes(StandardCharsets.US_ASCII));
  }

  private static long count(List<Result> results, String status) {
    return results.stream().filter(result -> status.equals(result.status())).count();
  }

  private static String extension(String key) {
    String lower = key.toLowerCase(Locale.ROOT);
    int dot = lower.lastIndexOf('.');
    return dot < 0 || dot == lower.length() - 1 ? "" : lower.substring(dot + 1);
  }

  private static String md5(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
        .digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String sha256(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private static UUID v3UserId(long sourceUserId) {
    try {
      String hex = md5("legacy:usuario:" + sourceUserId);
      return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
          + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao derivar ID V3", exception);
    }
  }

  private static SourceConfig sourceConfig() {
    return new SourceConfig(
        URI.create(required("R2_KYC_SOURCE_ENDPOINT")),
        environment("R2_KYC_SOURCE_REGION", "auto"),
        required("R2_KYC_SOURCE_ACCESS_KEY"),
        required("R2_KYC_SOURCE_SIGNING_VALUE"),
        required("R2_KYC_SOURCE_BUCKET"));
  }

  private static R2StorageProperties destinationProperties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint(required("R2_ENDPOINT"));
    properties.setRegion(environment("R2_REGION", "auto"));
    properties.setAccessKey(required("R2_ACCESS_KEY"));
    properties.setSigningValue(required("R2_SIGNING_VALUE"));
    properties.setPublicMediaBucket(required("R2_PUBLIC_MEDIA_BUCKET"));
    properties.setPrivateMediaBucket(required("R2_PRIVATE_MEDIA_BUCKET"));
    properties.setDocumentBucket(required("R2_DOCUMENT_BUCKET"));
    properties.setPublicMediaPrefix(environment("R2_PUBLIC_MEDIA_PREFIX", "hml/midias-aprovadas/"));
    properties.setPrivateMediaPrefix(environment("R2_PRIVATE_MEDIA_PREFIX", "hml/midias-pendentes/"));
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

  private record SourceConfig(
      URI endpoint,
      String region,
      String accessKey,
      String signingValue,
      String bucket) {
  }

  private record ParsedReference(String bucket, String key) {
  }

  private record Candidate(
      long sourceUserId,
      String referenceHash,
      String sourceReference,
      String historicalStatus) {

    private String uniqueKey() {
      return sourceUserId + ":" + referenceHash;
    }
  }

  private record Result(
      UUID v3UserId,
      String referenceHash,
      String objectKey,
      String checksum,
      long size,
      String mimeType,
      String extension,
      String part,
      String status,
      String reason,
      String kycStatus) {

    private static Result migrated(
        Candidate candidate,
        String key,
        DocumentoUploadValidator.DocumentoValidado validated) {
      return success(candidate, key, validated, "MIGRADA");
    }

    private static Result preserved(
        Candidate candidate,
        String key,
        DocumentoUploadValidator.DocumentoValidado validated) {
      return success(candidate, key, validated, "PRESERVADA");
    }

    private static Result success(
        Candidate candidate,
        String key,
        DocumentoUploadValidator.DocumentoValidado validated,
        String status) {
      return new Result(
          R2PrivateKycDryRunIntegrationTest.v3UserId(candidate.sourceUserId()),
          candidate.referenceHash(),
          key,
          validated.sha256(),
          validated.bytes().length,
          validated.mimeType(),
          validated.extensao(),
          "UNICO",
          status,
          "",
          "PENDENTE");
    }

    private static Result quarantine(Candidate candidate, String reason) {
      return new Result(
          R2PrivateKycDryRunIntegrationTest.v3UserId(candidate.sourceUserId()),
          candidate.referenceHash(),
          "",
          "",
          0,
          "",
          "",
          "NAO_DETERMINADA",
          "QUARENTENA",
          reason,
          "PENDENTE");
    }

    private static Result quarantine(
        Candidate candidate,
        DocumentoUploadValidator.DocumentoValidado validated,
        String reason) {
      return new Result(
          R2PrivateKycDryRunIntegrationTest.v3UserId(candidate.sourceUserId()),
          candidate.referenceHash(),
          "",
          validated.sha256(),
          validated.bytes().length,
          validated.mimeType(),
          validated.extensao(),
          "NAO_DETERMINADA",
          "QUARENTENA",
          reason,
          "PENDENTE");
    }

    private static Result blocked(
        Candidate candidate,
        String key,
        DocumentoUploadValidator.DocumentoValidado validated,
        String reason) {
      return new Result(
          R2PrivateKycDryRunIntegrationTest.v3UserId(candidate.sourceUserId()),
          candidate.referenceHash(),
          key,
          validated.sha256(),
          validated.bytes().length,
          validated.mimeType(),
          validated.extensao(),
          "UNICO",
          "BLOQUEADA",
          reason,
          "PENDENTE");
    }

    private String toTsv() {
      return String.join("\t",
          v3UserId.toString(),
          referenceHash,
          objectKey,
          checksum,
          Long.toString(size),
          mimeType,
          extension,
          part,
          status,
          reason,
          kycStatus);
    }

    private String fingerprintValue() {
      String normalizedStatus = Set.of("MIGRADA", "PRESERVADA").contains(status) ? "VALIDA" : status;
      return String.join(":",
          v3UserId.toString(), referenceHash, objectKey, checksum, Long.toString(size), mimeType,
          extension, part, normalizedStatus, reason, kycStatus);
    }
  }
}
