package br.com.topsdojob.v3.platform.health;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaCoordenador;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class BackendReadinessService {

    private static final long MAX_EFFECTIVE_TIMEOUT_MILLIS = 1_000;

    private final ApplicationReadinessState applicationState;
    private final DatabaseReadinessProbe databaseProbe;
    private final BooleanSupplier localityExpired;
    private final long configuredTimeoutMillis;
    private final long effectiveTimeoutMillis;
    private final ExecutorService executor;
    private final Object monitor = new Object();
    private ProbeWork active;
    private volatile boolean closed;

    @Autowired
    BackendReadinessService(
            ApplicationReadinessState applicationState,
            DatabaseReadinessProbe databaseProbe,
            LocalidadesConsultaCoordenador localidades,
            @Value("${app.health.readiness-timeout-ms:1500}") long timeoutMillis) {
        this(applicationState, databaseProbe, localidades::possuiExecucaoExpirada,
                Duration.ofMillis(timeoutMillis), readinessExecutor());
    }

    private BackendReadinessService(
            ApplicationReadinessState applicationState,
            DatabaseReadinessProbe databaseProbe,
            BooleanSupplier localityExpired,
            Duration timeout,
            ExecutorService executor) {
        if (timeout.toMillis() < 100 || timeout.toMillis() > 5_000) {
            throw new IllegalArgumentException("readiness-timeout-ms deve estar entre 100 e 5000");
        }
        this.applicationState = applicationState;
        this.databaseProbe = databaseProbe;
        this.localityExpired = localityExpired;
        this.configuredTimeoutMillis = timeout.toMillis();
        // Main/Compose may still inject 1500 ms. Cap this backend decision without
        // changing deployment configuration or the frontend's timeout.
        this.effectiveTimeoutMillis = Math.min(configuredTimeoutMillis, MAX_EFFECTIVE_TIMEOUT_MILLIS);
        this.executor = executor;
    }

    static BackendReadinessService forTesting(
            ApplicationReadinessState applicationState,
            DatabaseReadinessProbe databaseProbe,
            Duration timeout,
            ExecutorService executor) {
        return forTesting(applicationState, databaseProbe, () -> false, timeout, executor);
    }

    static BackendReadinessService forTesting(
            ApplicationReadinessState applicationState,
            DatabaseReadinessProbe databaseProbe,
            BooleanSupplier localityExpired,
            Duration timeout,
            ExecutorService executor) {
        return new BackendReadinessService(applicationState, databaseProbe, localityExpired, timeout, executor);
    }

    long configuredTimeoutMillis() {
        return configuredTimeoutMillis;
    }

    long effectiveTimeoutMillis() {
        return effectiveTimeoutMillis;
    }

    ReadinessResult check() {
        if (!applicationState.isReady()) {
            return result(false, false, false, false);
        }
        boolean localityInitiallyReady = !localityExpired.getAsBoolean();
        if (!localityInitiallyReady) {
            // No database probe was performed: do not report a fictitious failure
            // (or success) to explain the independent coordinator signal.
            return new ReadinessResult(false, Map.of(
                    "application", status(applicationState.isReady() && !closed),
                    "database", "UNKNOWN", "migrations", "UNKNOWN"));
        }
        ProbeWork work;
        synchronized (monitor) {
            if (closed) {
                return result(false, false, false, false);
            }
            work = active;
            if (work == null) {
                work = new ProbeWork(System.nanoTime()
                        + TimeUnit.MILLISECONDS.toNanos(effectiveTimeoutMillis));
                active = work;
                ProbeWork submitted = work;
                try {
                    executor.execute(() -> runProbe(submitted));
                } catch (RuntimeException exception) {
                    active = null;
                    return result(applicationState.isReady(), false, false, false);
                }
            }
        }

        try {
            long remaining = work.deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                work.requestCancellation();
                return result(applicationState.isReady(), false, false, false);
            }
            DatabaseReadinessProbe.DatabaseReadiness database =
                    work.result.get(remaining, TimeUnit.NANOSECONDS);
            // Include cleanup and scheduling in the original producer deadline,
            // never a new consumer deadline.
            if (System.nanoTime() - work.deadlineNanos >= 0 || work.cancelled) {
                return result(applicationState.isReady(), false, false, false);
            }
            boolean localityFinallyReady = !localityExpired.getAsBoolean();
            boolean applicationReady = applicationState.isReady() && !closed;
            if (System.nanoTime() - work.deadlineNanos >= 0 || work.cancelled) {
                return result(applicationReady, false, false, false);
            }
            return result(applicationReady, database.databaseReady(), database.migrationsReady(),
                    localityInitiallyReady && localityFinallyReady);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            work.requestCancellation();
            return result(applicationState.isReady(), false, false, false);
        } catch (ExecutionException | TimeoutException exception) {
            work.requestCancellation();
            return result(applicationState.isReady(), false, false, false);
        }
    }

    private void runProbe(ProbeWork work) {
        DatabaseReadinessProbe.DatabaseReadiness database = DatabaseReadinessProbe.DatabaseReadiness.down();
        work.started();
        try {
            if (!work.cancelled && System.nanoTime() - work.deadlineNanos < 0) {
                database = databaseProbe.check(work.deadlineNanos);
            }
        } catch (RuntimeException exception) {
            // Public readiness never includes dependency exception details.
        } finally {
            synchronized (monitor) {
                // Only real return from the probe (including JDBC close) frees this
                // identity. An interrupted/cancelled waiter cannot free it.
                work.finished();
                if (active == work) {
                    active = null;
                }
                work.result.complete(database);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        synchronized (monitor) {
            closed = true;
            if (active != null) {
                active.requestCancellation();
            }
        }
        executor.shutdownNow();
    }

    private static ReadinessResult result(
            boolean applicationReady,
            boolean databaseReady,
            boolean migrationsReady,
            boolean localityReady) {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("application", status(applicationReady));
        components.put("database", status(databaseReady));
        components.put("migrations", status(migrationsReady));
        return new ReadinessResult(
                applicationReady && databaseReady && migrationsReady && localityReady,
                Collections.unmodifiableMap(components));
    }

    private static String status(boolean ready) {
        return ready ? "UP" : "DOWN";
    }

    private static ExecutorService readinessExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "readiness-database");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static final class ProbeWork {
        private final long deadlineNanos;
        private final CompletableFuture<DatabaseReadinessProbe.DatabaseReadiness> result = new CompletableFuture<>();
        private volatile boolean cancelled;
        private Thread worker;

        private ProbeWork(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        private synchronized void started() {
            worker = Thread.currentThread();
            if (cancelled) {
                worker.interrupt();
            }
        }

        private synchronized void requestCancellation() {
            cancelled = true;
            if (worker != null) {
                worker.interrupt();
            }
        }

        private synchronized void finished() {
            worker = null;
        }
    }

    record ReadinessResult(boolean ready, Map<String, String> components) {
    }
}
