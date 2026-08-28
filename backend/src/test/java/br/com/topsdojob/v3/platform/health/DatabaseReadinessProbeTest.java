package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseReadinessProbeTest {

    @Test
    void validaSelectReadOnlyEFlywayCompativelSemConsultaPesada() throws Exception {
        Fixture fixture = fixture(1, 53, 0);

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
        Fixture fixture = fixture(1, 53, 1);

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isFalse();
    }

    @Test
    void reprovaSchemaAbaixoDaVersaoMinimaDoCodigo() throws Exception {
        Fixture fixture = fixture(1, 52, 0);

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isFalse();
    }

    @Test
    void aceitaSchemaAditivoMaisNovoParaPreservarRollbackDaAplicacao() throws Exception {
        Fixture fixture = fixture(1, 54, 0);

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
        Fixture fixture = fixture(1, 53, 0);
        when(fixture.migrationsStatement().executeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new SQLException("historico indisponivel"));

        var result = fixture.probe().check();

        assertThat(result.databaseReady()).isTrue();
        assertThat(result.migrationsReady()).isFalse();
    }

    private static Fixture fixture(int databaseValue, int version, long failed) throws Exception {
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
                new DatabaseReadinessProbe(dataSource, catalog(), 1),
                connection,
                databaseStatement,
                migrationsStatement);
    }

    private static PackagedMigrationCatalog catalog() {
        return new PackagedMigrationCatalog();
    }

    private record Fixture(
            DatabaseReadinessProbe probe,
            Connection connection,
            Statement databaseStatement,
            Statement migrationsStatement) {
    }
}
