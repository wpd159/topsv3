package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaCoordenador;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerReadinessTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Statement databaseStatement = mock(Statement.class);
    private final Statement migrationStatement = mock(Statement.class);
    private final ResultSet databaseResult = mock(ResultSet.class);
    private final ResultSet migrationResult = mock(ResultSet.class);
    private final LocalidadesConsultaCoordenador coordinator = mock(LocalidadesConsultaCoordenador.class);
    private final ApplicationReadinessState application = new ApplicationReadinessState();
    private final AtomicInteger statements = new AtomicInteger();
    private BackendReadinessService service;
    private HealthController controller;

    @BeforeEach
    void prepare() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenAnswer(invocation ->
                statements.getAndIncrement() % 2 == 0 ? databaseStatement : migrationStatement);
        doAnswer(invocation -> {
            statements.set(0);
            return null;
        }).when(connection).close();
        when(databaseStatement.executeQuery(anyString())).thenReturn(databaseResult);
        when(migrationStatement.executeQuery(anyString())).thenReturn(migrationResult);
        when(databaseResult.next()).thenReturn(true);
        when(databaseResult.getInt("probe")).thenReturn(1);
        when(migrationResult.next()).thenReturn(true);
        when(migrationResult.getInt("latest_version")).thenReturn(53);
        when(migrationResult.getLong("failed_count")).thenReturn(0L);
        application.applicationReady();
        service = BackendReadinessService.forTesting(
                application, new DatabaseReadinessProbe(dataSource, new PackagedMigrationCatalog(), 1),
                coordinator::possuiExecucaoExpirada, Duration.ofMillis(1_500), Executors.newSingleThreadExecutor());
        controller = new HealthController(service);
    }

    @AfterEach
    void close() {
        service.shutdown();
    }

    @Test
    void healthELivenessNaoConsultamBancoNemCoordenador() {
        assertThat(controller.health(request()).status()).isEqualTo("UP");
        assertThat(controller.liveness(request()).status()).isEqualTo("UP");
        verifyNoInteractions(dataSource, connection, coordinator);
    }

    @Test
    void execucaoDentroDoPrazoNaoExigeAquecimentoDoCatalogo() throws Exception {
        when(coordinator.possuiExecucaoExpirada()).thenReturn(false);
        assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(coordinator, times(2)).possuiExecucaoExpirada();
        verify(connection).setReadOnly(true);
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test
    void coordenadorExpiraDuranteCloseEDerrubaGlobalPreservandoComponentesReais() throws Exception {
        AtomicBoolean expired = new AtomicBoolean();
        when(coordinator.possuiExecucaoExpirada()).thenAnswer(invocation -> expired.get());
        doAnswer(invocation -> {
            expired.set(true);
            return null;
        }).when(connection).close();
        var response = controller.readiness(request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().components()).containsEntry("database", "UP").containsEntry("migrations", "UP");
    }

    @Test
    void select1PassaMasMigrationIncompativelMantem503() throws Exception {
        when(migrationResult.getInt("latest_version")).thenReturn(52);
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.migrations").value("DOWN"));
    }

    @Test
    void falhaNaAquisicaoEh503SanitizadoComShapeDaMain() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("private-db-host:5432"));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/health/readiness")
                        .requestAttr(RequestIdContext.ATTRIBUTE_NAME, "readiness-contract"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"status":"DOWN","app":"topsdojob-v3-backend","requestId":"readiness-contract",
                         "components":{"application":"UP","database":"DOWN","migrations":"DOWN"}}
                        """, true));
    }

    @Test
    void falhaOuRespostaSqlInvalidaFechaRecursos() throws Exception {
        when(databaseResult.getInt("probe")).thenReturn(0);
        assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(databaseResult).close();
        verify(databaseStatement).close();
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test
    void consultaFalhaFechaStatementRollbackEConexao() throws Exception {
        when(databaseStatement.executeQuery(anyString())).thenThrow(new SQLException("unavailable"));
        assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(databaseStatement).close();
        verify(connection).rollback();
        verify(connection).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"acquisition", "database-query", "migration-query", "rollback", "close"})
    void timeoutEmCadaFaseRetemConexaoAteTerminoRealSemReaproveitarResultadoTardio(String phase) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        Runnable block = () -> {
            if (first.getAndSet(false)) {
                entered.countDown();
                awaitIgnoringInterrupt(release);
            }
        };
        switch (phase) {
            case "acquisition" -> when(dataSource.getConnection()).thenAnswer(invocation -> {
                block.run();
                return connection;
            });
            case "database-query" -> when(databaseStatement.executeQuery(anyString())).thenAnswer(invocation -> {
                block.run();
                return databaseResult;
            });
            case "migration-query" -> when(migrationStatement.executeQuery(anyString())).thenAnswer(invocation -> {
                block.run();
                return migrationResult;
            });
            case "rollback" -> doAnswer(invocation -> {
                block.run();
                return null;
            }).when(connection).rollback();
            case "close" -> { }
            default -> throw new AssertionError(phase);
        }
        doAnswer(invocation -> {
            if (phase.equals("close")) {
                block.run();
            }
            statements.set(0);
            closed.countDown();
            return null;
        }).when(connection).close();
        var callers = Executors.newSingleThreadExecutor();
        try {
            long started = System.nanoTime();
            var firstResponse = callers.submit(() -> controller.readiness(request()));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(firstResponse.get(2, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            // The effective deadline is 1000 (not configured1500), with 250 ms
            // scheduling allowance in this local concurrent test.
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(800L, 1_250L);
            for (int index = 0; index < 20; index++) {
                assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            }
            verify(dataSource).getConnection();
            assertThat(closed.getCount()).isEqualTo(1);
            release.countDown();
            assertThat(closed.await(1, TimeUnit.SECONDS)).isTrue();
            if (phase.equals("acquisition")) {
                verify(connection, never()).createStatement();
            }
            awaitHealthy();
            verify(dataSource, times(2)).getConnection();
            verify(connection, times(2)).close();
        } finally {
            release.countDown();
            callers.shutdownNow();
            assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shutdownDuranteAquisicaoNaoPropagaCancelamentoENaoBloqueiaLiveness() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Unexpected release");
            } catch (InterruptedException interruption) {
                throw new SQLException("acquisition interrupted", interruption);
            }
        });
        var callers = Executors.newSingleThreadExecutor();
        try {
            var response = callers.submit(() -> controller.readiness(request()));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            service.shutdown();
            assertThat(response.get(1, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(controller.liveness(request()).status()).isEqualTo("UP");
        } finally {
            callers.shutdownNow();
            assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitHealthy() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            if (controller.readiness(request()).getStatusCode() == HttpStatus.OK) {
                return;
            }
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No recovery after JDBC actually closed");
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

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/health/readiness");
        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, "readiness-contract");
        return request;
    }
}
