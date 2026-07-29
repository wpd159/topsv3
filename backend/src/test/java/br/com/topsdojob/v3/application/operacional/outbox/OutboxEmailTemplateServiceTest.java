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

  @Test
  void renderizaRecuperacaoSemExporSegredoNoPayloadOuDadosSensiveisNoHtml() {
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
    TokenSegurancaEntity securityRecord = TokenSegurancaEntity.criar(
        userId,
        "RECUPERACAO_SENHA",
        "hash",
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
        OffsetDateTime.now(ZoneOffset.UTC));
    when(tokens.findById(securityRecord.getId())).thenReturn(Optional.of(securityRecord));
    String payload = payloads.auth(
        "AUTH_RECUPERACAO_SENHA_SOLICITADA",
        userId,
        securityRecord.getId(),
        "q***@example.invalid",
        "123456",
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
    OutboxEventoEntity event = OutboxEventoEntity.registrarPendente(
        UUID.randomUUID(),
        "USUARIO",
        userId,
        "AUTH_RECUPERACAO_SENHA_SOLICITADA",
        payload,
        "AUTH:" + UUID.randomUUID(),
        OffsetDateTime.now(ZoneOffset.UTC));

    OutboxEmailMessage rendered = new OutboxEmailTemplateService(
        objectMapper,
        users,
        tokens,
        protector,
        "https://v3.esle.cloud").render(event);

    assertThat(rendered.recipient()).isEqualTo("qa@example.invalid");
    assertThat(rendered.textBody()).contains("123456", "https://v3.esle.cloud");
    assertThat(rendered.htmlBody()).contains("123456", "https://v3.esle.cloud");
    assertThat(rendered.htmlBody()).doesNotContain("+5562", "requestId", "object key", "<script");
    assertThat(payload).doesNotContain("123456");
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
}
