package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.server.ResponseStatusException;

class LocalidadesConsultaOrcamentoTest {

    private final AtomicLong nanos = new AtomicLong();

    @AfterEach
    void limparContexto() {
        LocalidadesConsultaOrcamento.limpar();
        TransactionSynchronizationManager.clear();
        Thread.interrupted();
    }

    @Test
    void exigeDuracaoPositivaEMantemFracaoPositivaSemTimeoutZero() {
        assertThatThrownBy(() -> new LocalidadesConsultaOrcamento(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalidadesConsultaOrcamento(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        var orcamento = orcamento(Duration.ofNanos(999));
        assertThat(orcamento.restanteMillis()).isEqualTo(1);
        nanos.set(999);
        assertIndisponivel(orcamento::conferir);
    }

    @Test
    void prazoMonotonicoNaoReiniciaEntreEtapasEFuncionaNoOverflowDoRelogio() {
        nanos.set(Long.MAX_VALUE - 10);
        var orcamento = orcamento(Duration.ofNanos(20));
        nanos.addAndGet(15);
        assertThat(orcamento.restanteMillis()).isEqualTo(1);
        nanos.addAndGet(5);
        assertIndisponivel(orcamento::conferir);
    }

    @Test
    void interrupcaoFalhaFechadoSemLimparAFlagDoWorker() {
        var orcamento = orcamento(Duration.ofSeconds(4));
        Thread.currentThread().interrupt();
        assertThat(orcamento.expirou()).isFalse();
        assertIndisponivel(orcamento::conferir);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        nanos.set(Duration.ofSeconds(4).toNanos());
        assertThat(orcamento.expirou()).isTrue();
    }

    @Test
    void contextoEhRestauradoMesmoComFalhaENaoPublicaResultadoTardio() {
        var anterior = orcamento(Duration.ofSeconds(4));
        var atual = orcamento(Duration.ofSeconds(1));
        anterior.instalar();
        assertIndisponivel(() -> atual.executar(() -> {
            assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isSameAs(atual);
            nanos.set(Duration.ofSeconds(1).toNanos());
            return "resultado tardio";
        }));
        assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isSameAs(anterior);
        LocalidadesConsultaOrcamento.limpar();
        assertThatThrownBy(() -> anterior.executar(() -> {
            throw new IllegalArgumentException("falha original");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("falha original");
        assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
    }

    @Test
    void recusaTransacaoHerdadaAntesDeAcessarOPool() {
        var manager = mock(PlatformTransactionManager.class);
        var em = mock(EntityManager.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4)).transacao(manager, em, () -> "ok"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("herdar transacao");
        verifyNoInteractions(manager, em);
    }

    @Test
    void naoConsultaSeAConexaoSoChegaAposOPrazo() throws Exception {
        var fixture = new Fixture();
        doAnswer(invocation -> {
            nanos.set(Duration.ofSeconds(4).toNanos());
            return fixture.iniciar();
        }).when(fixture.manager).getTransaction(any());
        assertIndisponivel(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> "nao deve executar"));
        verifyNoInteractions(fixture.em, fixture.connection, fixture.statement);
        verify(fixture.manager).rollback(fixture.status);
    }

    @Test
    void configuraTimeoutsLocaisEMantemRedeAteCommitAntesDeDevolverAoPool() throws Exception {
        var fixture = new Fixture();
        var orcamento = orcamento(Duration.ofMillis(3500));
        nanos.set(Duration.ofMillis(1200).toNanos());
        assertThat(orcamento.transacao(fixture.manager, fixture.em, () -> {
            assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isSameAs(orcamento);
            return "ok";
        })).isEqualTo("ok");

        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(fixture.manager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        assertThat(definition.getValue().getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(definition.getValue().getTimeout()).isEqualTo(3);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(fixture.connection).prepareStatement(sql.capture());
        assertThat(sql.getValue())
                .contains("set_config('statement_timeout', ?, true)")
                .contains("set_config('lock_timeout', ?, true)")
                .contains("set_config('transaction_timeout', ?, true)");
        var ordem = inOrder(fixture.connection, fixture.statement, fixture.manager, fixture.devolverAoPool);
        ordem.verify(fixture.connection).setNetworkTimeout(any(), eq(2300));
        ordem.verify(fixture.statement).setQueryTimeout(3);
        ordem.verify(fixture.statement).setString(1, "2300ms");
        ordem.verify(fixture.statement).setString(2, "2300ms");
        ordem.verify(fixture.statement).setString(3, "2300ms");
        ordem.verify(fixture.statement).execute();
        ordem.verify(fixture.statement).close();
        ordem.verify(fixture.manager).commit(fixture.status);
        ordem.verify(fixture.connection).setNetworkTimeout(any(), eq(12000));
        ordem.verify(fixture.devolverAoPool).run();
    }

    @Test
    void novaTransacaoUsaSomenteOSaldoDoMesmoOrcamento() throws Exception {
        var fixture = new Fixture();
        var orcamento = orcamento(Duration.ofMillis(3500));
        orcamento.transacao(fixture.manager, fixture.em, () -> "primeira");
        nanos.set(Duration.ofMillis(2900).toNanos());
        orcamento.transacao(fixture.manager, fixture.em, () -> "segunda");
        verify(fixture.statement).setString(3, "600ms");
        verify(fixture.connection).setNetworkTimeout(any(), eq(600));
    }

    @Test
    void erroDoCorpoERestauracaoPreservamFalhaOriginalEDescartamFisicaDepoisDoRollback() throws Exception {
        var fixture = new Fixture();
        doThrow(new SQLException("restauracao falhou"))
                .when(fixture.connection).setNetworkTimeout(any(), eq(12000));
        var original = new IllegalArgumentException("falha original");
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> { throw original; }))
                .isSameAs(original)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .singleElement().isInstanceOf(IllegalStateException.class));
        verify(fixture.manager).rollback(fixture.status);
        verify(fixture.manager, never()).commit(any());
        var ordem = inOrder(fixture.manager, fixture.connection, fixture.fisica, fixture.devolverAoPool);
        ordem.verify(fixture.manager).rollback(fixture.status);
        ordem.verify(fixture.connection).setNetworkTimeout(any(), eq(12000));
        ordem.verify(fixture.fisica).abort(any());
        ordem.verify(fixture.devolverAoPool).run();
        assertThat(fixture.fisicaFechada).isTrue();
        verify(fixture.connection, never()).close();
    }

    @Test
    void conexaoEncerradaPeloServidorNaoRecebeRestauracaoNemEscondeFalha() throws Exception {
        var fixture = new Fixture();
        when(fixture.connection.isClosed()).thenReturn(true);
        var original = new IllegalStateException("servidor encerrou transacao");
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> { throw original; }))
                .isSameAs(original);
        verify(fixture.connection, never()).setNetworkTimeout(any(), eq(12000));
        verify(fixture.fisica, never()).abort(any());
        verify(fixture.manager).rollback(fixture.status);
    }

    @Test
    void falhaAoConfigurarSqlTambemRestauraRede() throws Exception {
        var fixture = new Fixture();
        when(fixture.statement.execute()).thenThrow(new SQLException("configuracao falhou"));
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> "nao deve executar"))
                .isInstanceOf(RuntimeException.class);
        verify(fixture.statement).close();
        verify(fixture.connection).setNetworkTimeout(any(), eq(12000));
        verify(fixture.manager).rollback(fixture.status);
        var ordem = inOrder(fixture.manager, fixture.connection, fixture.devolverAoPool);
        ordem.verify(fixture.manager).rollback(fixture.status);
        ordem.verify(fixture.connection).setNetworkTimeout(any(), eq(12000));
        ordem.verify(fixture.devolverAoPool).run();
    }

    @Test
    void setterQuePodeAlterarRedeAntesDeFalharJaTemCleanupRegistrado() throws Exception {
        var fixture = new Fixture();
        SQLException original = new SQLException("setter parcialmente executado");
        doThrow(original).when(fixture.connection).setNetworkTimeout(any(), eq(4000));
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> "nao executar"))
                .isInstanceOf(IllegalStateException.class).hasCause(original);
        verify(fixture.connection, never()).prepareStatement(anyString());
        var ordem = inOrder(fixture.manager, fixture.connection, fixture.devolverAoPool);
        ordem.verify(fixture.manager).rollback(fixture.status);
        ordem.verify(fixture.connection).setNetworkTimeout(any(), eq(12000));
        ordem.verify(fixture.devolverAoPool).run();
    }

    @Test
    void falhaDeResetAposCommitNaoViraSucessoEAbortSemSuporteFechaFisica() throws Exception {
        var fixture = new Fixture();
        doThrow(new SQLException("reset falhou"))
                .when(fixture.connection).setNetworkTimeout(any(), eq(12000));
        doThrow(new SQLException("abort sem suporte"))
                .when(fixture.fisica).abort(any());
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> "resultado descartado"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("restaurar timeout");
        var ordem = inOrder(fixture.manager, fixture.connection, fixture.fisica, fixture.devolverAoPool);
        ordem.verify(fixture.manager).commit(fixture.status);
        ordem.verify(fixture.connection).setNetworkTimeout(any(), eq(12000));
        ordem.verify(fixture.fisica).abort(any());
        ordem.verify(fixture.fisica).close();
        ordem.verify(fixture.devolverAoPool).run();
        assertThat(fixture.fisicaFechada).isTrue();
        verify(fixture.connection, never()).close();
        verify(fixture.manager, never()).rollback(any());
    }

    @Test
    void semHandleFisicoNaoAlteraTimeoutNemExecutaConsulta() throws Exception {
        var fixture = new Fixture();
        SQLException original = new SQLException("unwrap indisponivel");
        when(fixture.connection.unwrap(Connection.class)).thenThrow(original);
        assertThatThrownBy(() -> orcamento(Duration.ofSeconds(4))
                .transacao(fixture.manager, fixture.em, () -> "nao executar"))
                .isInstanceOf(IllegalStateException.class).hasCause(original);
        verify(fixture.connection, never()).setNetworkTimeout(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(fixture.connection, never()).prepareStatement(anyString());
        verify(fixture.manager).rollback(fixture.status);
        verify(fixture.devolverAoPool).run();
    }

    @Test
    void watchdogAbortaDuranteCommitEAguardaFimDoAbortAntesDaDevolucao() throws Exception {
        var fixture = new Fixture();
        var orcamento = orcamento(Duration.ofMillis(3500));
        CountDownLatch abortIniciado = new CountDownLatch(1);
        CountDownLatch liberarAbort = new CountDownLatch(1);
        doAnswer(invocation -> {
            abortIniciado.countDown();
            assertThat(liberarAbort.await(2, TimeUnit.SECONDS)).isTrue();
            fixture.fisicaFechada.set(true);
            return null;
        }).when(fixture.fisica).abort(any());
        Thread watchdog = new Thread(orcamento::interromperConexao, "localidades-watchdog-test");
        fixture.antesConclusao = () -> {
            nanos.set(Duration.ofMillis(3500).toNanos());
            watchdog.start();
            aguardar(abortIniciado);
        };
        var worker = Executors.newSingleThreadExecutor();
        try {
            var resultado = worker.submit(() ->
                    orcamento.transacao(fixture.manager, fixture.em, () -> "nao publicar"));
            assertThat(abortIniciado.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(resultado.isDone()).isFalse();
            verify(fixture.devolverAoPool, never()).run();
            liberarAbort.countDown();
            assertThatThrownBy(() -> resultado.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ResponseStatusException.class);
            watchdog.join(2000);
            assertThat(watchdog.isAlive()).isFalse();
            var ordem = inOrder(fixture.manager, fixture.fisica, fixture.devolverAoPool);
            ordem.verify(fixture.manager).commit(fixture.status);
            ordem.verify(fixture.fisica).abort(any());
            ordem.verify(fixture.devolverAoPool).run();
            verify(fixture.connection, never()).setNetworkTimeout(any(), eq(12000));
            orcamento.interromperConexao();
            verify(fixture.fisica).abort(any());
            assertThat(fixture.fisicaFechada).isTrue();
        } finally {
            liberarAbort.countDown();
            watchdog.join(2000);
            worker.shutdownNow();
            assertThat(worker.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void watchdogTardioNaoAlcancaConexaoReutilizadaPorOutroOrcamento() throws Exception {
        var fixture = new Fixture();
        var anterior = orcamento(Duration.ofSeconds(4));
        assertThat(anterior.transacao(fixture.manager, fixture.em, () -> "primeira"))
                .isEqualTo("primeira");
        var seguinte = orcamento(Duration.ofSeconds(4));
        assertThat(seguinte.transacao(fixture.manager, fixture.em, () -> {
            anterior.interromperConexao();
            return "conexao reutilizada saudavel";
        })).isEqualTo("conexao reutilizada saudavel");
        anterior.interromperConexao();
        seguinte.interromperConexao();
        verify(fixture.fisica, never()).abort(any());
        verify(fixture.fisica, never()).close();
        assertIndisponivel(() -> anterior.transacao(fixture.manager, fixture.em, () -> "nao executar"));
    }

    @Test
    void cancelamentoSemHandleImpedeSqlQuandoAConexaoChegaDepois() throws Exception {
        var fixture = new Fixture();
        var orcamento = orcamento(Duration.ofSeconds(4));
        doAnswer(invocation -> {
            fixture.iniciar();
            orcamento.interromperConexao();
            return fixture.status;
        }).when(fixture.manager).getTransaction(any());
        assertIndisponivel(() -> orcamento.transacao(fixture.manager, fixture.em, () -> "nao executar"));
        verifyNoInteractions(fixture.em, fixture.connection, fixture.fisica, fixture.statement);
        verify(fixture.manager).rollback(fixture.status);
        verify(fixture.devolverAoPool).run();
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("espera de teste interrompida", exception);
        }
    }

    private LocalidadesConsultaOrcamento orcamento(Duration duracao) {
        return new LocalidadesConsultaOrcamento(duracao, nanos::get);
    }

    private void assertIndisponivel(Runnable trabalho) {
        assertThatThrownBy(trabalho::run).isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static final class Fixture {
        private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        private final EntityManager em = mock(EntityManager.class);
        private final Session session = mock(Session.class);
        private final Connection connection = mock(Connection.class);
        private final Connection fisica = mock(Connection.class);
        private final AtomicBoolean fisicaFechada = new AtomicBoolean();
        private final Runnable devolverAoPool = mock(Runnable.class);
        private final PreparedStatement statement = mock(PreparedStatement.class);
        private final SimpleTransactionStatus status = new SimpleTransactionStatus();
        private Runnable antesConclusao = () -> {};

        Fixture() throws Exception {
            when(manager.getTransaction(any())).thenAnswer(invocation -> iniciar());
            doAnswer(invocation -> {
                concluir(TransactionSynchronization.STATUS_COMMITTED);
                return null;
            }).when(manager).commit(status);
            doAnswer(invocation -> {
                concluir(TransactionSynchronization.STATUS_ROLLED_BACK);
                return null;
            }).when(manager).rollback(status);
            when(em.unwrap(Session.class)).thenReturn(session);
            when(connection.unwrap(Connection.class)).thenReturn(fisica);
            when(connection.getNetworkTimeout()).thenReturn(12000);
            when(fisica.isClosed()).thenAnswer(invocation -> fisicaFechada.get());
            doAnswer(invocation -> { fisicaFechada.set(true); return null; }).when(fisica).abort(any());
            doAnswer(invocation -> { fisicaFechada.set(true); return null; }).when(fisica).close();
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            doAnswer(invocation -> {
                Work work = invocation.getArgument(0);
                try {
                    work.execute(connection);
                } catch (SQLException exception) {
                    throw new IllegalStateException("erro JDBC", exception);
                }
                return null;
            }).when(session).doWork(any());
        }

        private SimpleTransactionStatus iniciar() {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return status;
        }

        private void concluir(int completionStatus) {
            // Mirrors Spring's sequence: actual completion, callbacks, resource/pool cleanup.
            antesConclusao.run();
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            try {
                TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
            } finally {
                TransactionSynchronizationManager.clear();
                devolverAoPool.run();
            }
        }
    }
}
