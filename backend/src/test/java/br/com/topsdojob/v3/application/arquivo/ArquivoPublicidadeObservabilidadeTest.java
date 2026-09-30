package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageException;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ArquivoPublicidadeObservabilidadeTest {
  private final EntityManager entityManager = mock(EntityManager.class);
  private ArquivoPublicidadeRegistroService service;
  private ch.qos.logback.classic.Logger logger;
  private ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void preparar() {
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> storage = mock(ObjectProvider.class);
    service = new ArquivoPublicidadeRegistroService(
        mock(NamedParameterJdbcTemplate.class), entityManager, new ObjectMapper(),
        storage, new R2StorageProperties(), mock(ArquivoPublicidadeStoryRegistroService.class),
        mock(PremiumPublicoMapper.class), mock(ArquivoPublicidadeTransicaoTemporalService.class));
    logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ArquivoPublicidadeRegistroService.class);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void limpar() {
    logger.detachAppender(logs);
    logs.stop();
    MDC.remove("aprovacaoOperacaoId");
  }

  @Test
  void sucessoRegistraSomenteFaseDuracaoOperacaoEResultado() {
    UUID operacao = UUID.randomUUID();
    MDC.put("aprovacaoOperacaoId", operacao.toString());
    Supplier<String> acao = () -> "conteudo-privado-sintetico";

    String retorno = ReflectionTestUtils.invokeMethod(service, "medirFase", "ARQUIVO_GET_ORIGEM", acao);

    assertThat(retorno).isEqualTo("conteudo-privado-sintetico");
    assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
        .contains("operacaoId=" + operacao, "fase=ARQUIVO_GET_ORIGEM", "resultado=OK", "statusR2=NA")
        .matches(".*duracaoMs=[0-9]+.*")
        .doesNotContain("conteudo-privado-sintetico"));
  }

  @Test
  void excecaoR2PreservaInstanciaEIncluiApenasStatusRemotoPermitido() {
    UUID operacao = UUID.randomUUID();
    MDC.put("aprovacaoOperacaoId", operacao.toString());
    R2StorageException falha = new R2StorageException("R2 GET retornou HTTP 503");
    Supplier<String> acao = () -> { throw falha; };

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "medirFase", "ARQUIVO_GET_ORIGEM", acao))
        .isSameAs(falha);
    assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
        .contains("operacaoId=" + operacao, "fase=ARQUIVO_GET_ORIGEM", "resultado=ERRO", "statusR2=503")
        .doesNotContain("R2 GET retornou HTTP", "chave", "bucket", "token"));
  }

  @Test
  void mensagemNaoPermitidaNaoVazaUrlOuSegredoEStatusFicaIndisponivel() {
    MDC.put("aprovacaoOperacaoId", UUID.randomUUID().toString());
    String urlSintetica = "https://example.invalid/path?x=CHANGE_ME";
    R2StorageException falha = new R2StorageException("R2 GET retornou HTTP 503 " + urlSintetica);
    Supplier<String> acao = () -> { throw falha; };

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "medirFase", "ARQUIVO_GET_ORIGEM", acao))
        .isSameAs(falha);
    assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
        .contains("resultado=ERRO", "statusR2=NA")
        .doesNotContain(urlSintetica, "HTTP 503"));
  }

  @Test
  void semOperacaoCorrelacionadaNaoEmiteLogAdicional() {
    Supplier<String> acao = () -> "ok";
    assertThat((String) ReflectionTestUtils.invokeMethod(service, "medirFase", "ARQUIVO_GET_ORIGEM", acao))
        .isEqualTo("ok");
    assertThat(mensagens()).isEmpty();
  }

  @Test
  void erroNoFlushTambemRegistraFaseSqlSemExporExcecao() {
    UUID operacao = UUID.randomUUID();
    MDC.put("aprovacaoOperacaoId", operacao.toString());
    IllegalStateException falha = new IllegalStateException("conteudo-sensivel-sintetico");
    doThrow(falha).when(entityManager).flush();

    assertThatThrownBy(() -> service.registrarEstado(UUID.randomUUID(), "MOTIVO", "request",
        OffsetDateTime.parse("2026-09-20T12:00:00Z"))).isSameAs(falha);
    assertThat(mensagens()).singleElement().satisfies(mensagem -> assertThat(mensagem)
        .contains("operacaoId=" + operacao, "fase=ARQUIVO_LOCK_SQL", "resultado=ERRO")
        .doesNotContain("conteudo-sensivel-sintetico"));
  }

  private List<String> mensagens() {
    return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
