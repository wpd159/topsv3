package br.com.topsdojob.v3.application.stories;

import static br.com.topsdojob.v3.application.stories.StoryPublicacaoObservabilidade.Fase.ARQUIVO_GET_ORIGEM;
import static br.com.topsdojob.v3.application.stories.StoryPublicacaoObservabilidade.Fase.ARQUIVO_PUT_PRIVADO;
import static br.com.topsdojob.v3.application.stories.StoryPublicacaoObservabilidade.Fase.ARQUIVO_TOTAL;
import static br.com.topsdojob.v3.application.stories.StoryPublicacaoObservabilidade.Fase.PUBLICACAO_SERVICO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.stories.StoryPublicacaoObservabilidade.Captura;
import br.com.topsdojob.v3.application.stories.StoryPublicacaoObservabilidade.Escopo;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class StoryPublicacaoObservabilidadeTest {
  private static final Pattern OPERACAO_ID = Pattern.compile("operacaoId=([0-9a-f-]{36})");
  private static final String FORMATO = "story_publicacao_fase requestId=[A-Za-z0-9._:-]+ "
      + "operacaoId=[0-9a-f-]{36} fase=[A-Z_]+ abrangencia=[A-Z_]+ "
      + "duracaoMs=[0-9]+ resultado=[A-Z_]+";

  private ch.qos.logback.classic.Logger logger;
  private Level nivelAnterior;
  private ListAppender<ILoggingEvent> logs;
  private Map<String, String> mdcAnterior;

  @BeforeEach
  void preparar() {
    mdcAnterior = MDC.getCopyOfContextMap();
    MDC.remove("storyPublicacaoOperacaoId");
    MDC.remove("storyPublicacaoRequestId");
    logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
        StoryPublicacaoObservabilidade.class);
    nivelAnterior = logger.getLevel();
    logger.setLevel(Level.INFO);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void limpar() {
    logger.detachAppender(logs);
    logs.stop();
    logger.setLevel(nivelAnterior);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
    MDC.clear();
    if (mdcAnterior != null) {
      MDC.setContextMap(mdcAnterior);
    }
  }

  @Test
  void fasesAninhadasECapturadasCompartilhamRequestEOperacaoSemExporRetorno() throws Exception {
    String segredo = "conteudo-privado-sintetico";
    String retorno;
    try (Escopo ignored = StoryPublicacaoObservabilidade.abrir("req-story-sintetico")) {
      Captura captura = StoryPublicacaoObservabilidade.capturar();
      retorno = StoryPublicacaoObservabilidade.medir(PUBLICACAO_SERVICO,
          (Supplier<String>) () -> StoryPublicacaoObservabilidade.medir(ARQUIVO_TOTAL,
              (Supplier<String>) () -> StoryPublicacaoObservabilidade.medir(
                  captura, ARQUIVO_GET_ORIGEM,
                  (Supplier<String>) () -> StoryPublicacaoObservabilidade.medir(
                      captura, ARQUIVO_PUT_PRIVADO, (Supplier<String>) () -> segredo))));
    }

    assertThat(retorno).isEqualTo(segredo);
    assertThat(mensagens()).hasSize(4)
        .allSatisfy(mensagem -> assertThat(mensagem)
            .matches(FORMATO)
            .contains("requestId=req-story-sintetico", "resultado=OK")
            .doesNotContain(segredo));
    assertThat(mensagens()).extracting(this::fase)
        .containsExactlyInAnyOrder("PUBLICACAO_SERVICO", "ARQUIVO_TOTAL",
            "ARQUIVO_GET_ORIGEM", "ARQUIVO_PUT_PRIVADO");
    assertThat(mensagens().stream().map(this::operacaoId).collect(java.util.stream.Collectors.toSet()))
        .hasSize(1);
  }

  @Test
  void requestIdArbitrarioEExcecaoNaoAparecemNosLogs() throws Exception {
    IllegalStateException falha = new IllegalStateException(
        "https://example.invalid/arquivo?to" + "ken=CHANGE_ME");

    try (Escopo ignored = StoryPublicacaoObservabilidade.abrir(
        "Bearer CHANGE_ME")) {
      assertThatThrownBy(() -> StoryPublicacaoObservabilidade.medir(
          ARQUIVO_GET_ORIGEM, (Supplier<String>) () -> { throw falha; }))
          .isSameAs(falha);
    }

    assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
        .matches(FORMATO)
        .contains("requestId=AUSENTE", "fase=ARQUIVO_GET_ORIGEM", "resultado=ERRO")
        .doesNotContain("CHANGE_ME", "https://", "to" + "ken=", "Bearer"));
  }

  @Test
  void falhaDoLoggerNaoSubstituiExcecaoOriginal() throws Exception {
    @SuppressWarnings("unchecked")
    Appender<ILoggingEvent> appenderFalho = mock(Appender.class);
    doThrow(new IllegalStateException("falha-sintetica-do-logger"))
        .when(appenderFalho).doAppend(any());
    logger.addAppender(appenderFalho);
    IllegalStateException original = new IllegalStateException("erro-original-sintetico");
    try {
      try (Escopo ignored = StoryPublicacaoObservabilidade.abrir("req-story-logger")) {
        assertThatThrownBy(() -> StoryPublicacaoObservabilidade.medir(
            PUBLICACAO_SERVICO, (Supplier<String>) () -> { throw original; }))
            .isSameAs(original);
      }
      verify(appenderFalho, atLeastOnce()).doAppend(any());
    } finally {
      logger.detachAppender(appenderFalho);
    }
  }

  @Test
  void semEscopoNaoEmiteFaseNemConclusao() {
    String resultado = StoryPublicacaoObservabilidade.medir(
        PUBLICACAO_SERVICO, (Supplier<String>) () -> "ok");
    Captura captura = StoryPublicacaoObservabilidade.capturar();
    String capturado = StoryPublicacaoObservabilidade.medir(
        captura, ARQUIVO_GET_ORIGEM, (Supplier<String>) () -> "ok-capturado");

    assertThat(resultado).isEqualTo("ok");
    assertThat(capturado).isEqualTo("ok-capturado");
    assertThat(mensagens()).isEmpty();
  }

  @Test
  void commitSoApareceDepoisDaConclusaoTransacional() throws Exception {
    afirmarConclusao(TransactionSynchronization.STATUS_COMMITTED, "TRANSACAO_COMMIT");
  }

  @Test
  void rollbackSoApareceDepoisDaConclusaoTransacional() throws Exception {
    afirmarConclusao(TransactionSynchronization.STATUS_ROLLED_BACK, "TRANSACAO_ROLLBACK");
  }

  @Test
  void escopoRestauraContextoMdcAnteriorMesmoComFasesAninhadas() throws Exception {
    MDC.put("requestId", "req-outra-operacao");
    MDC.put("storyPublicacaoOperacaoId", "operacao-anterior");
    MDC.put("storyPublicacaoRequestId", "req-story-anterior");
    Map<String, String> anterior = MDC.getCopyOfContextMap();

    try (Escopo ignored = StoryPublicacaoObservabilidade.abrir("req-story-novo")) {
      assertThat(MDC.get("storyPublicacaoRequestId")).isEqualTo("req-story-novo");
      StoryPublicacaoObservabilidade.medir(PUBLICACAO_SERVICO,
          (Supplier<String>) () -> StoryPublicacaoObservabilidade.medir(
              ARQUIVO_TOTAL, (Supplier<String>) () -> "ok"));
    }

    assertThat(MDC.getCopyOfContextMap()).isEqualTo(anterior);
  }

  private void afirmarConclusao(int status, String esperado) throws Exception {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      try (Escopo ignored = StoryPublicacaoObservabilidade.abrir("req-story-transacao")) {
        StoryPublicacaoObservabilidade.medir(PUBLICACAO_SERVICO, () -> "ok");
      }
      assertThat(mensagens()).noneMatch(mensagem ->
          mensagem.contains("fase=POS_CORPO_TRANSACAO")
              || mensagem.contains("fase=TOTAL_ATE_CONCLUSAO"));

      List<TransactionSynchronization> callbacks =
          TransactionSynchronizationManager.getSynchronizations();
      assertThat(callbacks).hasSize(1);
      callbacks.get(0).afterCompletion(status);

      assertThat(mensagens()).hasSize(3)
          .allSatisfy(mensagem -> assertThat(mensagem)
              .matches(FORMATO)
              .contains("requestId=req-story-transacao"));
      assertThat(mensagens()).extracting(this::fase)
          .containsExactlyInAnyOrder("PUBLICACAO_SERVICO", "POS_CORPO_TRANSACAO",
              "TOTAL_ATE_CONCLUSAO");
      assertThat(mensagens().stream().map(this::operacaoId).collect(java.util.stream.Collectors.toSet()))
          .hasSize(1);
      assertThat(mensagens().stream().filter(mensagem ->
          mensagem.contains("fase=POS_CORPO_TRANSACAO")
              || mensagem.contains("fase=TOTAL_ATE_CONCLUSAO")).toList())
          .allSatisfy(mensagem -> assertThat(mensagem).contains("resultado=" + esperado));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  private List<String> mensagens() {
    return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private String fase(String mensagem) {
    return mensagem.replaceFirst("^.* fase=([A-Z_]+) .*$", "$1");
  }

  private String operacaoId(String mensagem) {
    Matcher matcher = OPERACAO_ID.matcher(mensagem);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }
}
