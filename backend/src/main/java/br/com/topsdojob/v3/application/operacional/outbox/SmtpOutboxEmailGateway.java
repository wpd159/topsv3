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
    String destination = destination(message.recipient());
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
      helper.setTo(destination);
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
    return switch (properties.recipientMode()) {
      case CAPTURE -> properties.captureAddress();
      case DIRECT -> recipient;
      case ALLOWLIST -> {
        if (!properties.isRecipientAllowed(recipient)) {
          throw new OutboxRecipientNotAllowedException();
        }
        yield recipient;
      }
    };
  }
}
