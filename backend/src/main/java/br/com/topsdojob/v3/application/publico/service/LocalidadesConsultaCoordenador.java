package br.com.topsdojob.v3.application.publico.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Shares work, never completed/stale results. Callers must not own a transaction. */
@Component
public class LocalidadesConsultaCoordenador implements AutoCloseable {
    public static final Duration PRAZO = Duration.ofMillis(3_500);
    private static final int MAX_CONSUMIDORES = 128;
    private static final Logger LOG = LoggerFactory.getLogger(LocalidadesConsultaCoordenador.class);
    private final PlatformTransactionManager transactions;
    private final EntityManager entityManager;
    private final DataSource dataSource;
    private final Duration prazo;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "localidades-produtor");
                thread.setDaemon(true);
                return thread;
            });
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "localidades-prazo");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong produtores = new AtomicLong();
    private final AtomicInteger remanescentes = new AtomicInteger();
    private final ThreadLocal<Execucao<?>> atual = new ThreadLocal<>();
    private Execucao<?> emVoo;

    @Autowired
    public LocalidadesConsultaCoordenador(PlatformTransactionManager transactions,
            EntityManager entityManager, DataSource dataSource) {
        this(transactions, entityManager, dataSource, PRAZO);
    }

    LocalidadesConsultaCoordenador(PlatformTransactionManager transactions,
            EntityManager entityManager, DataSource dataSource, Duration prazo) {
        this.transactions = transactions;
        this.entityManager = entityManager;
        this.dataSource = dataSource;
        this.prazo = prazo;
    }

    public <T> T executar(String chave, Supplier<T> consulta) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw indisponivel("consulta de localidades dentro de transacao externa");
        }
        Execucao<T> execucao;
        synchronized (this) {
            if (emVoo == null) {
                execucao = new Execucao<>(chave, new LocalidadesConsultaOrcamento(prazo));
                emVoo = execucao;
                try {
                    worker.execute(() -> produzir(execucao, consulta));
                } catch (RuntimeException exception) {
                    if (emVoo == execucao) emVoo = null;
                    throw indisponivel("execucao de localidades indisponivel");
                }
            } else {
                if (!emVoo.chave.equals(chave)) throw indisponivel("consulta de localidades ocupada");
                @SuppressWarnings("unchecked") Execucao<T> compartilhada = (Execucao<T>) emVoo;
                execucao = compartilhada;
            }
            execucao.orcamento.conferir();
            if (execucao.consumidores.get() >= MAX_CONSUMIDORES) {
                throw indisponivel("limite de consumidores de localidades");
            }
            execucao.consumidores.incrementAndGet();
        }
        try {
            return execucao.resultado.get(execucao.orcamento.restanteMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            // A consumer owns neither the producer nor its cancellation token.
            throw indisponivel("consumidor de localidades interrompido");
        } catch (TimeoutException exception) {
            throw indisponivel("prazo de localidades excedido");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof ResponseStatusException status) throw status;
            throw indisponivel("consulta de localidades indisponivel");
        } finally {
            execucao.consumidores.decrementAndGet();
        }
    }

    private <T> void produzir(Execucao<T> execucao, Supplier<T> consulta) {
        T resposta = null;
        Throwable falha = null;
        ScheduledFuture<?> alarme = null;
        remanescentes.incrementAndGet();
        produtores.incrementAndGet();
        atual.set(execucao);
        long inicio = System.nanoTime();
        try {
            synchronized (execucao) {
                execucao.thread = Thread.currentThread();
                alarme = timer.schedule(() -> {
                    synchronized (execucao) {
                        if (execucao.thread != null) {
                            execucao.thread.interrupt();
                            execucao.orcamento.interromperConexao();
                        }
                    }
                }, execucao.orcamento.restanteMillis(), TimeUnit.MILLISECONDS);
            }
            resposta = execucao.orcamento.executar(() -> {
                T value = consulta.get();
                execucao.orcamento.conferir();
                return value;
            });
        } catch (Throwable exception) {
            falha = exception;
        } finally {
            // This runs after the supplier's transactions and remote calls actually returned.
            synchronized (execucao) {
                execucao.thread = null;
                if (alarme != null) alarme.cancel(false);
            }
            Thread.interrupted();
            remanescentes.decrementAndGet();
            registrar("termino_" + (falha == null ? "sucesso" : "erro"), inicio);
            atual.remove();
            synchronized (this) {
                if (emVoo == execucao) emVoo = null;
            }
        }
        if (falha == null) execucao.resultado.complete(resposta);
        else execucao.resultado.completeExceptionally(falha);
    }

    public <T> T transacao(String fase, Supplier<T> consulta) {
        return medir(fase, () -> LocalidadesConsultaOrcamento.atualOuNulo()
                .transacao(transactions, entityManager, consulta));
    }

    public <T> T medir(String fase, Supplier<T> consulta) {
        LocalidadesConsultaOrcamento orcamento = LocalidadesConsultaOrcamento.atualOuNulo();
        orcamento.conferir();
        long inicio = System.nanoTime();
        try {
            T result = consulta.get();
            orcamento.conferir();
            return result;
        } finally {
            registrar(fase, inicio);
        }
    }

    private void registrar(String fase, long inicio) {
        Execucao<?> execucao = atual.get();
        var pool = dataSource instanceof HikariDataSource hikari ? hikari.getHikariPoolMXBean() : null;
        LOG.info("localidades operacao={} fase={} duracaoMs={} produtores={} consumidores={} trabalhoRestante={} poolAtivo={} poolOcioso={} poolEspera={}",
                execucao == null ? "nenhuma" : execucao.id, fase,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio), produtores.get(),
                execucao == null ? 0 : execucao.consumidores.get(), remanescentes.get(),
                pool == null ? -1 : pool.getActiveConnections(), pool == null ? -1 : pool.getIdleConnections(),
                pool == null ? -1 : pool.getThreadsAwaitingConnection());
    }

    public long produtoresIniciados() { return produtores.get(); }
    public int trabalhoRemanescente() { return remanescentes.get(); }
    public synchronized int consumidoresAguardando() { return emVoo == null ? 0 : emVoo.consumidores.get(); }
    public synchronized boolean possuiExecucaoEmVoo() { return emVoo != null; }
    public synchronized boolean possuiExecucaoExpirada() {
        return emVoo != null && emVoo.orcamento.expirou();
    }

    @Override
    @PreDestroy
    public void close() {
        worker.shutdownNow();
        timer.shutdownNow();
    }

    private static ResponseStatusException indisponivel(String motivo) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, motivo);
    }

    private static final class Execucao<T> {
        final String id;
        final String chave;
        final LocalidadesConsultaOrcamento orcamento;
        final CompletableFuture<T> resultado = new CompletableFuture<>();
        final AtomicInteger consumidores = new AtomicInteger();
        Thread thread;
        Execucao(String chave, LocalidadesConsultaOrcamento orcamento) {
            this.chave = chave;
            this.orcamento = orcamento;
            this.id = orcamento.operacaoId();
        }
    }
}
