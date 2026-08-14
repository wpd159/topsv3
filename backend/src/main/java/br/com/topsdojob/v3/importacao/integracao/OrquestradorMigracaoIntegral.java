package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
public class OrquestradorMigracaoIntegral {

  private static final String SISTEMA_ORIGEM = "TOPSDOJOB_MIGRACAO_INTEGRAL";
  private static final List<FaseMigracaoIntegral> ORDEM = List.of(
      FaseMigracaoIntegral.REFERENCIAS_LOCALIDADES,
      FaseMigracaoIntegral.USUARIOS_MAPEAMENTOS,
      FaseMigracaoIntegral.CREDENCIAIS,
      FaseMigracaoIntegral.KYC_PLANEJAMENTO,
      FaseMigracaoIntegral.CONTEUDO_SEO,
      FaseMigracaoIntegral.CATALOGO_COMERCIAL,
      FaseMigracaoIntegral.MANIFESTO_MIDIA,
      FaseMigracaoIntegral.KYC_PERSISTENCIA,
      FaseMigracaoIntegral.ANUNCIOS_E_VINCULOS,
      FaseMigracaoIntegral.SEO_ANUNCIOS,
      FaseMigracaoIntegral.FINANCEIRO,
      FaseMigracaoIntegral.FAVORITOS_METRICAS,
      FaseMigracaoIntegral.RECONCILIADORES,
      FaseMigracaoIntegral.FINGERPRINT);

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final ObjectMapper mapper;
  private final ExecutorFasesMigracaoIntegral executor;

  public OrquestradorMigracaoIntegral(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper mapper,
      ExecutorFasesMigracaoIntegral executor) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transacao = new TransactionTemplate(transactionManager);
    this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.executor = executor;
  }

  public Relatorio executar(PacoteMigracaoIntegral pacote, ModoMigracaoIntegral modo, int lote) {
    Objects.requireNonNull(pacote, "pacote obrigatorio");
    Objects.requireNonNull(modo, "modo obrigatorio");
    if (lote < 1 || lote > 10_000) {
      throw new IllegalArgumentException("tamanho de lote fora do intervalo permitido");
    }
    if (modo == ModoMigracaoIntegral.DRY_RUN) {
      return transacao.execute(status -> {
        Relatorio relatorio = executarInterno(pacote, modo, lote);
        status.setRollbackOnly();
        return relatorio;
      });
    }
    return executarInterno(pacote, modo, lote);
  }

  public static List<FaseMigracaoIntegral> ordemCanonica() {
    return ORDEM;
  }

  private Relatorio executarInterno(
      PacoteMigracaoIntegral pacote,
      ModoMigracaoIntegral modo,
      int lote) {
    UUID execucaoId = IdsMigracaoIntegral.uuid("execucao-migracao-integral", pacote.pacoteId());
    String fingerprintPacote = fingerprintPacote(pacote);
    EstadoExecucao existente = buscar(execucaoId);
    boolean retomada = existente != null;
    if (retomada && !fingerprintPacote.equals(existente.fingerprintPacote())) {
      throw new IllegalStateException("pacote diverge da execucao de migracao existente");
    }
    if (!retomada) {
      validarDestinoLimpo();
      criarExecucao(pacote, execucaoId, fingerprintPacote);
      existente = new EstadoExecucao(
          fingerprintPacote,
          "EM_EXECUCAO",
          EnumSet.noneOf(FaseMigracaoIntegral.class),
          Map.of(),
          null);
    } else if (modo == ModoMigracaoIntegral.DRY_RUN) {
      throw new IllegalStateException("dry-run exige destino limpo e execucao nova");
    }

    MetricasMigracaoIntegral metricas = MetricasMigracaoIntegral.retomar(existente.metricas());
    Map<FaseMigracaoIntegral, ExecutorFasesMigracaoIntegral.ResultadoEtapa> resultados =
        new LinkedHashMap<>(existente.resultados());
    EnumSet<FaseMigracaoIntegral> concluidas = EnumSet.copyOf(existente.fasesConcluidas());
    if (concluidas.containsAll(ORDEM) && existente.status().startsWith("CONCLUIDA")) {
      return new Relatorio(
          execucaoId,
          modo,
          existente.status(),
          true,
          List.copyOf(concluidas),
          Map.copyOf(resultados),
          metricas.relatorio(),
          pendenciasAbertas(execucaoId));
    }
    ExecutorFasesMigracaoIntegral.Contexto contexto = new ExecutorFasesMigracaoIntegral.Contexto(
        pacote, execucaoId, fingerprintPacote, modo, lote, retomada, metricas);
    try {
      for (FaseMigracaoIntegral fase : ORDEM) {
        interromperSeSolicitado();
        if (concluidas.contains(fase)) {
          continue;
        }
        ExecutorFasesMigracaoIntegral.ResultadoEtapa resultado = executor.executar(fase, contexto);
        resultados.put(fase, resultado);
        concluidas.add(fase);
        atualizarCheckpoint(
            execucaoId, fingerprintPacote, concluidas, resultados, metricas, null);
      }
      long pendencias = pendenciasAbertas(execucaoId);
      String status = pendencias == 0 ? "CONCLUIDA" : "CONCLUIDA_COM_PENDENCIAS";
      atualizarCheckpoint(
          execucaoId, fingerprintPacote, concluidas, resultados, metricas, status);
      return new Relatorio(
          execucaoId,
          modo,
          status,
          retomada,
          List.copyOf(concluidas),
          Map.copyOf(resultados),
          metricas.relatorio(),
          pendencias);
    } catch (ExecucaoInterrompidaException exception) {
      atualizarCheckpoint(
          execucaoId, fingerprintPacote, concluidas, resultados, metricas, "EM_EXECUCAO");
      throw exception;
    } catch (RuntimeException exception) {
      atualizarCheckpoint(
          execucaoId, fingerprintPacote, concluidas, resultados, metricas, "FALHA");
      throw exception;
    }
  }

  private void validarDestinoLimpo() {
    long execucoes = contar("importacao_execucao");
    long usuarios = contar("usuario");
    long anuncios = contar("anuncio");
    long pagamentos = contar("pagamento");
    long documentos = contar("documento_usuario");
    long movimentos = contar("movimento_credito");
    if (execucoes + usuarios + anuncios + pagamentos + documentos + movimentos > 0) {
      throw new IllegalStateException(
          "destino nao esta vazio e nao pertence a mesma execucao de migracao");
    }
  }

  private void criarExecucao(
      PacoteMigracaoIntegral pacote,
      UUID execucaoId,
      String fingerprintPacote) {
    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("pacoteFingerprint", fingerprintPacote);
    resumo.put("pacoteVersao", pacote.versao());
    resumo.put("fasesConcluidas", List.of());
    jdbc.update(
        """
        INSERT INTO importacao_execucao (
          id, sistema_origem, status, iniciado_em, resumo_json, criado_em
        ) VALUES (?, ?, 'EM_EXECUCAO', ?, CAST(? AS jsonb), ?)
        """,
        execucaoId,
        SISTEMA_ORIGEM,
        pacote.base().capturadoEm(),
        json(resumo),
        pacote.base().capturadoEm());
  }

  private EstadoExecucao buscar(UUID execucaoId) {
    return jdbc.query(
        """
        SELECT status, resumo_json::text FROM importacao_execucao
         WHERE id = ? AND sistema_origem = ?
        """,
        (rs, rowNum) -> estado(rs.getString("status"), rs.getString("resumo_json")),
        execucaoId,
        SISTEMA_ORIGEM).stream().findFirst().orElse(null);
  }

  private EstadoExecucao estado(String status, String json) {
    try {
      Map<String, Object> resumo = mapper.readValue(json, new TypeReference<>() { });
      String fingerprint = Objects.toString(resumo.get("pacoteFingerprint"), null);
      EnumSet<FaseMigracaoIntegral> fases = EnumSet.noneOf(FaseMigracaoIntegral.class);
      Object valores = resumo.get("fasesConcluidas");
      if (valores instanceof List<?> lista) {
        lista.stream().map(Object::toString).map(FaseMigracaoIntegral::valueOf).forEach(fases::add);
      }
      Map<FaseMigracaoIntegral, ExecutorFasesMigracaoIntegral.ResultadoEtapa> resultados =
          new LinkedHashMap<>();
      if (resumo.get("resultados") instanceof Map<?, ?> valoresResultados) {
        valoresResultados.forEach((chave, valor) -> resultados.put(
            FaseMigracaoIntegral.valueOf(chave.toString()),
            mapper.convertValue(
                valor, ExecutorFasesMigracaoIntegral.ResultadoEtapa.class)));
      }
      MetricasMigracaoIntegral.Checkpoint metricas = resumo.get("metricas") == null
          ? null
          : mapper.convertValue(
              resumo.get("metricas"), MetricasMigracaoIntegral.Checkpoint.class);
      return new EstadoExecucao(fingerprint, status, fases, Map.copyOf(resultados), metricas);
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalStateException("checkpoint integral invalido", exception);
    }
  }

  private void atualizarCheckpoint(
      UUID execucaoId,
      String fingerprintPacote,
      Set<FaseMigracaoIntegral> concluidas,
      Map<FaseMigracaoIntegral, ExecutorFasesMigracaoIntegral.ResultadoEtapa> resultados,
      MetricasMigracaoIntegral metricas,
      String statusFinal) {
    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("pacoteFingerprint", fingerprintPacote);
    resumo.put("fasesConcluidas", concluidas.stream().map(Enum::name).sorted().toList());
    resumo.put("resultados", resultados);
    resumo.put("metricas", metricas.checkpoint());
    String status = statusFinal == null ? "EM_EXECUCAO" : statusFinal;
    OffsetDateTime finalizadoEm = status.startsWith("CONCLUIDA")
        ? OffsetDateTime.now()
        : null;
    jdbc.update(
        """
        UPDATE importacao_execucao
           SET status = ?, finalizado_em = ?, resumo_json = CAST(? AS jsonb)
         WHERE id = ?
        """,
        status,
        finalizadoEm,
        json(resumo),
        execucaoId);
  }

  private long pendenciasAbertas(UUID execucaoId) {
    Long total = jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_pendencia
         WHERE execucao_id = ? AND status IN ('ABERTA', 'EM_REVISAO')
        """,
        Long.class,
        execucaoId);
    return total == null ? 0 : total;
  }

  private long contar(String tabela) {
    if (!Set.of(
        "importacao_execucao", "usuario", "anuncio", "pagamento",
        "documento_usuario", "movimento_credito").contains(tabela)) {
      throw new IllegalArgumentException("tabela fora do preflight");
    }
    Long total = jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
    return total == null ? 0 : total;
  }

  private String fingerprintPacote(PacoteMigracaoIntegral pacote) {
    try {
      return FingerprintMigracaoIntegral.sha256(mapper.writeValueAsBytes(pacote));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("pacote integral nao pode ser serializado", exception);
    }
  }

  private String json(Object valor) {
    try {
      return mapper.writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("checkpoint integral nao pode ser serializado", exception);
    }
  }

  private void interromperSeSolicitado() {
    if (Thread.currentThread().isInterrupted()) {
      throw new ExecucaoInterrompidaException();
    }
  }

  private record EstadoExecucao(
      String fingerprintPacote,
      String status,
      EnumSet<FaseMigracaoIntegral> fasesConcluidas,
      Map<FaseMigracaoIntegral, ExecutorFasesMigracaoIntegral.ResultadoEtapa> resultados,
      MetricasMigracaoIntegral.Checkpoint metricas) {
  }

  public record Relatorio(
      UUID execucaoId,
      ModoMigracaoIntegral modo,
      String status,
      boolean retomada,
      List<FaseMigracaoIntegral> fasesConcluidas,
      Map<FaseMigracaoIntegral, ExecutorFasesMigracaoIntegral.ResultadoEtapa> resultados,
      MetricasMigracaoIntegral.Relatorio metricas,
      long pendenciasAbertas) {
  }

  public static final class ExecucaoInterrompidaException extends RuntimeException {
    public ExecucaoInterrompidaException() {
      super("execucao de migracao interrompida com checkpoint preservado");
    }
  }
}
