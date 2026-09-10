package br.com.topsdojob.v3.platform.health;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class DatabaseReadinessProbe {

    private static final String DATABASE_PROBE_SQL = "SELECT 1 AS probe";
    private static final String MIGRATIONS_PROBE_SQL = """
            SELECT
              COALESCE(MAX(version::integer)
                FILTER (WHERE success AND version ~ '^[0-9]+$'), 0) AS latest_version,
              COUNT(*) FILTER (WHERE NOT success) AS failed_count
            FROM flyway_schema_history
            """;

    private final DataSource dataSource;
    private final PackagedMigrationCatalog migrationCatalog;
    private final int queryTimeoutSeconds;
    private final LongSupplier nanoClock;

    @Autowired
    DatabaseReadinessProbe(
            DataSource dataSource,
            PackagedMigrationCatalog migrationCatalog,
            @Value("${app.health.readiness-query-timeout-seconds:1}") int queryTimeoutSeconds) {
        this(dataSource, migrationCatalog, queryTimeoutSeconds, System::nanoTime);
    }

    DatabaseReadinessProbe(
            DataSource dataSource,
            PackagedMigrationCatalog migrationCatalog,
            int queryTimeoutSeconds,
            LongSupplier nanoClock) {
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 5) {
            throw new IllegalArgumentException("readiness-query-timeout-seconds deve estar entre 1 e 5");
        }
        this.dataSource = dataSource;
        this.migrationCatalog = migrationCatalog;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.nanoClock = nanoClock;
    }

    DatabaseReadiness check() {
        return check(nanoClock.getAsLong() + TimeUnit.SECONDS.toNanos(1));
    }

    DatabaseReadiness check(long deadlineNanos) {
        boolean databaseReady = false;
        boolean migrationsReady = false;
        if (expired(deadlineNanos)) {
            return DatabaseReadiness.down();
        }
        try (Connection connection = dataSource.getConnection()) {
            requireTime(deadlineNanos);
            connection.setReadOnly(true);
            requireTime(deadlineNanos);
            connection.setAutoCommit(false);
            try {
                requireTime(deadlineNanos);
                databaseReady = databaseResponds(connection, deadlineNanos);
                requireTime(deadlineNanos);
                if (databaseReady) {
                    migrationsReady = migrationsAreCompatible(connection, deadlineNanos);
                    requireTime(deadlineNanos);
                }
            } finally {
                // Cleanup must really finish before an UP result is observable.
                connection.rollback();
            }
        } catch (SQLException | RuntimeException exception) {
            return expired(deadlineNanos)
                    ? DatabaseReadiness.down() : new DatabaseReadiness(databaseReady, false);
        }
        // try-with-resources has completed close, including a driver that ignored
        // interruption. Its late success must not be reused as health evidence.
        return expired(deadlineNanos)
                ? DatabaseReadiness.down() : new DatabaseReadiness(databaseReady, migrationsReady);
    }

    private boolean databaseResponds(Connection connection, long deadlineNanos) throws SQLException {
        try (Statement statement = statement(connection, deadlineNanos);
                ResultSet result = statement.executeQuery(DATABASE_PROBE_SQL)) {
            return result.next() && result.getInt("probe") == 1;
        }
    }

    private boolean migrationsAreCompatible(Connection connection, long deadlineNanos) throws SQLException {
        try (Statement statement = statement(connection, deadlineNanos);
                ResultSet result = statement.executeQuery(MIGRATIONS_PROBE_SQL)) {
            if (!result.next()) {
                return false;
            }
            int latestVersion = result.getInt("latest_version");
            long failedCount = result.getLong("failed_count");
            return failedCount == 0
                    && latestVersion >= migrationCatalog.minimumCompatibleVersion();
        }
    }

    private Statement statement(Connection connection, long deadlineNanos) throws SQLException {
        requireTime(deadlineNanos);
        Statement statement = connection.createStatement();
        try {
            requireTime(deadlineNanos);
            long remaining = deadlineNanos - nanoClock.getAsLong();
            int remainingSeconds = (int) Math.max(1,
                    (remaining + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
            statement.setQueryTimeout(Math.min(queryTimeoutSeconds, remainingSeconds));
            requireTime(deadlineNanos);
            return statement;
        } catch (SQLException | RuntimeException exception) {
            try {
                statement.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private boolean expired(long deadlineNanos) {
        return Thread.currentThread().isInterrupted() || nanoClock.getAsLong() - deadlineNanos >= 0;
    }

    private void requireTime(long deadlineNanos) throws SQLException {
        if (expired(deadlineNanos)) {
            throw new SQLException("Readiness deadline exhausted");
        }
    }

    record DatabaseReadiness(boolean databaseReady, boolean migrationsReady) {

        static DatabaseReadiness down() {
            return new DatabaseReadiness(false, false);
        }
    }
}
