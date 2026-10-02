package br.com.topsdojob.v3.application.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminPremiumObservabilidadeTest {
    private AdminPremiumOperacaoService service;
    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void preparar() {
        service = new AdminPremiumOperacaoService(
                mock(AtivacaoBeneficioRepository.class), mock(MovimentoCreditoRepository.class),
                mock(AdminCreditoOperacaoService.class), mock(BeneficioPremiumRepository.class),
                mock(BeneficioPremiumOpcaoRepository.class), mock(GrupoAtivacaoBeneficioRepository.class),
                mock(AnuncioRepository.class), mock(UsuarioRepository.class),
                mock(AnuncioBloqueioJuridicoRepository.class), mock(BeneficioAnuncioConsultaService.class),
                mock(ArquivoPublicidadeRegistroService.class));
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AdminPremiumOperacaoService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(logs);
        logs.stop();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void fasesUsamIdTecnicoEMedicaoMonotonicaSemDadosDaExcecao() {
        UUID operacaoId = UUID.randomUUID();
        String requestId = "req-premium-sintetico";
        String resultado = ReflectionTestUtils.invokeMethod(service, "observarFase",
                requestId, operacaoId, "SINGLE", "CONTEXTO_LOCK_SQL", (Supplier<String>) () -> "OK");
        assertThat(resultado).isEqualTo("OK");
        assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
                .contains("requestId=" + requestId, "operacaoId=" + operacaoId,
                        "modalidade=SINGLE", "fase=CONTEXTO_LOCK_SQL", "resultado=OK")
                .matches(".*duracaoMs=[0-9]+.*"));

        IllegalStateException falha = new IllegalStateException("token-sintetico-nao-registrar");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "observarFase",
                requestId, operacaoId, "LOTE", "ARQUIVO_TOTAL", (Supplier<String>) () -> {
                    throw falha;
                })).isSameAs(falha);
        assertThat(mensagens().get(1)).contains("modalidade=LOTE", "fase=ARQUIVO_TOTAL", "resultado=ERRO")
                .doesNotContain("token-sintetico-nao-registrar");
    }

    @Test
    void conclusaoSomenteDepoisDoCallbackDistingueCommitERollback() {
        UUID operacaoId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        ReflectionTestUtils.invokeMethod(service, "observarConclusaoTransacional",
                "req-premium-sintetico", operacaoId, "SINGLE", System.nanoTime());
        assertThat(mensagens()).isEmpty();
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertThat(callbacks).hasSize(1);
        callbacks.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
                .contains("operacaoId=" + operacaoId, "modalidade=SINGLE",
                        "fase=CONCLUSAO_TRANSACAO", "resultado=TRANSACAO_COMMIT")
                .matches(".*duracaoMs=[0-9]+.*"));

        callbacks.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(mensagens().get(1)).contains("resultado=TRANSACAO_ROLLBACK");
    }

    @Test
    void semTransacaoOuRequestIdInvalidoNaoDeclaraCommitNemExibeEntradaArbitraria() {
        ReflectionTestUtils.invokeMethod(service, "observarConclusaoTransacional",
                "req-premium-sintetico", UUID.randomUUID(), "LOTE", System.nanoTime());
        assertThat(mensagens()).isEmpty();
        ReflectionTestUtils.invokeMethod(service, "observarFase",
                "Bearer segredo sintetico", UUID.randomUUID(), "LOTE", "CATALOGO",
                (Supplier<String>) () -> "OK");
        assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
                .contains("requestId=AUSENTE", "modalidade=LOTE")
                .doesNotContain("segredo sintetico"));
    }

    private List<String> mensagens() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
