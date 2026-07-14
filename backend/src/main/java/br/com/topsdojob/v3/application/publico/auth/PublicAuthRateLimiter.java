package br.com.topsdojob.v3.application.publico.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PublicAuthRateLimiter {
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public void require(String operation, String clientKey, int limit, Duration window) {
        String key = operation + ':' + Integer.toHexString(clientKey == null ? 0 : clientKey.hashCode());
        Instant now = Instant.now();
        Deque<Instant> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().plus(window).isBefore(now)) bucket.removeFirst();
            if (bucket.size() >= limit) {
                throw new PublicAuthException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas. Aguarde e tente novamente.");
            }
            bucket.addLast(now);
        }
    }
}
