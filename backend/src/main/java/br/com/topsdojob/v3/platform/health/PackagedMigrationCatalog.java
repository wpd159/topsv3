package br.com.topsdojob.v3.platform.health;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
final class PackagedMigrationCatalog {

    private static final String MIGRATIONS_PATTERN = "classpath*:db/migration/V*__*.sql";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private final int minimumCompatibleVersion;

    PackagedMigrationCatalog() {
        this(new PathMatchingResourcePatternResolver());
    }

    PackagedMigrationCatalog(ResourcePatternResolver resolver) {
        this.minimumCompatibleVersion = discoverMinimumCompatibleVersion(resolver);
    }

    int minimumCompatibleVersion() {
        return minimumCompatibleVersion;
    }

    private static int discoverMinimumCompatibleVersion(ResourcePatternResolver resolver) {
        Map<Integer, String> migrations = new HashMap<>();
        try {
            for (Resource resource : resolver.getResources(MIGRATIONS_PATTERN)) {
                String filename = resource.getFilename();
                Matcher matcher = filename == null ? null : VERSION_PATTERN.matcher(filename);
                if (matcher == null || !matcher.matches()) {
                    continue;
                }
                int version = Integer.parseInt(matcher.group(1));
                String previous = migrations.putIfAbsent(version, filename);
                if (previous != null) {
                    throw new IllegalStateException("versao de migration duplicada no artefato");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("nao foi possivel ler as migrations empacotadas", exception);
        }

        return migrations.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow(() -> new IllegalStateException("nenhuma migration versionada foi empacotada"));
    }
}
