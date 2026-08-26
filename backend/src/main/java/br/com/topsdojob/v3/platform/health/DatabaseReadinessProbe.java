package br.com.topsdojob.v3.platform.health;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
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

    DatabaseReadinessProbe(
            DataSource dataSource,
            PackagedMigrationCatalog migrationCatalog,
            @Value("${app.health.readiness-query-timeout-seconds:1}") int queryTimeoutSeconds) {
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 5) {
            throw new IllegalArgumentException("readiness-query-timeout-seconds deve estar entre 1 e 5");
        }
        this.dataSource = dataSource;
        this.migrationCatalog = migrationCatalog;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    DatabaseReadiness check() {
        boolean databaseReady = false;
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            databaseReady = databaseResponds(connection);
            if (!databaseReady) {
                connection.rollback();
                return DatabaseReadiness.down();
            }

            boolean migrationsReady = migrationsAreCompatible(connection);
            connection.rollback();
            return new DatabaseReadiness(true, migrationsReady);
        } catch (SQLException | RuntimeException exception) {
            return new DatabaseReadiness(databaseReady, false);
        }
    }

    private boolean databaseResponds(Connection connection) throws SQLException {
        try (Statement statement = statement(connection);
                ResultSet result = statement.executeQuery(DATABASE_PROBE_SQL)) {
            return result.next() && result.getInt("probe") == 1;
        }
    }

    private boolean migrationsAreCompatible(Connection connection) throws SQLException {
        try (Statement statement = statement(connection);
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

    private Statement statement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(queryTimeoutSeconds);
        return statement;
    }

    record DatabaseReadiness(boolean databaseReady, boolean migrationsReady) {

        static DatabaseReadiness down() {
            return new DatabaseReadiness(false, false);
        }
    }
}
