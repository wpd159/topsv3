package br.com.topsdojob.v3.application.stories;

import br.com.topsdojob.v3.platform.request.RequestIdFilter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Best-effort timings for the administrative Story publication only. */
public final class StoryPublicacaoObservabilidade {
  private static final Logger LOG = LoggerFactory.getLogger(StoryPublicacaoObservabilidade.class);
  private static final String OPERACAO_MDC = "storyPublicacaoOperacaoId";
  private static final String REQUEST_MDC = "storyPublicacaoRequestId";

  private StoryPublicacaoObservabilidade() {
  }

  public enum Fase {
    PUBLICACAO_SERVICO(true),
    CONTEXTO_PROPRIETARIO_SQL(false),
    CONTEXTO_USUARIO_FOR_UPDATE_SQL(false),
    CONTEXTO_ANUNCIO_FOR_UPDATE_SQL(false),
    IDEMPOTENCIA_HASH(false),
    IDEMPOTENCIA_SQL(false),
    STORY_ATIVO_FOR_UPDATE_SQL(false),
    DIREITO_TOTAL(true),
    DIREITO_CATALOGO_TOTAL(true),
    DIREITO_SQL_LEITURA(false),
    DIREITO_REPOSITORIO_SAVE(false),
    DIREITO_VIGENCIA_TOTAL(false),
    PERSISTENCIA_SAVE_FLUSH(false),
    ARQUIVO_TOTAL(true),
    CONSULTA_RESULTADO_TOTAL(false),
    ARQUIVO_FLUSH_SQL(false),
    ARQUIVO_SQL_LEITURA(false),
    ARQUIVO_SQL_FOR_UPDATE(false),
    ARQUIVO_SQL_ESCRITA(false),
    ARQUIVO_PREPARACAO_JSON_HASH(false),
    ARQUIVO_COPIAS_TOTAL(true),
    ARQUIVO_GET_ORIGEM(false),
    ARQUIVO_HASH_ORIGEM(false),
    ARQUIVO_REUSO_TOTAL(true),
    ARQUIVO_GET_REUSO(false),
    ARQUIVO_HASH_REUSO(false),
    ARQUIVO_PUT_PRIVADO(false),
    ARQUIVO_GET_CONFIRMACAO(false),
    ARQUIVO_HASH_CONFIRMACAO(false),
    ARQUIVO_DELETE_ROLLBACK(false),
    POS_CORPO_TRANSACAO(false),
    TOTAL_ATE_CONCLUSAO(true);

    private final boolean inclusiva;

    Fase(boolean inclusiva) {
      this.inclusiva = inclusiva;
    }
  }

  public static Escopo abrir(String requestId) {
    String anteriorOperacao = null;
    String anteriorRequest = null;
    boolean anterioresLidos = false;
    try {
      anteriorOperacao = MDC.get(OPERACAO_MDC);
      anteriorRequest = MDC.get(REQUEST_MDC);
      anterioresLidos = true;
      String requestSeguro = RequestIdFilter.isValidRequestId(requestId) ? requestId.trim() : "AUSENTE";
      Captura captura = new Captura(requestSeguro, UUID.randomUUID().toString());
      MDC.put(OPERACAO_MDC, captura.operacaoId);
      MDC.put(REQUEST_MDC, captura.requestId);
      return new Escopo(captura, anteriorOperacao, anteriorRequest, System.nanoTime());
    } catch (RuntimeException ignored) {
      // Observability is optional and must not block publication.
      if (anterioresLidos) {
        restaurarSemFalha(OPERACAO_MDC, anteriorOperacao);
        restaurarSemFalha(REQUEST_MDC, anteriorRequest);
      }
      return new Escopo(null, null, null, 0);
    }
  }

  public static Captura capturar() {
    try {
      String operacaoId = MDC.get(OPERACAO_MDC);
      String requestId = MDC.get(REQUEST_MDC);
      if (operacaoId == null || requestId == null) {
        return null;
      }
      UUID.fromString(operacaoId);
      return new Captura(RequestIdFilter.isValidRequestId(requestId) ? requestId : "AUSENTE", operacaoId);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  public static <T> T medir(Fase fase, Supplier<T> acao) {
    return medir(capturar(), fase, acao);
  }

  public static void medir(Fase fase, Runnable acao) {
    medir(capturar(), fase, acao);
  }

  public static <T> T medir(Captura captura, Fase fase, Supplier<T> acao) {
    if (captura == null) {
      return acao.get();
    }
    long inicio = System.nanoTime();
    try {
      T resultado = acao.get();
      registrar(captura, fase, inicio, "OK");
      return resultado;
    } catch (RuntimeException | Error exception) {
      registrar(captura, fase, inicio, "ERRO");
      throw exception;
    }
  }

  public static void medir(Captura captura, Fase fase, Runnable acao) {
    medir(captura, fase, () -> {
      acao.run();
      return null;
    });
  }

  private static void registrar(Captura captura, Fase fase, long inicio, String resultado) {
    try {
      LOG.info("story_publicacao_fase requestId={} operacaoId={} fase={} abrangencia={} duracaoMs={} resultado={}",
          captura.requestId, captura.operacaoId, fase.name(),
          fase.inclusiva ? "INCLUSIVA" : "SEGMENTO",
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio), resultado);
    } catch (RuntimeException ignored) {
      // Diagnostic logging must not replace the publication/transaction outcome.
    }
  }

  public static final class Captura {
    private final String requestId;
    private final String operacaoId;

    private Captura(String requestId, String operacaoId) {
      this.requestId = requestId;
      this.operacaoId = operacaoId;
    }
  }

  public static final class Escopo implements AutoCloseable {
    private final Captura captura;
    private final String anteriorOperacao;
    private final String anteriorRequest;
    private final long inicio;
    private boolean fechado;

    private Escopo(Captura captura, String anteriorOperacao, String anteriorRequest, long inicio) {
      this.captura = captura;
      this.anteriorOperacao = anteriorOperacao;
      this.anteriorRequest = anteriorRequest;
      this.inicio = inicio;
    }

    @Override
    public void close() {
      if (fechado) {
        return;
      }
      fechado = true;
      if (captura == null) {
        return;
      }
      long fimCorpo = System.nanoTime();
      try {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
          TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              String resultado = switch (status) {
                case STATUS_COMMITTED -> "TRANSACAO_COMMIT";
                case STATUS_ROLLED_BACK -> "TRANSACAO_ROLLBACK";
                default -> "TRANSACAO_INDETERMINADA";
              };
              registrar(captura, Fase.POS_CORPO_TRANSACAO, fimCorpo, resultado);
              registrar(captura, Fase.TOTAL_ATE_CONCLUSAO, inicio, resultado);
            }
          });
        }
      } catch (RuntimeException ignored) {
        // A failed observer cannot alter commit or rollback.
      } finally {
        restaurarSemFalha(OPERACAO_MDC, anteriorOperacao);
        restaurarSemFalha(REQUEST_MDC, anteriorRequest);
      }
    }
  }

  private static void restaurar(String chave, String anterior) {
    if (anterior == null) {
      MDC.remove(chave);
    } else {
      MDC.put(chave, anterior);
    }
  }

  private static void restaurarSemFalha(String chave, String anterior) {
    try {
      restaurar(chave, anterior);
    } catch (RuntimeException ignored) {
      // An MDC failure cannot replace the original publication exception.
    }
  }
}
