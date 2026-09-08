package br.com.topsdojob.v3.application.publico.service;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Orcamento de uma carga de localidades, compartilhado entre todas as suas fases. */
public final class LocalidadesConsultaOrcamento {

    private static final ThreadLocal<LocalidadesConsultaOrcamento> ATUAL = new ThreadLocal<>();
    private static final Logger LOG = LoggerFactory.getLogger(LocalidadesConsultaOrcamento.class);
    private static final String CONFIGURAR_TIMEOUTS = """
            select set_config('statement_timeout', ?, true),
                   set_config('lock_timeout', ?, true),
                   set_config('transaction_timeout', ?, true)
            """;

    private final LongSupplier relogio;
    private final long prazoNanos;
    private final String operacaoId = UUID.randomUUID().toString();
    private final Object conexaoMonitor = new Object();
    private ProtecaoConexao conexaoAtiva;
    private volatile boolean interrompido;

    public LocalidadesConsultaOrcamento(Duration duracao) {
        this(duracao, System::nanoTime);
    }

    LocalidadesConsultaOrcamento(Duration duracao, LongSupplier relogio) {
        Objects.requireNonNull(duracao, "duracao");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        if (duracao.isZero() || duracao.isNegative()) {
            throw new IllegalArgumentException("orcamento de localidades deve ser positivo");
        }
        this.prazoNanos = relogio.getAsLong() + duracao.toNanos();
    }

    public static LocalidadesConsultaOrcamento atualOuNulo() {
        return ATUAL.get();
    }

    public String operacaoId() { return operacaoId; }

    public <T> T medir(String fase, Supplier<T> trabalho) {
        conferir();
        long inicio = relogio.getAsLong();
        try {
            T resultado = trabalho.get();
            conferir();
            return resultado;
        } finally {
            registrar(fase, inicio);
        }
    }

    private void registrar(String fase, long inicio) {
        LOG.info("localidades operacao={} fase={} duracaoMs={}", operacaoId, fase,
                TimeUnit.NANOSECONDS.toMillis(relogio.getAsLong() - inicio));
    }

    public void instalar() {
        ATUAL.set(this);
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public void conferir() {
        restanteNanos();
    }

    public boolean expirou() {
        return prazoNanos - relogio.getAsLong() <= 0;
    }

    /** Aborta somente a conexao fisica ainda pertencente a este orcamento. */
    public void interromperConexao() {
        interrompido = true;
        synchronized (conexaoMonitor) {
            if (conexaoAtiva != null) conexaoAtiva.interromper();
        }
    }

    public long restanteMillis() {
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(restanteNanos()));
    }

    public <T> T executar(Supplier<T> trabalho) {
        Objects.requireNonNull(trabalho, "trabalho");
        LocalidadesConsultaOrcamento anterior = ATUAL.get();
        instalar();
        try {
            conferir();
            T resultado = trabalho.get();
            conferir();
            return resultado;
        } finally {
            if (anterior == null) {
                limpar();
            } else {
                ATUAL.set(anterior);
            }
        }
    }

    /**
     * Executada somente pelo worker de localidades. O coordenador interrompe a espera por
     * conexao no prazo e conserva a carga em andamento ate este metodo terminar de verdade.
     */
    public <T> T transacao(
            PlatformTransactionManager manager,
            EntityManager entityManager,
            Supplier<T> trabalho) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(entityManager, "entityManager");
        Objects.requireNonNull(trabalho, "trabalho");
        conferir();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("carga de localidades nao pode herdar transacao");
        }

        TransactionTemplate transacao = new TransactionTemplate(manager);
        transacao.setReadOnly(true);
        transacao.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transacao.setTimeout(segundosArredondados(restanteMillis()));

        long inicioAquisicao = relogio.getAsLong();
        long[] inicioRetencao = {-1};
        ProtecaoConexao protecao = new ProtecaoConexao();
        try {
            return executar(() -> {
                T resultado = transacao.execute(status -> {
                    inicioRetencao[0] = relogio.getAsLong();
                    registrar("aquisicao_e_inicio_transacao", inicioAquisicao);
                    // A aquisicao pertence ao mesmo prazo; nunca executar SQL apos obtencao tardia.
                    conferir();
                    Session session = entityManager.unwrap(Session.class);
                    session.doWork(protecao::configurar);
                    conferir();
                    T valor = trabalho.get();
                    conferir();
                    return valor;
                });
                protecao.conferirRestauracao();
                return resultado;
            });
        } catch (RuntimeException | Error original) {
            protecao.preservarFalhaOriginal(original);
            throw original;
        } finally {
            if (inicioRetencao[0] != -1) registrar("retencao_ate_fim_transacao", inicioRetencao[0]);
            else registrar("aquisicao_sem_transacao", inicioAquisicao);
        }
    }

    private long restanteNanos() {
        long restante = prazoNanos - relogio.getAsLong();
        if (interrompido || Thread.currentThread().isInterrupted() || restante <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "consulta de localidades excedeu o prazo");
        }
        return restante;
    }

    private static int segundosArredondados(long millis) {
        return (int) Math.min(Integer.MAX_VALUE, (millis + 999L) / 1000L);
    }

    private final class ProtecaoConexao {
        private Connection connection;
        private Connection fisica;
        private int timeoutRedeAnterior;
        private boolean redeAlterada;
        private boolean abortada;
        private IllegalStateException falhaRestauracao;

        void configurar(Connection atual) throws SQLException {
            conferir();
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("transacao de localidades exige sincronizacao de cleanup");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

                @Override
                public void afterCompletion(int status) { restaurarAposConclusao(); }
            });
            synchronized (conexaoMonitor) {
                conferir();
                if (conexaoAtiva != null) {
                    throw new IllegalStateException("orcamento de localidades ja possui conexao");
                }
                // Retain the physical handle before any mutation. Closing only a pool
                // proxy could return a contaminated session instead of discarding it.
                fisica = Objects.requireNonNull(atual.unwrap(Connection.class), "conexao fisica");
                connection = atual;
                conexaoAtiva = this;
                timeoutRedeAnterior = atual.getNetworkTimeout();
                int restante = (int) Math.min(Integer.MAX_VALUE, restanteMillis());
                // A JDBC setter may alter state and then fail: cleanup already owns it.
                redeAlterada = true;
                atual.setNetworkTimeout(Runnable::run, restante);
            }
            conferir();
            try (PreparedStatement statement = atual.prepareStatement(CONFIGURAR_TIMEOUTS)) {
                long restanteSql = restanteMillis();
                statement.setQueryTimeout(segundosArredondados(restanteSql));
                String limite = restanteSql + "ms";
                statement.setString(1, limite);
                statement.setString(2, limite);
                statement.setString(3, limite);
                statement.execute();
            }
        }

        private void restaurarAposConclusao() {
            synchronized (conexaoMonitor) {
                try {
                    // PostgreSQL 17 or the watchdog may close the physical session.
                    // Keep the scoped timeout until actual commit/rollback completes.
                    if (redeAlterada && !abortada && !connection.isClosed()) {
                        connection.setNetworkTimeout(Runnable::run, timeoutRedeAnterior);
                    }
                } catch (SQLException | RuntimeException exception) {
                    falhaRestauracao = new IllegalStateException(
                            "falha ao restaurar timeout da conexao de localidades", exception);
                    LOG.error("localidades operacao={} fase=restauracao_rede resultado=FALHA", operacaoId);
                    descartarFisica();
                } finally {
                    // The same monitor serializes abort and release. Once this reference
                    // is removed, a late watchdog cannot touch a pool-reused connection.
                    if (conexaoAtiva == this) conexaoAtiva = null;
                    connection = null;
                    fisica = null;
                }
            }
            // Spring invokes afterCompletion after commit/rollback, before its resource
            // cleanup returns the connection. SET LOCAL also lasts through completion.
        }

        private void interromper() {
            if (abortada) return;
            abortada = true;
            descartarFisica();
        }

        private void descartarFisica() {
            try {
                fisica.abort(Runnable::run);
                if (!fisica.isClosed()) fisica.close();
                LOG.warn("localidades operacao={} fase=descarte_conexao resultado=ABORTADA", operacaoId);
            } catch (SQLException | RuntimeException abortError) {
                if (falhaRestauracao == null) {
                    falhaRestauracao = new IllegalStateException(
                            "falha ao interromper conexao fisica de localidades");
                }
                falhaRestauracao.addSuppressed(new IllegalStateException(
                        "falha ao abortar conexao fisica de localidades", abortError));
                try {
                    fisica.close();
                    LOG.warn("localidades operacao={} fase=descarte_conexao resultado=FECHADA", operacaoId);
                } catch (SQLException | RuntimeException closeError) {
                    falhaRestauracao.addSuppressed(new IllegalStateException(
                            "falha ao fechar conexao fisica de localidades", closeError));
                    LOG.error("localidades operacao={} fase=descarte_conexao resultado=FALHA", operacaoId);
                }
            }
        }

        private void conferirRestauracao() {
            if (falhaRestauracao != null) throw falhaRestauracao;
        }

        private void preservarFalhaOriginal(Throwable original) {
            if (falhaRestauracao != null && falhaRestauracao != original) {
                original.addSuppressed(falhaRestauracao);
            }
        }
    }
}
