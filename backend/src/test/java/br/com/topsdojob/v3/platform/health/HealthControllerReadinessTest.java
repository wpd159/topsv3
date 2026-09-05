package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaCoordenador;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerReadinessTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final ResultSet result = mock(ResultSet.class);
    private final LocalidadesConsultaCoordenador coordinator = mock(LocalidadesConsultaCoordenador.class);
    private ObjectProvider<DataSource> dataSources;
    private ObjectProvider<LocalidadesConsultaCoordenador> localidades;
    private HealthController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        dataSources = mock(ObjectProvider.class);
        localidades = mock(ObjectProvider.class);
        when(localidades.getIfAvailable()).thenReturn(coordinator);
        when(dataSources.getIfAvailable()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getNetworkTimeout()).thenReturn(30_000);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        controller = new HealthController(dataSources, localidades);
    }

    @AfterEach
    void closeController() {
        controller.close();
    }

    @Test
    void healthELivenessContinuamUpSemResolverDataSource() {
        assertThat(controller.health(request()).status()).isEqualTo("UP");
        assertThat(controller.liveness(request()).status()).isEqualTo("UP");
        verifyNoInteractions(dataSources, dataSource, connection, localidades, coordinator);
    }

    @Test
    void ausenciaDoCoordenadorNaoExigeCatalogoAquecido() throws Exception {
        when(localidades.getIfAvailable()).thenReturn(null);

        assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(statement).executeQuery("SELECT 1");
        verifyNoInteractions(coordinator);
    }

    @Test
    void execucaoDentroDoPrazoNaoBloqueiaReadinessNemExecutaCatalogo() throws Exception {
        when(coordinator.possuiExecucaoEmVoo()).thenReturn(true);
        when(coordinator.possuiExecucaoExpirada()).thenReturn(false);

        assertThat(controller.readiness(request()).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(statement).executeQuery("SELECT 1");
        verify(coordinator, times(2)).possuiExecucaoExpirada();
        verifyNoMoreInteractions(coordinator);
    }

    @Test
    void execucaoExpiradaAindaEmVooRetorna503SemConsultarBanco() {
        when(coordinator.possuiExecucaoExpirada()).thenReturn(true);

        assertDown(controller.readiness(request()));
        verify(coordinator).possuiExecucaoExpirada();
        verifyNoInteractions(dataSources, dataSource, connection);
    }

    @Test
    void retiradaDaExecucaoExpiradaRecuperaSemMemoriaDeFalha() throws Exception {
        AtomicBoolean expiredInFlight = new AtomicBoolean(true);
        when(coordinator.possuiExecucaoExpirada()).thenAnswer(invocation -> expiredInFlight.get());

        assertDown(controller.readiness(request()));
        verifyNoInteractions(dataSources, dataSource, connection);
        expiredInFlight.set(false);
        awaitReady();
        verify(statement).executeQuery("SELECT 1");
        verify(connection).close();
    }

    @Test
    void expiracaoDuranteFechamentoDoCheckDeBancoTambemRetorna503() throws Exception {
        AtomicBoolean expiredInFlight = new AtomicBoolean(false);
        when(coordinator.possuiExecucaoExpirada()).thenAnswer(invocation -> expiredInFlight.get());
        doAnswer(invocation -> {
            expiredInFlight.set(true);
            return null;
        }).when(connection).close();

        assertDown(controller.readiness(request()));
        verify(statement).executeQuery("SELECT 1");
        verify(connection).close();
        verify(coordinator, times(2)).possuiExecucaoExpirada();
    }

    @Test
    void readinessExecutaSomenteSelectConstanteComTimeoutsERestauraConexao() throws Exception {
        var response = controller.readiness(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new HealthController.HealthResponse(
                "UP", "topsdojob-v3-backend", "readiness-contract"));
        ArgumentCaptor<Integer> networkTimeouts = ArgumentCaptor.forClass(Integer.class);
        verify(connection, times(2)).setNetworkTimeout(any(Executor.class), networkTimeouts.capture());
        assertThat(networkTimeouts.getAllValues().get(0)).isBetween(1, 1_000);
        assertThat(networkTimeouts.getAllValues().get(1)).isEqualTo(30_000);
        var order = inOrder(connection, statement, result);
        order.verify(connection).setNetworkTimeout(any(Executor.class), anyInt());
        order.verify(connection).createStatement();
        order.verify(statement).setQueryTimeout(1);
        order.verify(statement).setMaxRows(1);
        order.verify(statement).executeQuery("SELECT 1");
        order.verify(result).next();
        order.verify(result).getInt(1);
        order.verify(result).close();
        order.verify(statement).close();
        order.verify(connection).setNetworkTimeout(any(Executor.class), eq(30_000));
        order.verify(connection).close();
    }

    @Test
    void ausenciaDeDataSourceRetorna503SemImpedirLiveness() {
        when(dataSources.getIfAvailable()).thenReturn(null);

        assertDown(controller.readiness(request()));
        assertThat(controller.liveness(request()).status()).isEqualTo("UP");
        verifyNoInteractions(dataSource, connection);
    }

    @Test
    void falhaNaAquisicaoRetorna503SemExporDetalhes() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("password=segredo jdbc:postgresql://privado"));

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/health/readiness")
                        .requestAttr(RequestIdContext.ATTRIBUTE_NAME, "readiness-contract"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"status":"DOWN","app":"topsdojob-v3-backend","requestId":"readiness-contract"}
                        """, true));
    }

    @Test
    void falhaNaConsultaFechaRecursosERestauraTimeout() throws Exception {
        when(statement.executeQuery("SELECT 1")).thenThrow(new SQLException("database indisponivel"));

        assertDown(controller.readiness(request()));
        verify(statement).close();
        verify(connection).setNetworkTimeout(any(Executor.class), eq(30_000));
        verify(connection).close();
    }

    @Test
    void resultadoInvalidoNaoSinalizaProntidao() throws Exception {
        when(result.getInt(1)).thenReturn(0);

        assertDown(controller.readiness(request()));
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void erroAoConfigurarTimeoutFechaConexaoEFalhaFechado() throws Exception {
        doAnswer(invocation -> {
            if ((int) invocation.getArgument(1) != 30_000) {
                throw new SQLException("network timeout unsupported");
            }
            return null;
        }).when(connection).setNetworkTimeout(any(Executor.class), anyInt());

        assertDown(controller.readiness(request()));
        verify(connection).close();
        verify(connection, never()).createStatement();
    }

    @Test
    void prazoTotalIncluiAquisicaoEInterrompeEsperaCompativelComHikari() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            try {
                release.await();
                return connection;
            } catch (InterruptedException interruption) {
                interrupted.countDown();
                throw new SQLException("acquisition interrupted", interruption);
            }
        });

        try {
            long start = System.nanoTime();
            assertDown(controller.readiness(request()));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isBetween(800L, 1_800L);
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            verify(connection, never()).createStatement();
        } finally {
            release.countDown();
        }
    }

    @Test
    void concorrenciaECancelamentoNaoLiberamSlotAntesDoFechamentoReal() throws Exception {
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch releaseAcquisition = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            if (acquisitions.incrementAndGet() == 1) {
                acquisitionStarted.countDown();
                awaitIgnoringInterrupt(releaseAcquisition);
            }
            return connection;
        });
        doAnswer(invocation -> {
            if (closes.incrementAndGet() == 1) {
                closeStarted.countDown();
                awaitIgnoringInterrupt(releaseClose);
            }
            return null;
        }).when(connection).close();
        var caller = Executors.newSingleThreadExecutor();

        try {
            var first = caller.submit(() -> controller.readiness(request()));
            assertThat(acquisitionStarted.await(1, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 50; index++) {
                assertDown(controller.readiness(request()));
            }
            assertDown(first.get(2, TimeUnit.SECONDS));
            for (int index = 0; index < 50; index++) {
                assertDown(controller.readiness(request()));
            }
            assertThat(acquisitions).hasValue(1);

            releaseAcquisition.countDown();
            assertThat(closeStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertDown(controller.readiness(request()));
            assertThat(acquisitions).hasValue(1);
            verify(connection, never()).createStatement();

            releaseClose.countDown();
            awaitReady();
            assertThat(acquisitions).hasValue(2);
            verify(connection, times(2)).close();
        } finally {
            releaseAcquisition.countDown();
            releaseClose.countDown();
            caller.shutdownNow();
        }
    }

    @Test
    void consultaQueIgnoraInterrupcaoNaoMultiplicaTrabalhoAposTimeout() throws Exception {
        CountDownLatch releaseQuery = new CountDownLatch(1);
        when(statement.executeQuery("SELECT 1")).thenAnswer(invocation -> {
            awaitIgnoringInterrupt(releaseQuery);
            return result;
        });
        try {
            long start = System.nanoTime();
            assertDown(controller.readiness(request()));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isBetween(800L, 1_800L);
            for (int index = 0; index < 50; index++) {
                assertDown(controller.readiness(request()));
            }
            verify(dataSource).getConnection();
            verify(statement).executeQuery("SELECT 1");
        } finally {
            releaseQuery.countDown();
        }
        verify(connection, timeout(1_000)).close();
        verify(statement).close();
        verify(result).close();
    }

    @Test
    void encerramentoDoWorkerFalhaFechadoMasPreservaLiveness() {
        controller.close();

        assertDown(controller.readiness(request()));
        assertDown(controller.readiness(request()));
        assertThat(controller.liveness(request()).status()).isEqualTo("UP");
        verifyNoInteractions(dataSources, dataSource);
    }

    @Test
    void encerramentoDuranteAquisicaoRetorna503SemPropagarCancelamento() throws Exception {
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            acquisitionStarted.countDown();
            try {
                release.await();
                return connection;
            } catch (InterruptedException interruption) {
                throw new SQLException("acquisition interrupted", interruption);
            }
        });
        var caller = Executors.newSingleThreadExecutor();
        try {
            var response = caller.submit(() -> controller.readiness(request()));
            assertThat(acquisitionStarted.await(1, TimeUnit.SECONDS)).isTrue();
            controller.close();
            assertDown(response.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    private void awaitReady() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            if (controller.readiness(request()).getStatusCode() == HttpStatus.OK) {
                return;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("readiness nao recuperou depois do fechamento efetivo");
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health/readiness");
        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, "readiness-contract");
        return request;
    }

    private static void assertDown(ResponseEntity<HealthController.HealthResponse> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new HealthController.HealthResponse(
                "DOWN", "topsdojob-v3-backend", "readiness-contract"));
    }
}
