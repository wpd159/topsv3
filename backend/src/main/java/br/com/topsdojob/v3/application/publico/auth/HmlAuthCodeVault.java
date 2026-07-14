package br.com.topsdojob.v3.application.publico.auth;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test", "homologacao"})
@ConditionalOnProperty(prefix = "app.auth", name = "hml-code-vault-enabled", havingValue = "true")
public class HmlAuthCodeVault {
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void put(UUID outboxId, String code, OffsetDateTime expiresAt) {
        entries.put(outboxId, new Entry(code, expiresAt));
    }

    public String take(UUID outboxId) {
        Entry entry = entries.remove(outboxId);
        if (entry == null || entry.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw unavailable();
        }
        return entry.code();
    }

    private org.springframework.web.server.ResponseStatusException unavailable() {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Codigo operacional indisponivel.");
    }

    private record Entry(String code, OffsetDateTime expiresAt) {}
}
