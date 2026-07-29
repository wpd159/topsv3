package br.com.topsdojob.v3.application.operacional.outbox;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpOutboxEmailGateway implements OutboxEmailGateway {
  private final JavaMailSender mailSender;
  private final OutboxEmailProperties properties;

  public SmtpOutboxEmailGateway(
      JavaMailSender mailSender,
      OutboxEmailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(OutboxEmailMessage message) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(
          mimeMessage,
          true,
          StandardCharsets.UTF_8.name());
      helper.setFrom(new InternetAddress(
          properties.senderAddress(),
          properties.senderName(),
          StandardCharsets.UTF_8.name()));
      helper.setTo(destination(message.recipient()));
      if (!properties.replyTo().isBlank()) {
        helper.setReplyTo(properties.replyTo());
      }
      helper.setSubject(message.subject());
      helper.setText(message.textBody(), message.htmlBody());
      mimeMessage.setHeader("Message-ID", "<" + message.outboxId() + "@outbox.topsdojob>");
      mimeMessage.setHeader("X-TopsV3-Outbox-Id", message.outboxId().toString());
      mimeMessage.setHeader("X-TopsV3-Recipient-Mode", properties.recipientMode().name());
      mailSender.send(mimeMessage);
    } catch (MessagingException | java.io.UnsupportedEncodingException exception) {
      throw new IllegalStateException("provedor de e-mail indisponivel", exception);
    }
  }

  private String destination(String recipient) {
    if (properties.recipientMode() == OutboxEmailProperties.RecipientMode.CAPTURE) {
      return properties.captureAddress();
    }
    return recipient;
  }
}
