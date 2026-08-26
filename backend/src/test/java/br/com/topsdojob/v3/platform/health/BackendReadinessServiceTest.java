package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BackendReadinessServiceTest {

    @Test
    void reprovaEnquantoAplicacaoAindaInicializaSemConsultarBanco() {
        var state = new ApplicationReadinessState();
        var probe = mock(DatabaseReadinessProbe.class);

        try (var fixture = fixture(state, probe, 500)) {
            var result = fixture.service().check();

            assertThat(result.ready()).isFalse();
            assertThat(result.components()).containsEntry("application", "DOWN");
            verify(probe, never()).check();
        }
    }

    @Test
    void aprovaAplicacaoBancoEMigrationsProntos() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        when(probe.check()).thenReturn(new DatabaseReadinessProbe.DatabaseReadiness(true, true));

        try (var fixture = fixture(state, probe, 500)) {
            var result = fixture.service().check();

            assertThat(result.ready()).isTrue();
            assertThat(result.components()).containsExactly(
                    org.assertj.core.api.Assertions.entry("application", "UP"),
                    org.assertj.core.api.Assertions.entry("database", "UP"),
                    org.assertj.core.api.Assertions.entry("migrations", "UP"));
        }
    }

    @Test
    void reprovaBancoOuMigrationCritica() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        when(probe.check())
                .thenReturn(DatabaseReadinessProbe.DatabaseReadiness.down())
                .thenReturn(new DatabaseReadinessProbe.DatabaseReadiness(true, false));

        try (var fixture = fixture(state, probe, 500)) {
            assertThat(fixture.service().check().ready()).isFalse();
            var migrationFailure = fixture.service().check();
            assertThat(migrationFailure.ready()).isFalse();
            assertThat(migrationFailure.components())
                    .containsEntry("database", "UP")
                    .containsEntry("migrations", "DOWN");
        }
    }

    @Test
    void interrompeProbeQueExcedeTimeoutSemPrenderARequisicao() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        when(probe.check()).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return new DatabaseReadinessProbe.DatabaseReadiness(true, true);
        });

        long started = System.nanoTime();
        try (var fixture = fixture(state, probe, 100)) {
            var result = fixture.service().check();

            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertThat(result.ready()).isFalse();
            assertThat(elapsedMillis).isLessThan(1_000);
        }
    }

    @Test
    void recuperaReadinessAutomaticamenteQuandoDependenciaVolta() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        AtomicBoolean available = new AtomicBoolean(false);
        when(probe.check()).thenAnswer(invocation -> available.get()
                ? new DatabaseReadinessProbe.DatabaseReadiness(true, true)
                : DatabaseReadinessProbe.DatabaseReadiness.down());

        try (var fixture = fixture(state, probe, 500)) {
            assertThat(fixture.service().check().ready()).isFalse();
            available.set(true);
            assertThat(fixture.service().check().ready()).isTrue();
        }
    }

    private static ApplicationReadinessState readyState() {
        var state = new ApplicationReadinessState();
        state.applicationReady();
        return state;
    }

    private static ServiceFixture fixture(
            ApplicationReadinessState state,
            DatabaseReadinessProbe probe,
            long timeoutMillis) {
        var executor = Executors.newSingleThreadExecutor();
        var service = new BackendReadinessService(
                state,
                probe,
                Duration.ofMillis(timeoutMillis),
                executor);
        return new ServiceFixture(service);
    }

    private record ServiceFixture(BackendReadinessService service) implements AutoCloseable {

        @Override
        public void close() {
            service.shutdown();
        }
    }
}
