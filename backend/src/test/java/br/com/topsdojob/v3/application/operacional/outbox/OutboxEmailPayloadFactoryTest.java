package br.com.topsdojob.v3.application.operacional.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEmailPayloadFactoryTest {
  private static final String TEST_CIPHER_MATERIAL = "YWFhYWFhYWFhYWFhYWFhYQ==";

  @Test
  void payloadDeAutenticacaoEVersionadoENaoContemCodigoBruto() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    OutboxEmailPayloadFactory factory = new OutboxEmailPayloadFactory(
        objectMapper,
        new OutboxSecretProtector(TEST_CIPHER_MATERIAL));
    UUID userId = UUID.randomUUID();
    UUID tokenId = UUID.randomUUID();

    String payload = factory.auth(
        "AUTH_RECUPERACAO_SENHA_SOLICITADA",
        userId,
        tokenId,
        "q***@example.invalid",
        "123456",
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));

    JsonNode json = objectMapper.readTree(payload);
    assertThat(json.path("communicationVersion").asInt()).isEqualTo(1);
    assertThat(json.path("destinatarioUsuarioId").asText()).isEqualTo(userId.toString());
    assertThat(json.path("tokenSegurancaId").asText()).isEqualTo(tokenId.toString());
    assertThat(json.path("codigoProtegido").asText()).startsWith("v1:");
    assertThat(payload).doesNotContain("123456");
  }
}
