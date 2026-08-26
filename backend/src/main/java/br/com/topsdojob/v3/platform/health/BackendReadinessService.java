package br.com.topsdojob.v3.platform.health;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class BackendReadinessService {

    private static final int MAX_CONCURRENT_PROBES = 4;

    private final ApplicationReadinessState applicationState;
    private final DatabaseReadinessProbe databaseProbe;
    private final Duration timeout;
    private final ExecutorService executor;

    BackendReadinessService(
            ApplicationReadinessState applicationState,
            DatabaseReadinessProbe databaseProbe,
            @Value("${app.health.readiness-timeout-ms:1500}") long timeoutMillis) {
        this(applicationState, databaseProbe, Duration.ofMillis(timeoutMillis), readinessExecutor());
    }

    BackendReadinessService(
            ApplicationReadinessState applicationState,
            DatabaseReadinessProbe databaseProbe,
            Duration timeout,
            ExecutorService executor) {
        if (timeout.toMillis() < 100 || timeout.toMillis() > 5_000) {
            throw new IllegalArgumentException("readiness-timeout-ms deve estar entre 100 e 5000");
        }
        this.applicationState = applicationState;
        this.databaseProbe = databaseProbe;
        this.timeout = timeout;
        this.executor = executor;
    }

    ReadinessResult check() {
        if (!applicationState.isReady()) {
            return result(false, false, false);
        }

        Future<DatabaseReadinessProbe.DatabaseReadiness> future;
        try {
            future = executor.submit(databaseProbe::check);
        } catch (RuntimeException exception) {
            return result(true, false, false);
        }

        try {
            DatabaseReadinessProbe.DatabaseReadiness database =
                    future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return result(true, database.databaseReady(), database.migrationsReady());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return result(true, false, false);
        } catch (ExecutionException | TimeoutException exception) {
            future.cancel(true);
            return result(true, false, false);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ReadinessResult result(
            boolean applicationReady,
            boolean databaseReady,
            boolean migrationsReady) {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("application", status(applicationReady));
        components.put("database", status(databaseReady));
        components.put("migrations", status(migrationsReady));
        return new ReadinessResult(
                applicationReady && databaseReady && migrationsReady,
                Collections.unmodifiableMap(components));
    }

    private static String status(boolean ready) {
        return ready ? "UP" : "DOWN";
    }

    private static ExecutorService readinessExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "readiness-database-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                0,
                MAX_CONCURRENT_PROBES,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_CONCURRENT_PROBES),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    record ReadinessResult(boolean ready, Map<String, String> components) {
    }
}
