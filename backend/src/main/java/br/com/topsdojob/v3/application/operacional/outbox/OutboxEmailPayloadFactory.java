package br.com.topsdojob.v3.application.operacional.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxEmailPayloadFactory {
  public static final int COMMUNICATION_VERSION = 1;

  private final ObjectMapper objectMapper;
  private final OutboxSecretProtector secretProtector;

  public OutboxEmailPayloadFactory(
      ObjectMapper objectMapper,
      OutboxSecretProtector secretProtector) {
    this.objectMapper = objectMapper;
    this.secretProtector = secretProtector;
  }

  public String auth(
      String template,
      UUID recipientUserId,
      UUID securityTokenId,
      String maskedDestination,
      String code,
      OffsetDateTime expiresAt) {
    Map<String, Object> values = base(template, recipientUserId);
    values.put("tokenSegurancaId", securityTokenId);
    values.put("destinoMascarado", maskedDestination);
    values.put("codigoProtegido", secretProtector.protect(code));
    values.put("expiraEm", expiresAt);
    return json(values);
  }

  public String staff(UUID recipientUserId, String role) {
    Map<String, Object> values = base("STAFF_CONVITE_CRIADO", recipientUserId);
    values.put("papel", role);
    return json(values);
  }

  private Map<String, Object> base(String template, UUID recipientUserId) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("communicationVersion", COMMUNICATION_VERSION);
    values.put("template", template);
    values.put("destinatarioUsuarioId", recipientUserId);
    return values;
  }

  private String json(Map<String, Object> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar comunicacao da outbox", exception);
    }
  }
}
