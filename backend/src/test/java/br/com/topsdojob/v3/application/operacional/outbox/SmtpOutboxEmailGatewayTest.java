package br.com.topsdojob.v3.application.operacional.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpOutboxEmailGatewayTest {
  @Test
  void perfilHomologacaoPermiteAllowlistDoCofreComCaptureComoFallback() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application-homologacao.yml"));

    assertThat(yaml.getObject())
        .isNotNull()
        .containsEntry("app.outbox.email.recipient-mode", "${OUTBOX_RECIPIENT_MODE:CAPTURE}");
  }

  @Test
  void preproducaoRedirecionaParaCapturaSemExporDestinatarioReal() throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(sender.createMimeMessage()).thenReturn(mimeMessage);
    OutboxEmailProperties properties = new OutboxEmailProperties(
        true,
        "preproducao",
        "CAPTURE",
        "capture@preprod.invalid",
        "",
        "no-reply@topsdojob.com",
        "Tops do Job",
        "",
        10,
        8,
        30);
    SmtpOutboxEmailGateway gateway = new SmtpOutboxEmailGateway(sender, properties);

    gateway.send(new OutboxEmailMessage(
        UUID.randomUUID(),
        "idempotencia",
        "usuario.importado@example.com",
        "Assunto",
        "Texto",
        "<p>Texto</p>"));

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    MimeMessage sent = captor.getValue();
    assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("capture@preprod.invalid");
    assertThat(sent.getAllRecipients()[0].toString()).doesNotContain("usuario.importado");
    assertThat(sent.getHeader("X-TopsV3-Recipient-Mode", null)).isEqualTo("CAPTURE");
  }

  @Test
  void allowlistEntregaSomenteAoDestinatarioQaAutorizado() throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(sender.createMimeMessage()).thenReturn(mimeMessage);
    OutboxEmailProperties properties = new OutboxEmailProperties(
        true,
        "preproducao",
        "ALLOWLIST",
        "capture@preprod.invalid",
        "qa.autorizado@example.com; outro.qa@example.com",
        "no-reply@topsdojob.com",
        "Tops do Job",
        "",
        10,
        8,
        30);
    SmtpOutboxEmailGateway gateway = new SmtpOutboxEmailGateway(sender, properties);

    gateway.send(new OutboxEmailMessage(
        UUID.randomUUID(),
        "idempotencia-qa",
        "QA.AUTORIZADO@example.com",
        "Assunto",
        "Texto",
        "<p>Texto</p>"));

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    MimeMessage sent = captor.getValue();
    assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("QA.AUTORIZADO@example.com");
    assertThat(sent.getHeader("X-TopsV3-Recipient-Mode", null)).isEqualTo("ALLOWLIST");
  }

  @Test
  void mensagemTransacionalPreservaAssuntoEPartesUtf8() throws Exception {
    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(sender.createMimeMessage()).thenReturn(mimeMessage);
    OutboxEmailProperties properties = new OutboxEmailProperties(
        true,
        "preproducao",
        "ALLOWLIST",
        "capture@preprod.invalid",
        "qa.autorizado@example.com",
        "no-reply@topsdojob.com",
        "Tops do Job",
        "",
        10,
        8,
        30);
    SmtpOutboxEmailGateway gateway = new SmtpOutboxEmailGateway(sender, properties);

    gateway.send(new OutboxEmailMessage(
        UUID.randomUUID(),
        "idempotencia-utf8",
        "qa.autorizado@example.com",
        "Recuperação de senha — Tops do Job",
        "Código, recuperação, redefinição, automático, não, segurança e você.",
        "<p>Código, recuperação, redefinição, automático, não, segurança e você.</p>"));

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(captor.capture());
    MimeMessage sent = captor.getValue();
    sent.saveChanges();

    assertThat(sent.getSubject()).isEqualTo("Recuperação de senha — Tops do Job");
    assertThat(contentTypes(sent.getContent()))
        .anyMatch(type -> type.contains("text/plain") && type.toLowerCase().contains("charset=utf-8"))
        .anyMatch(type -> type.contains("text/html") && type.toLowerCase().contains("charset=utf-8"));
  }

  @Test
  void allowlistBloqueiaDestinatarioNaoAutorizadoAntesDoSmtp() {
    JavaMailSender sender = mock(JavaMailSender.class);
    OutboxEmailProperties properties = new OutboxEmailProperties(
        true,
        "preproducao",
        "ALLOWLIST",
        "capture@preprod.invalid",
        "qa.autorizado@example.com",
        "no-reply@topsdojob.com",
        "Tops do Job",
        "",
        10,
        8,
        30);
    SmtpOutboxEmailGateway gateway = new SmtpOutboxEmailGateway(sender, properties);

    assertThatThrownBy(() -> gateway.send(new OutboxEmailMessage(
        UUID.randomUUID(),
        "idempotencia-importado",
        "usuario.importado@example.com",
        "Assunto",
        "Texto",
        "<p>Texto</p>")))
        .isInstanceOf(OutboxRecipientNotAllowedException.class)
        .hasMessage("destinatario nao autorizado para entrega externa");

    verify(sender, never()).createMimeMessage();
  }

  @Test
  void allowlistExigeAoMenosUmDestinatario() {
    assertThatThrownBy(() -> new OutboxEmailProperties(
        true,
        "preproducao",
        "ALLOWLIST",
        "capture@preprod.invalid",
        " ",
        "no-reply@topsdojob.com",
        "Tops do Job",
        "",
        10,
        8,
        30))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OUTBOX_ALLOWED_RECIPIENTS");
  }

  @Test
  void preproducaoRecusaModoDirectIrrestrito() {
    assertThatThrownBy(() -> new OutboxEmailProperties(
        true,
        "preproducao",
        "DIRECT",
        "capture@preprod.invalid",
        "",
        "no-reply@topsdojob.com",
        "Tops do Job",
        "",
        10,
        8,
        30))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OUTBOX_RECIPIENT_MODE=DIRECT nao e permitido na preproducao");
  }

  private java.util.List<String> contentTypes(Object content) throws Exception {
    java.util.List<String> types = new java.util.ArrayList<>();
    collectContentTypes(content, types);
    return types;
  }

  private void collectContentTypes(Object content, java.util.List<String> types) throws Exception {
    if (!(content instanceof Multipart multipart)) {
      return;
    }
    for (int index = 0; index < multipart.getCount(); index++) {
      BodyPart part = multipart.getBodyPart(index);
      types.add(part.getContentType());
      collectContentTypes(part.getContent(), types);
    }
  }
}
