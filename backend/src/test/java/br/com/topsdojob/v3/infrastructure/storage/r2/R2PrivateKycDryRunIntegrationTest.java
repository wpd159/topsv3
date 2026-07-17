package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "R2_KYC_DRY_RUN_ENABLED", matches = "true")
class R2PrivateKycDryRunIntegrationTest {

  private static final int CONCURRENCY = 4;
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
  private static final Set<String> HISTORICAL_STATES = Set.of(
      "NAO_INICIADO", "PENDENTE", "APROVADO", "REPROVADO");
  private static final Set<String> PARTS = Set.of(
      "UNICO", "FRENTE", "VERSO", "NAO_DETERMINADA");

  @Test
  void migraCachePrivadoSanitizadoSemCredencialDaOrigem() throws Exception {
    R2StorageProperties destinationProperties = destinationProperties();
    destinationProperties.validateConfigured();
    assertThat(destinationProperties.getDocumentPrefix()).isEqualTo("hml/documentos/");

    String scope = required("R2_KYC_DESTINATION_SCOPE");
    if (!scope.matches("[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*")) {
      throw new IllegalArgumentException("Escopo de destino KYC invalido");
    }
    String destinationRoot = destinationProperties.getDocumentPrefix()
        + "importacao/" + scope + "/sha256/";
    Path cacheDirectory = Path.of(required("R2_KYC_DRY_RUN_CACHE_DIR")).toAbsolutePath().normalize();

    HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    ObjectStorage destination = new R2ObjectStorage(
        destinationProperties,
        new R2SigV4Client(
            http,
            URI.create(destinationProperties.getEndpoint()),
            destinationProperties.getRegion(),
            destinationProperties.getAccessKey(),
            destinationProperties.getSigningValue()));
    LegacyKycDocumentValidator validator = new LegacyKycDocumentValidator();

    List<Candidate> candidates = readCandidates(Path.of(required("R2_KYC_DRY_RUN_INPUT")));
    assertThat(candidates).extracting(Candidate::uniqueKey).doesNotHaveDuplicates();

    Map<String, Object> checksumLocks = new ConcurrentHashMap<>();
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
    List<Result> results = new ArrayList<>();
    try {
      List<Future<Result>> futures = candidates.stream()
          .map(candidate -> executor.submit(() -> migrate(
              candidate,
              cacheDirectory,
              destinationRoot,
              destination,
              validator,
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
    long consolidated = count(results, "CONSOLIDADA");
    long quarantined = count(results, "QUARENTENA");
    long blocked = count(results, "BLOQUEADA");
    assertThat(blocked).as("checksum divergente bloqueia o dry-run").isZero();
    assertThat(migrated + preserved).as("ao menos um documento privado deve ser validado").isPositive();
    assertThat(results.stream()
        .filter(Result::successful)
        .allMatch(result -> Set.of("UNICO", "FRENTE", "VERSO").contains(result.part())
            && Set.of("PENDENTE", "VALIDADO", "REJEITADO").contains(result.kycStatus())
            && Set.of("application/pdf", "image/jpeg", "image/png").contains(result.mimeType())))
        .isTrue();
    assertNoCrossUserChecksum(results);
    assertConsolidatedReferencesResolve(results);
    assertTemporaryPrivateAccess(results, destination, http);

    System.out.printf(
        "R2_KYC_DRY_RUN total=%d migradas=%d preservadas=%d consolidadas=%d "
            + "quarentena=%d bloqueadas=%d fingerprint=%s%n",
        results.size(), migrated, preserved, consolidated, quarantined, blocked, fingerprint(results));
  }

  private Result migrate(
      Candidate candidate,
      Path cacheDirectory,
      String destinationRoot,
      ObjectStorage destination,
      LegacyKycDocumentValidator validator,
      Map<String, Object> checksumLocks) {
    try {
      if ("DUPLICATA_MESMO_USUARIO".equals(candidate.classification())) {
        return Result.consolidated(candidate);
      }
      if (!"ELIGIVEL".equals(candidate.classification())) {
        return Result.quarantine(candidate, candidate.classification());
      }

      Path source = cacheDirectory.resolve(candidate.cacheId() + ".bin").normalize();
      if (!source.startsWith(cacheDirectory) || !Files.isRegularFile(source)) {
        return Result.quarantine(candidate, "ARQUIVO_INEXISTENTE");
      }
      byte[] bytes = Files.readAllBytes(source);
      if (!sha256(bytes).equals(candidate.cacheId())) {
        return Result.blocked(candidate, "", null, "CHECKSUM_CACHE_DIVERGENTE");
      }

      DocumentoUploadValidator.DocumentoValidado validated;
      try {
        validated = validator.validar(bytes, candidate.extension());
      } catch (ResponseStatusException exception) {
        return Result.quarantine(candidate, "TIPO_OU_CONTEUDO_INVALIDO");
      }
      if (!validated.sha256().equals(candidate.cacheId())) {
        return Result.blocked(candidate, "", validated, "CHECKSUM_VALIDACAO_DIVERGENTE");
      }

      String checksum = validated.sha256();
      String key = destinationRoot + checksum.substring(0, 2) + "/"
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

  private static List<Candidate> readCandidates(Path input) throws Exception {
    List<Candidate> candidates = new ArrayList<>();
    for (String line : Files.readAllLines(input, StandardCharsets.US_ASCII)) {
      if (line.isBlank()) continue;
      String[] fields = line.split("\\t", -1);
      if (fields.length != 9
          || !fields[0].matches("[1-9][0-9]*")
          || !fields[1].matches("[0-9a-f]{32}")
          || !fields[2].matches("[0-9a-f]{32}")
          || (!fields[3].isEmpty() && !fields[3].matches("[0-9a-f]{64}"))
          || (!fields[4].isEmpty() && !Set.of("jpg", "png", "pdf").contains(fields[4]))
          || !HISTORICAL_STATES.contains(fields[5])
          || !PARTS.contains(fields[6])
          || (!fields[7].isEmpty() && !fields[7].matches("[0-9a-f]{64}"))
          || !fields[8].matches("[A-Z][A-Z0-9_]{2,80}")) {
        throw new IllegalArgumentException("Linha invalida no manifesto operacional KYC");
      }
      candidates.add(new Candidate(
          Long.parseLong(fields[0]), fields[1], fields[2], fields[3], fields[4],
          fields[5], fields[6], fields[7], fields[8]));
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

  private static void assertNoCrossUserChecksum(List<Result> results) {
    Map<String, Set<UUID>> owners = results.stream()
        .filter(Result::successful)
        .collect(Collectors.groupingBy(
            Result::checksum,
            Collectors.mapping(Result::v3UserId, Collectors.toSet())));
    assertThat(owners.values()).allMatch(ownerIds -> ownerIds.size() == 1);
  }

  private static void assertConsolidatedReferencesResolve(List<Result> results) {
    Map<String, Result> byOwnerAndReference = results.stream()
        .collect(Collectors.toMap(
            result -> result.v3UserId() + ":" + result.referenceHash(),
            result -> result));
    assertThat(results.stream()
        .filter(result -> "CONSOLIDADA".equals(result.status()))
        .allMatch(result -> {
          Result canonical = byOwnerAndReference.get(
              result.v3UserId() + ":" + result.canonicalReferenceHash());
          return canonical != null && canonical.successful();
        }))
        .isTrue();
  }

  private static void assertTemporaryPrivateAccess(
      List<Result> results,
      ObjectStorage storage,
      HttpClient http) throws Exception {
    Result sample = results.stream().filter(Result::successful).findFirst().orElseThrow();
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

  private static String sha256(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private static UUID v3UserId(long sourceUserId) {
    try {
      String hex = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
          .digest(("legacy:usuario:" + sourceUserId).getBytes(StandardCharsets.UTF_8)));
      return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
          + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao derivar ID V3", exception);
    }
  }

  private static String kycStatus(String historicalStatus) {
    return switch (historicalStatus) {
      case "APROVADO" -> "VALIDADO";
      case "REPROVADO" -> "REJEITADO";
      case "NAO_INICIADO", "PENDENTE" -> "PENDENTE";
      default -> throw new IllegalArgumentException("Estado historico KYC invalido");
    };
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

  private record Candidate(
      long sourceUserId,
      String referenceHash,
      String canonicalReferenceHash,
      String cacheId,
      String extension,
      String historicalStatus,
      String part,
      String submissionHash,
      String classification) {

    private String uniqueKey() {
      return sourceUserId + ":" + referenceHash;
    }
  }

  private record Result(
      UUID v3UserId,
      String referenceHash,
      String canonicalReferenceHash,
      String objectKey,
      String checksum,
      long size,
      String mimeType,
      String extension,
      String part,
      String submissionHash,
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
          candidate.canonicalReferenceHash(),
          key,
          validated.sha256(),
          validated.bytes().length,
          validated.mimeType(),
          validated.extensao(),
          candidate.part(),
          candidate.submissionHash(),
          status,
          "",
          R2PrivateKycDryRunIntegrationTest.kycStatus(candidate.historicalStatus()));
    }

    private static Result consolidated(Candidate candidate) {
      return empty(candidate, "CONSOLIDADA", "DUPLICATA_MESMO_USUARIO");
    }

    private static Result quarantine(Candidate candidate, String reason) {
      return empty(candidate, "QUARENTENA", reason);
    }

    private static Result blocked(
        Candidate candidate,
        String key,
        DocumentoUploadValidator.DocumentoValidado validated,
        String reason) {
      return new Result(
          R2PrivateKycDryRunIntegrationTest.v3UserId(candidate.sourceUserId()),
          candidate.referenceHash(),
          candidate.canonicalReferenceHash(),
          key,
          validated == null ? "" : validated.sha256(),
          validated == null ? 0 : validated.bytes().length,
          validated == null ? "" : validated.mimeType(),
          validated == null ? "" : validated.extensao(),
          candidate.part(),
          candidate.submissionHash(),
          "BLOQUEADA",
          reason,
          R2PrivateKycDryRunIntegrationTest.kycStatus(candidate.historicalStatus()));
    }

    private static Result empty(Candidate candidate, String status, String reason) {
      return new Result(
          R2PrivateKycDryRunIntegrationTest.v3UserId(candidate.sourceUserId()),
          candidate.referenceHash(),
          candidate.canonicalReferenceHash(),
          "", "", 0, "", "",
          candidate.part(),
          candidate.submissionHash(),
          status,
          reason,
          R2PrivateKycDryRunIntegrationTest.kycStatus(candidate.historicalStatus()));
    }

    private boolean successful() {
      return Set.of("MIGRADA", "PRESERVADA").contains(status);
    }

    private String toTsv() {
      return String.join("\t",
          v3UserId.toString(),
          referenceHash,
          canonicalReferenceHash,
          objectKey,
          checksum,
          Long.toString(size),
          mimeType,
          extension,
          part,
          submissionHash,
          status,
          reason,
          kycStatus);
    }

    private String fingerprintValue() {
      String normalizedStatus = successful() ? "VALIDA" : status;
      return String.join(":",
          v3UserId.toString(), referenceHash, canonicalReferenceHash, checksum,
          Long.toString(size), mimeType, extension, part, submissionHash,
          normalizedStatus, reason, kycStatus);
    }
  }
}
