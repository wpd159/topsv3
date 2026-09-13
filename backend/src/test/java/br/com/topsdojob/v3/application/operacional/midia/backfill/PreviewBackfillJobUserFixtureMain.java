package br.com.topsdojob.v3.application.operacional.midia.backfill;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Child JVM only: production bootstrap/runner with the owned fixture's read-only inventory. */
public final class PreviewBackfillJobUserFixtureMain {
  private PreviewBackfillJobUserFixtureMain() { }

  public static void main(String[] args) throws Exception {
    Path directory = Path.of(System.getenv("PREVIEW_JOB_FIXTURE_DATA"));
    Properties loaded = new Properties();
    try (var input = Files.newInputStream(directory.resolve("application.properties"))) {
      loaded.load(input);
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    loaded.forEach((key, value) -> properties.put(key.toString(), value));
    var keys = Files.readAllLines(directory.resolve("inventory.keys"));
    ObjectStorageInventory inventory = (area, prefix, token, maxKeys) -> {
      if (area != StorageArea.PUBLIC_MEDIA || token != null || maxKeys != 1_000
          || keys.stream().anyMatch(key -> !key.startsWith(prefix))) {
        throw new IllegalStateException("unexpected synthetic inventory query");
      }
      return new StoredObjectPage(keys.stream().map(key -> new StoredObjectMetadata(
          key, 123L, "synthetic-preview", Instant.parse("2026-09-01T12:00:00Z"))).toList(), null, false);
    };
    Files.readAllLines(Path.of("/proc/self/status")).stream()
        .filter(line -> line.startsWith("Uid:") || line.startsWith("Gid:") || line.startsWith("CapEff:"))
        .forEach(line -> System.out.println("PREVIEW_JOB_FIXTURE_PROCESS " + line));
    System.out.println("PREVIEW_JOB_FIXTURE_CONFIGURATION_READ=PASS");
    var application = RestrictedMediaPreviewBackfillBootstrap.application();
    application.addInitializers(context -> {
      // Ambient environment cannot redirect the owned database, storage or email settings.
      context.getEnvironment().getPropertySources().addFirst(
          new MapPropertySource("preview-job-owned-synthetic", properties));
      ((GenericApplicationContext) context).registerBean(ObjectStorageInventory.class, () -> inventory);
    });
    application.run(args); // The production runner closes its own minimal Spring context.
  }
}
