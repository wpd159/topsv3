package br.com.topsdojob.v3.application.publico.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PublicAuthRateLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicAuthRateLimiter.class);
    private static final long CLEANUP_INTERVAL = 256;

    private final ConcurrentHashMap<String, AttemptBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final Clock clock;

    public PublicAuthRateLimiter() {
        this(Clock.systemUTC());
    }

    PublicAuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void require(String operation, String clientKey, int limit, Duration window) {
        validate(limit, window);
        String key = bucketKey(operation, clientKey);
        Instant now = clock.instant();
        AttemptBucket bucket = buckets.computeIfAbsent(key, ignored -> new AttemptBucket());
        synchronized (bucket) {
            prune(bucket, now, window);
            if (bucket.attempts.size() >= limit) {
                throw rateLimited(operation, key, bucket, now, window);
            }
            bucket.attempts.addLast(now);
            bucket.expiresAt = now.plus(window);
        }
        cleanupExpired(now);
    }

    public void check(String operation, String clientKey, int limit, Duration window) {
        validate(limit, window);
        String key = bucketKey(operation, clientKey);
        Instant now = clock.instant();
        AttemptBucket bucket = buckets.get(key);
        if (bucket == null) {
            cleanupExpired(now);
            return;
        }
        synchronized (bucket) {
            prune(bucket, now, window);
            if (bucket.attempts.size() >= limit) {
                throw rateLimited(operation, key, bucket, now, window);
            }
            if (bucket.attempts.isEmpty()) {
                buckets.remove(key, bucket);
            }
        }
        cleanupExpired(now);
    }

    public void recordFailure(String operation, String clientKey, int limit, Duration window) {
        validate(limit, window);
        String key = bucketKey(operation, clientKey);
        Instant now = clock.instant();
        AttemptBucket bucket = buckets.computeIfAbsent(key, ignored -> new AttemptBucket());
        synchronized (bucket) {
            prune(bucket, now, window);
            bucket.attempts.addLast(now);
            bucket.expiresAt = now.plus(window);
            if (bucket.attempts.size() >= limit) {
                throw rateLimited(operation, key, bucket, now, window);
            }
        }
        cleanupExpired(now);
    }

    public void reset(String operation, String clientKey) {
        buckets.remove(bucketKey(operation, clientKey));
    }

    private PublicAuthException rateLimited(
            String operation,
            String key,
            AttemptBucket bucket,
            Instant now,
            Duration window) {
        Instant availableAt = bucket.attempts.peekFirst().plus(window);
        long millis = Math.max(1, Duration.between(now, availableAt).toMillis());
        long retryAfterSeconds = Math.max(1, (millis + 999) / 1000);
        LOGGER.warn("public_auth_rate_limited operation={} key_ref={} retry_after_seconds={}",
                sanitizeOperation(operation), key.substring(key.indexOf(':') + 1), retryAfterSeconds);
        return new PublicAuthException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Aguarde e tente novamente.",
                retryAfterSeconds);
    }

    private void prune(AttemptBucket bucket, Instant now, Duration window) {
        while (!bucket.attempts.isEmpty() && !bucket.attempts.peekFirst().plus(window).isAfter(now)) {
            bucket.attempts.removeFirst();
        }
    }

    private void cleanupExpired(Instant now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        buckets.forEach((key, bucket) -> {
            Instant expiresAt = bucket.expiresAt;
            if (expiresAt != null && !expiresAt.isAfter(now)) {
                buckets.remove(key, bucket);
            }
        });
    }

    private String bucketKey(String operation, String clientKey) {
        String safeOperation = sanitizeOperation(operation);
        String material = safeOperation + ':' + (clientKey == null ? "unknown" : clientKey);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return safeOperation + ':' + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String sanitizeOperation(String operation) {
        if (operation == null || !operation.matches("[a-z0-9-]{1,40}")) {
            return "unknown";
        }
        return operation;
    }

    private void validate(int limit, Duration window) {
        if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Configuracao de rate limit invalida");
        }
    }

    private static final class AttemptBucket {
        private final Deque<Instant> attempts = new ArrayDeque<>();
        private volatile Instant expiresAt;
    }
}
