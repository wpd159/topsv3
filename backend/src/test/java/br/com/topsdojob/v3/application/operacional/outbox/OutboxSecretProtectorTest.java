package br.com.topsdojob.v3.application.operacional.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxSecretProtectorTest {
  private static final String TEST_CIPHER_MATERIAL = "YWFhYWFhYWFhYWFhYWFhYQ==";

  @Test
  void cifraSemPersistirCodigoEmClaroERestauraSomenteComAChave() {
    OutboxSecretProtector protector = new OutboxSecretProtector(TEST_CIPHER_MATERIAL);

    String protectedValue = protector.protect("123456");

    assertThat(protectedValue).startsWith("v1:");
    assertThat(protectedValue).doesNotContain("123456");
    assertThat(protector.reveal(protectedValue)).isEqualTo("123456");
  }

  @Test
  void rejeitaChaveAusenteOuEnvelopeAdulterado() {
    assertThatThrownBy(() -> new OutboxSecretProtector("").protect("123456"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new OutboxSecretProtector(TEST_CIPHER_MATERIAL).reveal("v1:invalido:invalido"))
        .isInstanceOf(IllegalStateException.class);
  }
}
