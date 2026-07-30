package br.com.topsdojob.v3.application.operacional.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.TokenSegurancaEntity;
import br.com.topsdojob.v3.persistence.repository.TokenSegurancaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEmailTemplateServiceTest {
  private static final String TEST_CIPHER_MATERIAL = "YWFhYWFhYWFhYWFhYWFhYQ==";
  private static final String SYNTHETIC_CODE = "246810";

  @Test
  void confirmacaoERecuperacaoUsamLayoutCompartilhadoUtf8ELogoAbsoluta() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    OutboxSecretProtector protector = new OutboxSecretProtector(TEST_CIPHER_MATERIAL);
    OutboxEmailPayloadFactory payloads = new OutboxEmailPayloadFactory(objectMapper, protector);
    UsuarioRepository users = mock(UsuarioRepository.class);
    TokenSegurancaRepository tokens = mock(TokenSegurancaRepository.class);
    UUID userId = UUID.randomUUID();
    UsuarioEntity user = UsuarioEntity.criarCadastroPublico(
        userId,
        "QA",
        "qa@example.invalid",
        "+5562999999999",
        null,
        OffsetDateTime.now(ZoneOffset.UTC));
    when(users.findById(userId)).thenReturn(Optional.of(user));
    TokenSegurancaEntity resetRecord = TokenSegurancaEntity.criar(
        userId,
        "RECUPERACAO_SENHA",
        "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
        OffsetDateTime.now(ZoneOffset.UTC));
    TokenSegurancaEntity confirmationRecord = TokenSegurancaEntity.criar(
        userId,
        "CONFIRMACAO_EMAIL",
        "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
        OffsetDateTime.now(ZoneOffset.UTC));
    when(tokens.findById(resetRecord.getId())).thenReturn(Optional.of(resetRecord));
    when(tokens.findById(confirmationRecord.getId())).thenReturn(Optional.of(confirmationRecord));
    String resetPayload = payloads.auth(
        "AUTH_RECUPERACAO_SENHA_SOLICITADA",
        userId,
        resetRecord.getId(),
        "q***@example.invalid",
        SYNTHETIC_CODE,
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
    String confirmationPayload = payloads.auth(
        "AUTH_CONFIRMACAO_CONTA_SOLICITADA",
        userId,
        confirmationRecord.getId(),
        "q***@example.invalid",
        SYNTHETIC_CODE,
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
    OutboxEventoEntity resetEvent = OutboxEventoEntity.registrarPendente(
        UUID.randomUUID(),
        "USUARIO",
        userId,
        "AUTH_RECUPERACAO_SENHA_SOLICITADA",
        resetPayload,
        "AUTH:" + UUID.randomUUID(),
        OffsetDateTime.now(ZoneOffset.UTC));
    OutboxEventoEntity confirmationEvent = OutboxEventoEntity.registrarPendente(
        UUID.randomUUID(),
        "USUARIO",
        userId,
        "AUTH_CONFIRMACAO_CONTA_SOLICITADA",
        confirmationPayload,
        "AUTH:" + UUID.randomUUID(),
        OffsetDateTime.now(ZoneOffset.UTC));

    OutboxEmailTemplateService service = new OutboxEmailTemplateService(
        objectMapper,
        users,
        tokens,
        protector,
        "https://v3.esle.cloud");
    OutboxEmailMessage reset = service.render(resetEvent);
    OutboxEmailMessage confirmation = service.render(confirmationEvent);

    assertThat(confirmation.subject()).isEqualTo("Confirme sua conta — Tops do Job");
    assertThat(reset.subject()).isEqualTo("Recuperação de senha — Tops do Job");
    assertThat(confirmation.textBody())
        .contains("código", "expira", "Não compartilhe", "https://v3.esle.cloud");
    assertThat(reset.textBody())
        .contains("Redefina sua senha", "código", "Segurança", "você", "automática");
    assertAccountLayout(confirmation);
    assertAccountLayout(reset);
    assertThat(resetPayload).doesNotContain(SYNTHETIC_CODE);
    assertThat(confirmationPayload).doesNotContain(SYNTHETIC_CODE);
  }

  @Test
  void escapaConteudoDinamicoDaModeracao() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    UsuarioRepository users = mock(UsuarioRepository.class);
    TokenSegurancaRepository tokens = mock(TokenSegurancaRepository.class);
    UUID userId = UUID.randomUUID();
    UsuarioEntity user = UsuarioEntity.criarCadastroPublico(
        userId,
        "QA",
        "qa@example.invalid",
        "+5562999999999",
        null,
        OffsetDateTime.now(ZoneOffset.UTC));
    when(users.findById(userId)).thenReturn(Optional.of(user));
    String payload = """
        {"communicationVersion":1,"destinatarioUsuarioId":"%s",
         "anuncioTitulo":"<script>alert(1)</script>",
         "motivoSanitizado":"<img src=x onerror=alert(1)>",
         "linkEdicao":"https://v3.esle.cloud/meus-anuncios/qa/editar"}
        """.formatted(userId);
    OutboxEventoEntity event = OutboxEventoEntity.registrarPendente(
        UUID.randomUUID(),
        "REVISAO_ANUNCIO",
        UUID.randomUUID(),
        "MODERACAO_REPROVADA",
        payload,
        "MODERACAO:" + UUID.randomUUID(),
        OffsetDateTime.now(ZoneOffset.UTC));

    OutboxEmailMessage rendered = new OutboxEmailTemplateService(
        objectMapper,
        users,
        tokens,
        new OutboxSecretProtector(TEST_CIPHER_MATERIAL),
        "https://v3.esle.cloud").render(event);

    assertThat(rendered.htmlBody()).doesNotContain("<script", "<img", "onerror=");
    assertThat(rendered.htmlBody()).contains("alert(1)");
  }

  private void assertAccountLayout(OutboxEmailMessage rendered) {
    assertThat(rendered.recipient()).isEqualTo("qa@example.invalid");
    assertThat(rendered.htmlBody())
        .contains(
            "<meta charset=\"UTF-8\">",
            "data-account-email-layout=\"v1\"",
            "role=\"presentation\"",
            "https://v3.esle.cloud/logo-email.webp",
            "alt=\"Tops do Job\"",
            "https://v3.esle.cloud",
            "Mensagem automática")
        .doesNotContain(
            "+5562",
            "requestId",
            "object key",
            "<script",
            "{{",
            "${",
            "%s");
    assertThat(count(rendered.htmlBody(), SYNTHETIC_CODE)).isEqualTo(1);
    assertThat(count(rendered.htmlBody(), "logo-email.webp")).isEqualTo(1);
  }

  private int count(String content, String value) {
    return (content.length() - content.replace(value, "").length()) / value.length();
  }
}
