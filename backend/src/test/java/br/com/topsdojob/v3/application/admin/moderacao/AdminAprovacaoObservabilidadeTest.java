package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAprovarAnuncioRequestDto;
import br.com.topsdojob.v3.application.admin.premium.BeneficioFotosExtrasModeracaoService;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminAprovacaoObservabilidadeTest {
  private final ArquivoPublicidadeRegistroService arquivo = mock(ArquivoPublicidadeRegistroService.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private AdminModeracaoAcaoService service;
  private ch.qos.logback.classic.Logger logger;
  private ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void preparar() {
    service = new AdminModeracaoAcaoService(
        mock(RevisaoAnuncioRepository.class), anuncioRepository,
        mock(UsuarioRepository.class), mock(AnuncioBloqueioJuridicoRepository.class),
        mock(AnuncioMidiaRepository.class), mock(ArquivoMidiaRepository.class),
        mock(DocumentoUsuarioRepository.class), mock(DecisaoModeracaoRepository.class),
        mock(AuditoriaEventoRepository.class), mock(OutboxEventoRepository.class),
        new ObjectMapper(), mock(MidiaStorageAprovacaoService.class),
        mock(BeneficioFotosExtrasModeracaoService.class), mock(FotoElegivelAnuncioPolicy.class),
        arquivo, "https://example.invalid");
    logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AdminModeracaoAcaoService.class);
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
    MDC.clear();
  }

  @Test
  void commitERollbackSaoRegistradosSomenteDepoisDoCallbackTransacional() {
    UUID operacao = UUID.randomUUID();
    observarConclusao(operacao, TransactionSynchronization.STATUS_COMMITTED);
    observarConclusao(operacao, TransactionSynchronization.STATUS_ROLLED_BACK);

    assertThat(mensagens()).hasSize(2)
        .allSatisfy(mensagem -> assertThat(mensagem)
            .contains("operacaoId=" + operacao, "fase=CONCLUSAO_TRANSACAO")
            .matches(".*duracaoMs=[0-9]+.*")
            .doesNotContain("cpf", "token", "cookie", "requestBody"));
    assertThat(mensagens().get(0)).contains("resultado=TRANSACAO_COMMIT");
    assertThat(mensagens().get(1)).contains("resultado=TRANSACAO_ROLLBACK");
  }

  @Test
  void semTransacaoOuSemOperacaoNaoAnunciaCommit() {
    ReflectionTestUtils.invokeMethod(service, "observarConclusaoTransacional", UUID.randomUUID(), System.nanoTime());
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    ReflectionTestUtils.invokeMethod(service, "observarConclusaoTransacional", null, System.nanoTime());
    assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    assertThat(mensagens()).isEmpty();
  }

  @Test
  void arquivoRecebeOperacaoSomenteNoEscopoDaChamadaELogNaoExpoeErro() {
    UUID anuncio = UUID.randomUUID(), operacao = UUID.randomUUID();
    OffsetDateTime instante = OffsetDateTime.parse("2026-09-20T12:00:00Z");
    String contextoAnterior = "outra-operacao";
    MDC.put("aprovacaoOperacaoId", contextoAnterior);

    ReflectionTestUtils.invokeMethod(service, "registrarArquivoAprovacao",
        anuncio, "MOTIVO_SINTETICO", "cpf-12345678900", instante, operacao);
    verify(arquivo).registrarEstado(anuncio, "MOTIVO_SINTETICO", "cpf-12345678900", instante);
    assertThat(MDC.get("aprovacaoOperacaoId")).isEqualTo(contextoAnterior);

    IllegalStateException falha = new IllegalStateException("token-secreto-exemplo");
    doThrow(falha).when(arquivo)
        .registrarEstado(anuncio, "FALHA_SINTETICA", "cpf-12345678900", instante);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "registrarArquivoAprovacao",
        anuncio, "FALHA_SINTETICA", "cpf-12345678900", instante, operacao))
        .isSameAs(falha);
    assertThat(MDC.get("aprovacaoOperacaoId")).isEqualTo(contextoAnterior);
    assertThat(mensagens()).hasSize(2)
        .allSatisfy(mensagem -> assertThat(mensagem)
            .contains("operacaoId=" + operacao, "fase=ARQUIVO_TOTAL")
            .doesNotContain("cpf-12345678900", "token-secreto-exemplo", "MOTIVO_SINTETICO"));
    assertThat(mensagens().get(0)).contains("resultado=OK");
    assertThat(mensagens().get(1)).contains("resultado=ERRO");
  }

  @Test
  void falhaNaConsultaDeContextoRegistraFaseSqlSanitizada() {
    UUID anuncio = UUID.randomUUID(), operacao = UUID.randomUUID();
    IllegalStateException falha = new IllegalStateException("nome-privado-sintetico");
    when(anuncioRepository.findUsuarioIdById(anuncio)).thenThrow(falha);
    AdminUserPrincipal ator = new AdminUserPrincipal(UUID.randomUUID(), "Admin sintetico",
        "admin@example.invalid", "n/a", List.of(PapelUsuario.ADMIN), List.of(), List.of(), true);

    assertThatThrownBy(() -> service.aprovarEPublicarAnuncio(anuncio, ator, "request",
        new AdminAprovarAnuncioRequestDto(operacao, 7, null))).isSameAs(falha);
    assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
        .contains("operacaoId=" + operacao, "fase=CONTEXTO_LOCK_SQL", "resultado=ERRO")
        .doesNotContain("nome-privado-sintetico", "admin@example.invalid"));
  }

  @Test
  void arquivoNaoDeixaOperacaoNoMdcQuandoNaoHaviaContextoAnterior() {
    UUID operacao = UUID.randomUUID();
    assertThat(MDC.get("aprovacaoOperacaoId")).isNull();
    ReflectionTestUtils.invokeMethod(service, "registrarArquivoAprovacao",
        UUID.randomUUID(), "MOTIVO_SINTETICO", "request", OffsetDateTime.parse("2026-09-20T12:00:00Z"),
        operacao);
    assertThat(MDC.get("aprovacaoOperacaoId")).isNull();
  }

  private void observarConclusao(UUID operacao, int resultado) {
    int antes = mensagens().size();
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      ReflectionTestUtils.invokeMethod(service, "observarConclusaoTransacional", operacao, System.nanoTime());
      assertThat(mensagens()).hasSize(antes);
      List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertThat(callbacks).hasSize(1);
      callbacks.get(0).afterCompletion(resultado);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  private List<String> mensagens() {
    return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
