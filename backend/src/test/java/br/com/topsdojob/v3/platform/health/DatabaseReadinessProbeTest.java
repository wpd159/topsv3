package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseReadinessProbeTest {

    @Test
    void validaSelectReadOnlyEFlywayCompativelSemConsultaPesada() throws Exception {
        Fixture fixture = fixture(1, 55, 0);

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isTrue();
        verify(fixture.connection()).setReadOnly(true);
        verify(fixture.connection()).setAutoCommit(false);
        verify(fixture.connection()).rollback();
        verify(fixture.connection(), times(2)).createStatement();
        verify(fixture.databaseStatement()).setQueryTimeout(1);
        verify(fixture.migrationsStatement()).setQueryTimeout(1);
    }

    @Test
    void reprovaMigrationFalha() throws Exception {
        Fixture fixture = fixture(1, 55, 1);

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isFalse();
    }

    @Test
    void reprovaSchemaAbaixoDaVersaoMinimaDoCodigo() throws Exception {
        Fixture fixture = fixture(1, 54, 0);

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isFalse();
    }

    @Test
    void aceitaSchemaAditivoMaisNovoParaPreservarRollbackDaAplicacao() throws Exception {
        Fixture fixture = fixture(1, 56, 0);

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isTrue();
    }

    @Test
    void reprovaQuandoPostgresEstaIndisponivel() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("indisponivel"));
        var probe = new DatabaseReadinessProbe(dataSource, catalog(), 1);

        var result = probe.check();

        assertThat(result).isEqualTo(DatabaseReadinessProbe.DatabaseReadiness.down());
    }

    @Test
    void reconheceBancoMasReprovaSeHistoricoFlywayNaoPodeSerLido() throws Exception {
        Fixture fixture = fixture(1, 55, 0);
        when(fixture.migrationsStatement().executeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new SQLException("historico indisponivel"));

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"acquisition", "database-query", "migration-query", "rollback", "close"})
    void prazoMonotonicoIncluiCadaFaseAteFechamentoEfetivo(String phase) throws Exception {
        AtomicLong clock = new AtomicLong();
        Fixture fixture = fixture(1, 55, 0, clock::get);
        long deadline = 100_000_000L;
        switch (phase) {
            case "acquisition" -> when(fixture.dataSource().getConnection()).thenAnswer(invocation -> {
                clock.set(deadline);
                return fixture.connection();
            });
            case "database-query" -> when(fixture.databaseStatement().executeQuery(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> {
                        clock.set(deadline);
                        return fixture.databaseResult();
                    });
            case "migration-query" -> when(fixture.migrationsStatement().executeQuery(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> {
                        clock.set(deadline);
                        return fixture.migrationsResult();
                    });
            case "rollback" -> doAnswer(invocation -> {
                clock.set(deadline);
                return null;
            }).when(fixture.connection()).rollback();
            case "close" -> doAnswer(invocation -> {
                clock.set(deadline);
                return null;
            }).when(fixture.connection()).close();
            default -> throw new AssertionError(phase);
        }

        assertThat(fixture.probe().check(deadline)).isEqualTo(DatabaseReadinessProbe.DatabaseReadiness.down());
        verify(fixture.connection()).close();
        if (phase.equals("acquisition")) {
            verify(fixture.connection(), never()).createStatement();
        } else {
            verify(fixture.connection()).rollback();
        }
    }

    @Test
    void prazoJaExpiradoNaoAdquireConexao() throws Exception {
        Fixture fixture = fixture(1, 55, 0, () -> 100);
        assertThat(fixture.probe().check(100)).isEqualTo(DatabaseReadinessProbe.DatabaseReadiness.down());
        verify(fixture.dataSource(), never()).getConnection();
    }

    @Test
    void erroAoConfigurarStatementFechaStatementConexaoERollback() throws Exception {
        Fixture fixture = fixture(1, 55, 0);
        doThrow(new SQLException("unsupported timeout")).when(fixture.databaseStatement()).setQueryTimeout(1);

        assertThat(fixture.probe().check()).isEqualTo(DatabaseReadinessProbe.DatabaseReadiness.down());
        verify(fixture.databaseStatement()).close();
        verify(fixture.connection()).rollback();
        verify(fixture.connection()).close();
        verify(fixture.databaseStatement(), never()).executeQuery(org.mockito.ArgumentMatchers.anyString());
    }

    private static Fixture fixture(int databaseValue, int version, long failed) throws Exception {
        return fixture(databaseValue, version, failed, System::nanoTime);
    }

    private static Fixture fixture(int databaseValue, int version, long failed, LongSupplier clock) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement databaseStatement = mock(Statement.class);
        Statement migrationsStatement = mock(Statement.class);
        ResultSet databaseResult = mock(ResultSet.class);
        ResultSet migrationsResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(databaseStatement, migrationsStatement);
        when(databaseStatement.executeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(databaseResult);
        when(migrationsStatement.executeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(migrationsResult);
        when(databaseResult.next()).thenReturn(true);
        when(databaseResult.getInt("probe")).thenReturn(databaseValue);
        when(migrationsResult.next()).thenReturn(true);
        when(migrationsResult.getInt("latest_version")).thenReturn(version);
        when(migrationsResult.getLong("failed_count")).thenReturn(failed);

        return new Fixture(
                new DatabaseReadinessProbe(dataSource, catalog(), 1, clock),
                dataSource,
                connection,
                databaseStatement,
                migrationsStatement,
                databaseResult,
                migrationsResult);
    }

    private static PackagedMigrationCatalog catalog() {
        return new PackagedMigrationCatalog();
    }

    private record Fixture(
            DatabaseReadinessProbe probe,
            DataSource dataSource,
            Connection connection,
            Statement databaseStatement,
            Statement migrationsStatement,
            ResultSet databaseResult,
            ResultSet migrationsResult) {
    }
}
