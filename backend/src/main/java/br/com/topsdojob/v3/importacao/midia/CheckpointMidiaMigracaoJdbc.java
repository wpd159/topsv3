package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class CheckpointMidiaMigracaoJdbc implements CheckpointMidiaMigracao {

  private static final String SOURCE_SYSTEM = "LEGADO_MIDIAS_FASE_5";
  private static final String SOURCE_TABLE = "manifesto_midia";

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper objectMapper;

  public CheckpointMidiaMigracaoJdbc(
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<Registro> buscar(UUID executionId, String itemFingerprint) {
    return jdbc.query(
        """
        SELECT hash_origem, payload_normalizado_json::text
          FROM stg_midia
         WHERE id = ?
           AND execucao_id = ?
           AND status = 'PROCESSADO'
        """,
        (rs, rowNumber) -> registro(rs),
        id(executionId, itemFingerprint),
        executionId).stream().findFirst();
  }

  @Override
  public void registrarConcluido(
      UUID executionId,
      Item item,
      String checksum,
      long size,
      String mimeType) {
    transactions.executeWithoutResult(status -> {
      String itemFingerprint = item.fingerprint();
      String payload = payload(item, checksum, size, mimeType);
      UUID stagingId = id(executionId, itemFingerprint);
      UUID entityId = uuid(item.entidadeV3Id());
      OffsetDateTime now = OffsetDateTime.now();
      jdbc.update(
          """
          INSERT INTO stg_midia (
            id, execucao_id, sistema_origem, tabela_origem, id_origem,
            hash_origem, payload_normalizado_json, status, pendencia_codigo,
            entidade_v3_id, criado_em, processado_em
          ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PROCESSADO', NULL, ?, ?, ?)
          ON CONFLICT (id) DO UPDATE SET
            hash_origem = EXCLUDED.hash_origem,
            payload_normalizado_json = EXCLUDED.payload_normalizado_json,
            status = 'PROCESSADO',
            pendencia_codigo = NULL,
            entidade_v3_id = EXCLUDED.entidade_v3_id,
            processado_em = EXCLUDED.processado_em
          WHERE stg_midia.execucao_id = EXCLUDED.execucao_id
          """,
          stagingId,
          executionId,
          SOURCE_SYSTEM,
          SOURCE_TABLE,
          item.idOrigem(),
          itemFingerprint,
          payload,
          entityId,
          now,
          now);
      registrarMapeamento(executionId, item, itemFingerprint, entityId, now);
    });
  }

  private void registrarMapeamento(
      UUID executionId,
      Item item,
      String fingerprint,
      UUID entityId,
      OffsetDateTime now) {
    jdbc.update(
        """
        INSERT INTO importacao_mapeamento (
          id, execucao_id, sistema_origem, tabela_origem, id_origem,
          hash_origem, entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, 'MIDIA', ?, 'MAPEADO', ?, ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem)
        DO UPDATE SET
          hash_origem = EXCLUDED.hash_origem,
          entidade_v3_id = EXCLUDED.entidade_v3_id,
          status = 'MAPEADO',
          atualizado_em = EXCLUDED.atualizado_em
        """,
        id(executionId, "map:" + item.idOrigem()),
        executionId,
        SOURCE_SYSTEM,
        SOURCE_TABLE,
        item.idOrigem(),
        fingerprint,
        entityId,
        now,
        now);
  }

  private Registro registro(ResultSet resultSet) throws SQLException {
    try {
      Map<String, Object> payload = objectMapper.readValue(
          resultSet.getString("payload_normalizado_json"),
          new TypeReference<>() { });
      return new Registro(
          resultSet.getString("hash_origem"),
          text(payload, "destinoFingerprint"),
          text(payload, "checksum"),
          number(payload, "tamanhoBytes"),
          text(payload, "mimeType"));
    } catch (JsonProcessingException exception) {
      throw new SQLException("checkpoint de midia invalido", exception);
    }
  }

  private String payload(Item item, String checksum, long size, String mimeType) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("destinoFingerprint", destinoFingerprint(item));
    values.put("checksum", checksum);
    values.put("tamanhoBytes", size);
    values.put("mimeType", mimeType);
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar checkpoint sanitizado", exception);
    }
  }

  static String destinoFingerprint(Item item) {
    return MidiaMigracaoHashes.sha256(item.destino().representacaoCanonica());
  }

  private static UUID id(UUID executionId, String value) {
    return UUID.nameUUIDFromBytes(
        (executionId + ":" + value).getBytes(StandardCharsets.UTF_8));
  }

  private static UUID uuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String text(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value == null ? null : value.toString();
  }

  private static long number(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
  }
}
