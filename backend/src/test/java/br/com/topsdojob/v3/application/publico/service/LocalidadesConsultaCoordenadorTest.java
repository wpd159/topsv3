package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class LocalidadesConsultaCoordenadorTest {

    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final List<ExecutorService> callers = new ArrayList<>();
    private LocalidadesConsultaCoordenador coordinator = coordinator(Duration.ofSeconds(2));

    @AfterEach
    void close() {
        callers.forEach(ExecutorService::shutdownNow);
        coordinator.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 20})
    void consumidoresCompartilhamUmProdutorRealSemCacheDeRespostaConcluida(int consumers) throws Exception {
        ExecutorService executor = callers(consumers);
        CountDownLatch ready = new CountDownLatch(consumers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger work = new AtomicInteger();
        AtomicInteger simultaneous = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicReference<String> producerThread = new AtomicReference<>();
        Object payload = new Object();
        List<Future<Object>> results = new ArrayList<>();

        try {
            for (int index = 0; index < consumers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    return coordinator.executar("descoberta", () -> {
                        work.incrementAndGet();
                        maximum.accumulateAndGet(simultaneous.incrementAndGet(), Math::max);
                        producerThread.set(Thread.currentThread().getName());
                        try {
                            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                            assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNotNull();
                            await(release);
                            return payload;
                        } finally {
                            simultaneous.decrementAndGet();
                        }
                    });
                }));
            }
            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            awaitCondition(() -> coordinator.consumidoresAguardando() == consumers && work.get() == 1);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(1);
            assertThat(coordinator.trabalhoRemanescente()).isEqualTo(1);
            assertThat(coordinator.possuiExecucaoEmVoo()).isTrue();
            assertThat(work).hasValue(1);
            release.countDown();
            for (Future<Object> result : results) {
                assertThat(result.get(1, TimeUnit.SECONDS)).isSameAs(payload);
            }
            awaitIdle();
            assertThat(maximum).hasValue(1);
            assertThat(simultaneous).hasValue(0);
            assertThat(producerThread.get()).isEqualTo("localidades-produtor");
            Object fresh = new Object();
            assertThat(coordinator.executar("descoberta", () -> {
                work.incrementAndGet();
                return fresh;
            })).isSameAs(fresh);
            assertThat(work).hasValue(2);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(2);
            awaitIdle();
        } finally {
            start.countDown();
            release.countDown();
        }
    }

    @Test
    void falhaCompartilhadaLimpaExecucaoEPermiteNovaTentativaSemExporCausa() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger work = new AtomicInteger();
        ExecutorService executor = callers(6);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 6; index++) {
                results.add(executor.submit(() -> coordinator.executar("descoberta", () -> {
                    work.incrementAndGet();
                    await(release);
                    throw new IllegalStateException("credencial-que-nao-deve-sair");
                })));
            }
            awaitCondition(() -> coordinator.consumidoresAguardando() == 6 && work.get() == 1);
            assertThat(coordinator.trabalhoRemanescente()).isEqualTo(1);
            release.countDown();
            for (Future<String> result : results) {
                ResponseStatusException failure = unavailable(result);
                assertThat(failure.getReason()).doesNotContain("credencial");
            }
            assertThat(work).hasValue(1);
            awaitIdle();
            assertThat(coordinator.executar("descoberta", () -> "nova resposta")).isEqualTo("nova resposta");
            assertThat(coordinator.produtoresIniciados()).isEqualTo(2);
            awaitIdle();
        } finally {
            release.countDown();
        }
    }

    @Test
    void prazoInterrompeProdutorCompativelELimpaContadoresAntesDaRecuperacao() throws Exception {
        useShortBudget();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            long start = System.nanoTime();
            assertUnavailable(() -> coordinator.executar("descoberta", () -> {
                try {
                    release.await();
                    return "resposta atrasada";
                } catch (InterruptedException failure) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("producer interrupted", failure);
                }
            }));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isBetween(150L, 1_000L);
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            awaitIdle();
            assertThat(coordinator.produtoresIniciados()).isEqualTo(1);
            assertThat(coordinator.executar("descoberta", () -> {
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                return "recuperado";
            })).isEqualTo("recuperado");
            awaitIdle();
            assertThat(coordinator.produtoresIniciados()).isEqualTo(2);
        } finally {
            release.countDown();
        }
    }

    @Test
    void interrupcaoIndividualNaoCancelaProdutorNemOutroConsumidor() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> interruptedConsumer = new AtomicReference<>();
        AtomicBoolean consumerPreservedInterrupt = new AtomicBoolean();
        AtomicInteger work = new AtomicInteger();
        ExecutorService executor = callers(2);
        try {
            Future<String> first = executor.submit(() -> {
                interruptedConsumer.set(Thread.currentThread());
                try {
                    return coordinator.executar("descoberta", () -> {
                        work.incrementAndGet();
                        await(release);
                        assertThat(Thread.currentThread().isInterrupted()).isFalse();
                        return "compartilhado";
                    });
                } finally {
                    consumerPreservedInterrupt.set(Thread.currentThread().isInterrupted());
                }
            });
            awaitCondition(() -> coordinator.consumidoresAguardando() == 1 && work.get() == 1);
            Future<String> second = executor.submit(() -> coordinator.executar("descoberta", () -> {
                throw new AssertionError("segundo consumidor nao pode produzir");
            }));
            awaitCondition(() -> coordinator.consumidoresAguardando() == 2);
            interruptedConsumer.get().interrupt();
            unavailable(first);
            assertThat(consumerPreservedInterrupt).isTrue();
            assertThat(coordinator.consumidoresAguardando()).isEqualTo(1);
            assertThat(coordinator.trabalhoRemanescente()).isEqualTo(1);
            assertThat(coordinator.possuiExecucaoEmVoo()).isTrue();
            release.countDown();
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("compartilhado");
            assertThat(work).hasValue(1);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(1);
            awaitIdle();
        } finally {
            release.countDown();
        }
    }

    @Test
    void retriesNaoSubstituemProdutorQueIgnoraInterrupcaoAteRetornoEfetivo() throws Exception {
        useShortBudget();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger originalWork = new AtomicInteger();
        AtomicInteger replacementWork = new AtomicInteger();
        AtomicInteger physicalWork = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        try {
            assertUnavailable(() -> coordinator.executar("descoberta", () -> {
                originalWork.incrementAndGet();
                maximum.accumulateAndGet(physicalWork.incrementAndGet(), Math::max);
                try {
                    awaitIgnoringInterrupt(release);
                    return "resultado vencido";
                } finally {
                    physicalWork.decrementAndGet();
                }
            }));
            assertThat(coordinator.consumidoresAguardando()).isZero();
            assertThat(coordinator.trabalhoRemanescente()).isEqualTo(1);
            assertThat(coordinator.possuiExecucaoEmVoo()).isTrue();
            for (int index = 0; index < 20; index++) {
                assertUnavailable(() -> coordinator.executar("descoberta", () -> {
                    replacementWork.incrementAndGet();
                    return "nao deve executar";
                }));
                assertUnavailable(() -> coordinator.executar("catalogo", () -> {
                    replacementWork.incrementAndGet();
                    return List.of("outra chave");
                }));
            }
            assertThat(originalWork).hasValue(1);
            assertThat(replacementWork).hasValue(0);
            assertThat(physicalWork).hasValue(1);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(1);
            assertThat(coordinator.consumidoresAguardando()).isZero();
            release.countDown();
            awaitIdle();
            assertThat(physicalWork).hasValue(0);
            assertThat(coordinator.executar("descoberta", () -> {
                replacementWork.incrementAndGet();
                maximum.accumulateAndGet(physicalWork.incrementAndGet(), Math::max);
                try {
                    return "resultado novo";
                } finally {
                    physicalWork.decrementAndGet();
                }
            })).isEqualTo("resultado novo");
            assertThat(replacementWork).hasValue(1);
            assertThat(maximum).hasValue(1);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(2);
            awaitIdle();
        } finally {
            release.countDown();
        }
    }

    @Test
    void chavesDiferentesNaoMisturamTiposNemCriamSegundoProdutorConcorrente() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger alternativeWork = new AtomicInteger();
        try {
            Future<String> first = callers(1).submit(() -> coordinator.executar("descoberta", () -> {
                await(release);
                return "cidades";
            }));
            awaitCondition(() -> coordinator.consumidoresAguardando() == 1
                    && coordinator.trabalhoRemanescente() == 1);
            assertUnavailable(() -> coordinator.executar("catalogo", () -> {
                alternativeWork.incrementAndGet();
                return 42;
            }));
            assertThat(alternativeWork).hasValue(0);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(1);
            assertThat(coordinator.consumidoresAguardando()).isEqualTo(1);
            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("cidades");
            awaitIdle();
            assertThat(coordinator.executar("catalogo", () -> {
                alternativeWork.incrementAndGet();
                return 42;
            })).isEqualTo(42);
            assertThat(alternativeWork).hasValue(1);
            assertThat(coordinator.produtoresIniciados()).isEqualTo(2);
            awaitIdle();
        } finally {
            release.countDown();
        }
    }

    @Test
    void transacaoExternaFalhaAntesDoProdutorSemSuspenderOuDesvincularRecurso() {
        Object resourceKey = new Object();
        Object resource = new Object();
        AtomicInteger work = new AtomicInteger();
        TransactionSynchronizationManager.bindResource(resourceKey, resource);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertUnavailable(() -> coordinator.executar("descoberta", () -> work.incrementAndGet()));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.getResource(resourceKey)).isSameAs(resource);
            assertThat(work).hasValue(0);
            assertThat(coordinator.produtoresIniciados()).isZero();
            assertThat(coordinator.trabalhoRemanescente()).isZero();
            assertThat(coordinator.consumidoresAguardando()).isZero();
            assertThat(coordinator.possuiExecucaoEmVoo()).isFalse();
            verifyNoInteractions(transactions, entityManager, dataSource);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.unbindResource(resourceKey);
        }
    }

    @Test
    void workerEncerradoNaoDeixaIdentidadeOuContadoresPresos() {
        coordinator.close();
        assertUnavailable(() -> coordinator.executar("descoberta", () -> "nao executar"));
        assertUnavailable(() -> coordinator.executar("descoberta", () -> "nao executar"));
        assertThat(coordinator.possuiExecucaoEmVoo()).isFalse();
        assertThat(coordinator.produtoresIniciados()).isZero();
        assertThat(coordinator.trabalhoRemanescente()).isZero();
        assertThat(coordinator.consumidoresAguardando()).isZero();
    }

    private LocalidadesConsultaCoordenador coordinator(Duration budget) {
        return new LocalidadesConsultaCoordenador(transactions, entityManager, dataSource, budget);
    }

    private void useShortBudget() {
        coordinator.close();
        coordinator = coordinator(Duration.ofMillis(300));
    }

    private ExecutorService callers(int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        callers.add(executor);
        return executor;
    }

    private void awaitIdle() throws InterruptedException {
        awaitCondition(() -> !coordinator.possuiExecucaoEmVoo()
                && coordinator.trabalhoRemanescente() == 0
                && coordinator.consumidoresAguardando() == 0);
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("estado esperado nao foi atingido no prazo de teste");
            }
            Thread.sleep(2);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("latch de teste nao foi liberado");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("trabalho interrompido", failure);
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        try {
            while (true) {
                try {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0 || !latch.await(remaining, TimeUnit.NANOSECONDS)) {
                        throw new AssertionError("produtor sintetico nao foi liberado");
                    }
                    return;
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static ResponseStatusException unavailable(Future<?> result) throws Exception {
        try {
            result.get(1, TimeUnit.SECONDS);
            throw new AssertionError("era esperada resposta indisponivel");
        } catch (ExecutionException failure) {
            assertThat(failure.getCause()).isInstanceOf(ResponseStatusException.class);
            ResponseStatusException status = (ResponseStatusException) failure.getCause();
            assertThat(status.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            return status;
        }
    }
}
