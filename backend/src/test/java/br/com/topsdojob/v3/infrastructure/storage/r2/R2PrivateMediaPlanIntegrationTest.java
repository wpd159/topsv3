package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Candidate;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.MediaType;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Result;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Status;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Variant;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
    named = "R2_PRIVATE_MEDIA_PLAN_ENABLED",
    matches = "true")
class R2PrivateMediaPlanIntegrationTest {

  @Test
  void planejaManifestoDuasVezesSemEscreverObjetos() throws Exception {
    ObjectStorage source = storage(sourceProperties());
    R2StorageProperties destinationProperties = destinationProperties();
    destinationProperties.validateConfigured();
    ObjectStorage destination = storage(destinationProperties);
    PrivateMediaObjectTransport transport = new PrivateMediaObjectTransport();
    List<Candidate> candidates = readCandidates(
        Path.of(required("R2_PRIVATE_MEDIA_PLAN_INPUT")),
        destinationProperties.getPrivateMediaPrefix());

    List<Result> first = transport.normalizePrincipals(candidates.stream()
        .map(candidate -> transport.plan(source, destination, candidate))
        .toList());
    List<Result> second = transport.normalizePrincipals(candidates.stream()
        .map(candidate -> transport.plan(source, destination, candidate))
        .toList());

    writeManifest(
        Path.of(required("R2_PRIVATE_MEDIA_PLAN_OUTPUT")),
        second);
    assertThat(first).isEqualTo(second);
    assertThat(second)
        .extracting(Result::status)
        .doesNotContain(Status.MIGRADA, Status.BLOQUEADA);
    assertThat(second.stream()
        .filter(result -> result.status() == Status.PLANEJADA
            || result.status() == Status.PRESERVADA)
        .map(Result::destinationKey)
        .toList())
        .containsExactlyElementsOf(first.stream()
            .filter(result -> result.status() == Status.PLANEJADA
                || result.status() == Status.PRESERVADA)
            .map(Result::destinationKey)
            .toList());
    assertThat(second.stream()
        .filter(result -> result.status() == Status.PLANEJADA
            || result.status() == Status.PRESERVADA)
        .map(Result::checksum)
        .toList())
        .containsExactlyElementsOf(first.stream()
            .filter(result -> result.status() == Status.PLANEJADA
                || result.status() == Status.PRESERVADA)
            .map(Result::checksum)
            .toList());
    assertEveryLogicalMediaIsRecoverableOrFormallyQuarantined(second);
  }

  private void assertEveryLogicalMediaIsRecoverableOrFormallyQuarantined(
      List<Result> results) {
    var groups = results.stream()
        .collect(java.util.stream.Collectors.groupingBy(
            result -> result.sourceAdId() + ":" + result.logicalMediaHash()));
    long recoverable = groups.values().stream()
        .filter(group -> group.stream().anyMatch(result -> result.primary()
            && (result.status() == Status.PLANEJADA
                || result.status() == Status.PRESERVADA)))
        .count();
    long quarantined = groups.values().stream()
        .filter(group -> group.stream().allMatch(result ->
            !result.primary()
                && result.status() == Status.QUARENTENA
                && "OBJETO_ORIGEM_AUSENTE".equals(result.reason())))
        .count();

    assertThat(groups.values()).allSatisfy(group -> {
      var principal = group.stream()
          .filter(result -> result.primary()
              && (result.status() == Status.PLANEJADA
                  || result.status() == Status.PRESERVADA))
          .toList();
      if (principal.isEmpty()) {
        assertThat(group).allSatisfy(result -> {
          assertThat(result.primary()).isFalse();
          assertThat(result.status()).isEqualTo(Status.QUARENTENA);
          assertThat(result.reason()).isEqualTo("OBJETO_ORIGEM_AUSENTE");
          assertThat(result.destinationKey()).isNull();
        });
      } else {
        assertThat(principal).hasSize(1);
      }
    });
    assertThat(recoverable + quarantined).isEqualTo(groups.size());
  }

  private ObjectStorage storage(R2StorageProperties properties) {
    HttpClient http = HttpClient.newBuilder().build();
    return new ReadOnlyObjectStorage(new R2ObjectStorage(
        properties,
        new R2SigV4Client(
            http,
            URI.create(properties.getEndpoint()),
            properties.getRegion(),
            properties.getAccessKey(),
            properties.getSigningValue())));
  }

  private List<Candidate> readCandidates(Path input, String destinationPrefix) throws Exception {
    List<Candidate> candidates = new ArrayList<>();
    for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      String[] fields = line.split("\t", -1);
      if (fields.length != 11) {
        throw new IllegalArgumentException("linha invalida no inventario privado de midias");
      }
      candidates.add(new Candidate(
          Long.parseLong(fields[0]),
          fields[1],
          fields[2],
          fields[3],
          MediaType.valueOf(fields[4]),
          Variant.valueOf(fields[5]),
          Boolean.parseBoolean(fields[6]),
          fields[7],
          StorageArea.valueOf(fields[8]),
          fields[9],
          Integer.parseInt(fields[10]),
          destinationPrefix));
    }
    candidates.sort(Comparator
        .comparingLong(Candidate::sourceAdId)
        .thenComparing(Candidate::logicalMediaHash)
        .thenComparing(candidate -> candidate.variant().name()));
    assertThat(candidates)
        .extracting(candidate -> candidate.sourceTable() + ":" + candidate.sourceId()
            + ":" + candidate.variant())
        .doesNotHaveDuplicates();
    return List.copyOf(candidates);
  }

  private void writeManifest(Path output, List<Result> results) throws Exception {
    List<String> lines = results.stream()
        .sorted(Comparator
            .comparingLong(Result::sourceAdId)
            .thenComparing(Result::logicalMediaHash)
            .thenComparing(result -> result.variant().name()))
        .map(this::manifestLine)
        .toList();
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
    Files.write(temporary, lines, StandardCharsets.UTF_8);
    try {
      Files.move(
          temporary,
          output,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
      Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String manifestLine(Result result) {
    return String.join("\t",
        Long.toString(result.sourceAdId()),
        result.logicalMediaHash(),
        result.sourceTable(),
        result.sourceId(),
        result.type().name(),
        result.variant().name(),
        Boolean.toString(result.primary()),
        result.referenceHash(),
        value(result.destinationKey()),
        value(result.checksum()),
        Long.toString(result.size()),
        value(result.contentType()),
        value(result.width()),
        value(result.height()),
        "",
        Integer.toString(result.order()),
        result.status().name(),
        value(result.reason()));
  }

  private String value(Object value) {
    return value == null ? "" : value.toString();
  }

  private R2StorageProperties sourceProperties() {
    R2StorageProperties properties = commonProperties("R2_IMPORT_SOURCE_");
    properties.setPublicMediaBucket(required("R2_IMPORT_SOURCE_PUBLIC_BUCKET"));
    properties.setPrivateMediaBucket(required("R2_IMPORT_SOURCE_PRIVATE_BUCKET"));
    properties.setDocumentBucket(required("R2_IMPORT_SOURCE_DOCUMENT_BUCKET"));
    properties.setPublicMediaPrefix(environment("R2_IMPORT_SOURCE_PUBLIC_PREFIX", ""));
    properties.setPrivateMediaPrefix(environment("R2_IMPORT_SOURCE_PRIVATE_PREFIX", ""));
    properties.setDocumentPrefix(environment("R2_IMPORT_SOURCE_DOCUMENT_PREFIX", ""));
    return properties;
  }

  private R2StorageProperties destinationProperties() {
    R2StorageProperties properties = commonProperties("R2_");
    properties.setPublicMediaBucket(required("R2_PUBLIC_MEDIA_BUCKET"));
    properties.setPrivateMediaBucket(required("R2_PRIVATE_MEDIA_BUCKET"));
    properties.setDocumentBucket(required("R2_DOCUMENT_BUCKET"));
    properties.setPublicMediaPrefix(required("R2_PUBLIC_MEDIA_PREFIX"));
    properties.setPrivateMediaPrefix(required("R2_PRIVATE_MEDIA_PREFIX"));
    properties.setDocumentPrefix(required("R2_DOCUMENT_PREFIX"));
    properties.setPublicBaseUrl(System.getenv("R2_PUBLIC_BASE_URL"));
    return properties;
  }

  private R2StorageProperties commonProperties(String prefix) {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint(required(prefix + "ENDPOINT"));
    properties.setRegion(environment(prefix + "REGION", "auto"));
    properties.setAccessKey(required(prefix + "ACCESS_KEY"));
    properties.setSigningValue(required(prefix + "SIGNING_VALUE"));
    properties.setSignedUrlTtlSeconds(300);
    return properties;
  }

  private String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("variavel obrigatoria ausente: " + name);
    }
    return value;
  }

  private String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null ? fallback : value.trim();
  }
}
