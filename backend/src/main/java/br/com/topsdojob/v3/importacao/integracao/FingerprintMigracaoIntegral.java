package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSetMetaData;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

public final class FingerprintMigracaoIntegral {

  private static final Pattern IDENTIFICADOR = Pattern.compile("^[a-z][a-z0-9_]*$");
  private static final Set<String> TABELAS_EXCLUIDAS = Set.of(
      "flyway_schema_history",
      "importacao_execucao",
      "sessao_usuario",
      "token_seguranca",
      "outbox_evento",
      "outbox_email",
      "auditoria_evento");
  private static final Set<String> COLUNAS_TECNICAS_IMPORTACAO = Set.of(
      "id", "execucao_id", "criado_em", "atualizado_em", "processado_em",
      "iniciado_em", "finalizado_em", "resolvido_em");
  private static final Map<String, Set<String>> COLUNAS_TECNICAS_SEEDS = Map.of(
      "beneficio_premium", Set.of("criado_em", "atualizado_em"),
      "beneficio_premium_opcao", Set.of("criado_em", "atualizado_em"),
      "plano_credito", Set.of("criado_em", "atualizado_em"),
      "permissao", Set.of("criado_em"),
      "papel_permissao", Set.of("criado_em"));

  private final ObjectMapper mapper;

  public FingerprintMigracaoIntegral(ObjectMapper mapper) {
    this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  public String calcular(JdbcTemplate jdbc) {
    return calcular(fotografia(jdbc));
  }

  Map<String, String> calcularPorTabela(JdbcTemplate jdbc) {
    Map<String, String> resultado = new LinkedHashMap<>();
    fotografia(jdbc).forEach((tabela, linhas) ->
        resultado.put(tabela, calcular(Map.of(tabela, linhas))));
    return Map.copyOf(resultado);
  }

  private Map<String, List<Map<String, Object>>> fotografia(JdbcTemplate jdbc) {
    List<String> tabelas = jdbc.queryForList(
        """
        SELECT table_name
          FROM information_schema.tables
         WHERE table_schema = 'public'
           AND table_type = 'BASE TABLE'
         ORDER BY table_name
        """,
        String.class).stream()
        .filter(this::incluirTabela)
        .toList();
    Map<String, List<Map<String, Object>>> fotografia = new LinkedHashMap<>();
    for (String tabela : tabelas) {
      fotografia.put(tabela, linhas(jdbc, tabela));
    }
    return fotografia;
  }

  String calcular(Map<String, List<Map<String, Object>>> fotografia) {
    try {
      return sha256(mapper.writeValueAsBytes(canonicalizar(fotografia)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao calcular fingerprint sanitizado", exception);
    }
  }

  Map<String, List<Map<String, Object>>> canonicalizar(
      Map<String, List<Map<String, Object>>> fotografia) {
    Map<String, List<Map<String, Object>>> resultado = new LinkedHashMap<>();
    fotografia.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> {
          List<Map<String, Object>> linhas = entry.getValue().stream()
              .map(linha -> canonicalizarLinha(entry.getKey(), linha))
              .sorted(Comparator.comparing(this::json))
              .toList();
          resultado.put(entry.getKey(), linhas);
        });
    return resultado;
  }

  private List<Map<String, Object>> linhas(JdbcTemplate jdbc, String tabela) {
    if (!IDENTIFICADOR.matcher(tabela).matches()) {
      throw new IllegalArgumentException("tabela fora da politica de fingerprint");
    }
    return jdbc.query("SELECT * FROM " + tabela, resultSet -> {
      List<Map<String, Object>> linhas = new ArrayList<>();
      ResultSetMetaData metadata = resultSet.getMetaData();
      while (resultSet.next()) {
        Map<String, Object> linha = new LinkedHashMap<>();
        for (int indice = 1; indice <= metadata.getColumnCount(); indice++) {
          linha.put(metadata.getColumnLabel(indice), resultSet.getObject(indice));
        }
        linhas.add(linha);
      }
      return linhas;
    });
  }

  private Map<String, Object> canonicalizarLinha(String tabela, Map<String, Object> linha) {
    Map<String, Object> resultado = new LinkedHashMap<>();
    linha.entrySet().stream()
        .filter(entry -> incluirColuna(tabela, entry.getKey()))
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> resultado.put(entry.getKey(), valor(entry.getValue())));
    return resultado;
  }

  private Object valor(Object valor) {
    if (valor == null || valor instanceof Number || valor instanceof Boolean) {
      return valor;
    }
    if (valor instanceof byte[] bytes) {
      return sha256(bytes);
    }
    if (valor instanceof TemporalAccessor) {
      return valor.toString();
    }
    if ("org.postgresql.util.PGobject".equals(valor.getClass().getName())
        && (valor.toString().startsWith("{") || valor.toString().startsWith("["))) {
      try {
        JsonNode node = mapper.readTree(valor.toString());
        return mapper.convertValue(node, Object.class);
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("jsonb invalido no fingerprint", exception);
      }
    }
    if (valor.getClass().isArray()) {
      return Base64.getEncoder().encodeToString(valor.toString().getBytes(StandardCharsets.UTF_8));
    }
    return valor.toString();
  }

  private boolean incluirTabela(String tabela) {
    return !TABELAS_EXCLUIDAS.contains(tabela) && !tabela.startsWith("stg_");
  }

  private boolean incluirColuna(String tabela, String coluna) {
    if (tabela.startsWith("importacao_")) {
      return !COLUNAS_TECNICAS_IMPORTACAO.contains(coluna);
    }
    return !COLUNAS_TECNICAS_SEEDS.getOrDefault(tabela, Set.of()).contains(coluna);
  }

  private String json(Map<String, Object> valor) {
    try {
      return mapper.writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao ordenar fingerprint", exception);
    }
  }

  static String sha256(byte[] conteudo) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo))
          .toLowerCase(Locale.ROOT);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
