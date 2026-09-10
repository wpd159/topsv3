package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
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
            verify(probe, never()).check(anyLong());
        }
    }

    @Test
    void aprovaAplicacaoBancoEMigrationsProntos() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        when(probe.check(anyLong())).thenReturn(new DatabaseReadinessProbe.DatabaseReadiness(true, true));

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
        when(probe.check(anyLong()))
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
    void interrompeProbeQueExcedeTimeoutSemPrenderARequisicao() throws Exception {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        var interrupted = new CountDownLatch(1);
        when(probe.check(anyLong())).thenAnswer(invocation -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
                return DatabaseReadinessProbe.DatabaseReadiness.down();
            }
            return new DatabaseReadinessProbe.DatabaseReadiness(true, true);
        });

        long started = System.nanoTime();
        try (var fixture = fixture(state, probe, 100)) {
            var result = fixture.service().check();

            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertThat(result.ready()).isFalse();
            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void recuperaReadinessAutomaticamenteQuandoDependenciaVolta() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        AtomicBoolean available = new AtomicBoolean(false);
        when(probe.check(anyLong())).thenAnswer(invocation -> available.get()
                ? new DatabaseReadinessProbe.DatabaseReadiness(true, true)
                : DatabaseReadinessProbe.DatabaseReadiness.down());

        try (var fixture = fixture(state, probe, 500)) {
            assertThat(fixture.service().check().ready()).isFalse();
            available.set(true);
            assertThat(fixture.service().check().ready()).isTrue();
        }
    }

    @Test
    void limitaConfiguracao1500AoTeto1000EPreservaValoresValidosMenores() {
        var probe = mock(DatabaseReadinessProbe.class);
        for (long configured : new long[] {100, 750, 1_000, 1_500, 5_000}) {
            try (var fixture = fixture(readyState(), probe, configured)) {
                assertThat(fixture.service().configuredTimeoutMillis()).isEqualTo(configured);
                assertThat(fixture.service().effectiveTimeoutMillis()).isEqualTo(Math.min(configured, 1_000));
            }
        }
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (long invalid : new long[] {0, 99, 5_001}) {
                assertThatThrownBy(() -> BackendReadinessService.forTesting(
                        readyState(), probe, Duration.ofMillis(invalid), executor))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("entre 100 e 5000");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void coordenadorExpiradoDerrubaGlobalSemConsultarOuInventarFalhaDoBanco() {
        var probe = mock(DatabaseReadinessProbe.class);
        when(probe.check(anyLong())).thenReturn(new DatabaseReadinessProbe.DatabaseReadiness(true, true));
        AtomicBoolean expired = new AtomicBoolean(true);
        try (var fixture = fixture(readyState(), probe, expired::get, 500)) {
            var rejected = fixture.service().check();
            assertThat(rejected.ready()).isFalse();
            assertThat(rejected.components()).containsEntry("database", "UNKNOWN").containsEntry("migrations", "UNKNOWN");
            verify(probe, never()).check(anyLong());
            expired.set(false);
            assertThat(fixture.service().check().ready()).isTrue();
        }
    }

    @Test
    void reconfirmaCoordenadorDepoisDoProbeERecuperaSemCache() {
        var probe = mock(DatabaseReadinessProbe.class);
        AtomicBoolean expired = new AtomicBoolean(false);
        when(probe.check(anyLong())).thenAnswer(invocation -> {
            expired.set(true);
            return new DatabaseReadinessProbe.DatabaseReadiness(true, true);
        });
        try (var fixture = fixture(readyState(), probe, expired::get, 500)) {
            var rejected = fixture.service().check();
            assertThat(rejected.ready()).isFalse();
            assertThat(rejected.components()).containsEntry("database", "UP").containsEntry("migrations", "UP");
        }
    }

    @Test
    void fechamentoDaAplicacaoDuranteProbeNaoRetornaUp() {
        var state = readyState();
        var probe = mock(DatabaseReadinessProbe.class);
        when(probe.check(anyLong())).thenAnswer(invocation -> {
            state.applicationClosing();
            return new DatabaseReadinessProbe.DatabaseReadiness(true, true);
        });
        try (var fixture = fixture(state, probe, 500)) {
            var rejected = fixture.service().check();
            assertThat(rejected.ready()).isFalse();
            assertThat(rejected.components()).containsEntry("application", "DOWN").containsEntry("database", "UP");
        }
    }

    @Test
    void shutdownAposTerminoDoProdutorAntesDaDecisaoNaoConcedeUp() {
        var probe = mock(DatabaseReadinessProbe.class);
        AtomicInteger checks = new AtomicInteger();
        var service = new java.util.concurrent.atomic.AtomicReference<BackendReadinessService>();
        BooleanSupplier locality = () -> {
            if (checks.incrementAndGet() == 2) {
                service.get().shutdown();
            }
            return false;
        };
        when(probe.check(anyLong())).thenReturn(new DatabaseReadinessProbe.DatabaseReadiness(true, true));
        try (var fixture = fixture(readyState(), probe, locality, 500)) {
            service.set(fixture.service());
            var result = fixture.service().check();
            assertThat(result.ready()).isFalse();
            assertThat(result.components()).containsEntry("application", "DOWN");
        }
    }

    @Test
    void vinteConsumidoresCompartilhamProdutorESeuPrazoSemConexoesAdicionaisNaDrenagem() throws Exception {
        var probe = mock(DatabaseReadinessProbe.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        AtomicLong producerDeadline = new AtomicLong();
        when(probe.check(anyLong())).thenAnswer(invocation -> {
            int count = invocations.incrementAndGet();
            if (count == 1) {
                producerDeadline.set(invocation.getArgument(0));
                started.countDown();
                awaitIgnoringInterrupt(release);
                ended.countDown();
            }
            return new DatabaseReadinessProbe.DatabaseReadiness(true, true);
        });
        var callers = Executors.newFixedThreadPool(20);
        try (var fixture = fixture(readyState(), probe, 300)) {
            List<Future<BackendReadinessService.ReadinessResult>> responses = new ArrayList<>();
            responses.add(callers.submit(() -> fixture.service().check()));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            // Consumers arrive later than the producer and must not gain a fresh 300 ms.
            Thread.sleep(150);
            for (int index = 1; index < 20; index++) {
                responses.add(callers.submit(() -> fixture.service().check()));
            }
            for (var response : responses) {
                assertThat(response.get(1, TimeUnit.SECONDS).ready()).isFalse();
            }
            assertThat(System.nanoTime() - producerDeadline.get()).isLessThan(TimeUnit.MILLISECONDS.toNanos(250));
            assertThat(invocations).hasValue(1);
            for (int index = 0; index < 20; index++) {
                assertThat(fixture.service().check().ready()).isFalse();
            }
            assertThat(invocations).hasValue(1);
            assertThat(ended.getCount()).isEqualTo(1);
            release.countDown();
            assertThat(ended.await(1, TimeUnit.SECONDS)).isTrue();
            awaitReady(fixture.service());
            assertThat(invocations).hasValue(2);
        } finally {
            release.countDown();
            callers.shutdownNow();
            assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void resultadoObtidoAntesDoPrazoMasDecisaoTardiaNaoConcedeUp() {
        var probe = mock(DatabaseReadinessProbe.class);
        AtomicInteger checks = new AtomicInteger();
        BooleanSupplier locality = () -> {
            if (checks.incrementAndGet() == 2) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            return false;
        };
        when(probe.check(anyLong())).thenReturn(new DatabaseReadinessProbe.DatabaseReadiness(true, true));
        try (var fixture = fixture(readyState(), probe, locality, 100)) {
            assertThat(fixture.service().check().ready()).isFalse();
            assertThat(checks).hasValue(2);
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitReady(BackendReadinessService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            if (service.check().ready()) {
                return;
            }
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Readiness did not recover after actual drain");
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
        return fixture(state, probe, () -> false, timeoutMillis);
    }

    private static ServiceFixture fixture(
            ApplicationReadinessState state,
            DatabaseReadinessProbe probe,
            BooleanSupplier expired,
            long timeoutMillis) {
        var executor = Executors.newSingleThreadExecutor();
        var service = BackendReadinessService.forTesting(
                state,
                probe,
                expired,
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
