package br.com.topsdojob.v3.platform.health;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaCoordenador;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController implements AutoCloseable {

    private static final long READINESS_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final Executor NETWORK_TIMEOUT_EXECUTOR = Runnable::run;

    private final ObjectProvider<DataSource> dataSources;
    private final ObjectProvider<LocalidadesConsultaCoordenador> localidades;
    private final AtomicReference<FutureTask<Boolean>> readinessInFlight = new AtomicReference<>();
    private final ThreadPoolExecutor readinessWorker = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "database-readiness");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    public HealthController(ObjectProvider<DataSource> dataSources,
            ObjectProvider<LocalidadesConsultaCoordenador> localidades) {
        this.dataSources = dataSources;
        this.localidades = localidades;
    }

    @GetMapping
    public HealthResponse health(HttpServletRequest request) {
        return response(request, "UP");
    }

    @GetMapping("/readiness")
    public ResponseEntity<HealthResponse> readiness(HttpServletRequest request) {
        long deadline = System.nanoTime() + READINESS_TIMEOUT_NANOS;
        FutureTask<Boolean> check = new FutureTask<>(() -> applicationReady(deadline));
        if (!readinessInFlight.compareAndSet(null, check)) {
            return readinessResponse(request, false);
        }

        try {
            readinessWorker.execute(() -> {
                try {
                    check.run();
                } finally {
                    // Cancellation completes the Future before JDBC necessarily returns.
                    // Only the actual worker may release this check's identity.
                    readinessInFlight.compareAndSet(check, null);
                }
            });
        } catch (RejectedExecutionException unavailable) {
            readinessInFlight.compareAndSet(check, null);
            return readinessResponse(request, false);
        }

        try {
            long remaining = deadline - System.nanoTime();
            boolean ready = remaining > 0 && check.get(remaining, TimeUnit.NANOSECONDS);
            if (deadline - System.nanoTime() <= 0) {
                check.cancel(true);
                ready = false;
            }
            return readinessResponse(request, ready);
        } catch (InterruptedException interrupted) {
            check.cancel(true);
            Thread.currentThread().interrupt();
            return readinessResponse(request, false);
        } catch (ExecutionException | TimeoutException | CancellationException unavailable) {
            // Hikari's acquisition wait is interruptible. A driver that ignores the
            // interrupt still occupies this sole worker until its resources close.
            check.cancel(true);
            return readinessResponse(request, false);
        }
    }

    @GetMapping("/liveness")
    public HealthResponse liveness(HttpServletRequest request) {
        return response(request, "UP");
    }

    private boolean applicationReady(long deadline) throws SQLException {
        LocalidadesConsultaCoordenador coordinator = localidades.getIfAvailable();
        if (coordinator != null && coordinator.possuiExecucaoExpirada()) {
            return false;
        }
        // This is only a negative signal for unfinished, expired work: no catalog
        // warmup, cached failure, or requirement for a previous successful load.
        return databaseReady(deadline)
                && (coordinator == null || !coordinator.possuiExecucaoExpirada())
                && withinDeadline(deadline);
    }

    private boolean databaseReady(long deadline) throws SQLException {
        DataSource dataSource = dataSources.getIfAvailable();
        if (dataSource == null || !withinDeadline(deadline)) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!withinDeadline(deadline)) {
                return false;
            }
            int previousNetworkTimeout = connection.getNetworkTimeout();
            try {
                int remainingMillis = (int) Math.max(
                        1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                connection.setNetworkTimeout(NETWORK_TIMEOUT_EXECUTOR, remainingMillis);
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(1);
                    statement.setMaxRows(1);
                    if (!withinDeadline(deadline)) {
                        return false;
                    }
                    try (ResultSet result = statement.executeQuery("SELECT 1")) {
                        return result.next() && result.getInt(1) == 1 && withinDeadline(deadline);
                    }
                }
            } finally {
                connection.setNetworkTimeout(NETWORK_TIMEOUT_EXECUTOR, previousNetworkTimeout);
            }
        }
    }

    private boolean withinDeadline(long deadline) {
        return !Thread.currentThread().isInterrupted() && deadline - System.nanoTime() > 0;
    }

    private ResponseEntity<HealthResponse> readinessResponse(HttpServletRequest request, boolean ready) {
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response(request, ready ? "UP" : "DOWN"));
    }

    @Override
    @PreDestroy
    public void close() {
        FutureTask<Boolean> check = readinessInFlight.get();
        if (check != null) {
            check.cancel(true);
        }
        readinessWorker.shutdownNow();
    }

    private HealthResponse response(HttpServletRequest request, String status) {
        return new HealthResponse(
                status,
                "topsdojob-v3-backend",
                RequestIdContext.current(request));
    }

    public record HealthResponse(
            String status,
            String app,
            String requestId) {
    }
}
