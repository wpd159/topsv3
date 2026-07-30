package br.com.topsdojob.v3.application.operacional.outbox;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxEmailProperties {
  public enum RecipientMode {
    CAPTURE,
    ALLOWLIST,
    DIRECT
  }

  private final boolean enabled;
  private final RecipientMode recipientMode;
  private final String captureAddress;
  private final Set<String> allowedRecipients;
  private final String senderAddress;
  private final String senderName;
  private final String replyTo;
  private final int batchSize;
  private final int maxAttempts;
  private final long retryBaseSeconds;

  public OutboxEmailProperties(
      @Value("${app.outbox.email.enabled:false}") boolean enabled,
      @Value("${app.env:nao_configurado}") String appEnv,
      @Value("${app.outbox.email.recipient-mode:CAPTURE}") String recipientMode,
      @Value("${app.outbox.email.capture-address:capture@preprod.invalid}") String captureAddress,
      @Value("${app.outbox.email.allowed-recipients:}") String allowedRecipients,
      @Value("${app.outbox.email.sender-address:no-reply@topsdojob.com}") String senderAddress,
      @Value("${app.outbox.email.sender-name:Tops do Job}") String senderName,
      @Value("${app.outbox.email.reply-to:}") String replyTo,
      @Value("${app.outbox.email.batch-size:10}") int batchSize,
      @Value("${app.outbox.email.max-attempts:8}") int maxAttempts,
      @Value("${app.outbox.email.retry-base-seconds:30}") long retryBaseSeconds) {
    this.enabled = enabled;
    this.recipientMode = parseMode(recipientMode);
    if ("preproducao".equalsIgnoreCase(appEnv)
        && this.recipientMode == RecipientMode.DIRECT) {
      throw new IllegalStateException(
          "OUTBOX_RECIPIENT_MODE=DIRECT nao e permitido na preproducao");
    }
    this.captureAddress = required(captureAddress, "endereco de captura");
    this.allowedRecipients = parseAllowedRecipients(allowedRecipients);
    if (this.recipientMode == RecipientMode.ALLOWLIST && this.allowedRecipients.isEmpty()) {
      throw new IllegalStateException(
          "OUTBOX_ALLOWED_RECIPIENTS deve conter ao menos um endereco no modo ALLOWLIST");
    }
    this.senderAddress = required(senderAddress, "remetente");
    this.senderName = required(senderName, "nome do remetente");
    this.replyTo = replyTo == null ? "" : replyTo.trim();
    this.batchSize = Math.min(Math.max(batchSize, 1), 100);
    this.maxAttempts = Math.min(Math.max(maxAttempts, 1), 20);
    this.retryBaseSeconds = Math.min(Math.max(retryBaseSeconds, 1), 3_600);
  }

  public boolean enabled() {
    return enabled;
  }

  public RecipientMode recipientMode() {
    return recipientMode;
  }

  public String captureAddress() {
    return captureAddress;
  }

  public boolean isRecipientAllowed(String recipient) {
    return allowedRecipients.contains(normalizeRecipient(recipient));
  }

  public String senderAddress() {
    return senderAddress;
  }

  public String senderName() {
    return senderName;
  }

  public String replyTo() {
    return replyTo;
  }

  public int batchSize() {
    return batchSize;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public long retryBaseSeconds() {
    return retryBaseSeconds;
  }

  private RecipientMode parseMode(String value) {
    try {
      return RecipientMode.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "OUTBOX_RECIPIENT_MODE deve ser CAPTURE, ALLOWLIST ou DIRECT",
          exception);
    }
  }

  private Set<String> parseAllowedRecipients(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    Set<String> recipients = Stream.of(value.split("[,;\\s]+"))
        .map(this::normalizeRecipient)
        .filter(recipient -> !recipient.isBlank())
        .collect(Collectors.toUnmodifiableSet());
    if (recipients.stream().anyMatch(recipient -> !recipient.contains("@"))) {
      throw new IllegalStateException("OUTBOX_ALLOWED_RECIPIENTS contem endereco invalido");
    }
    return recipients;
  }

  private String normalizeRecipient(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(label + " da outbox de e-mail nao configurado");
    }
    return value.trim();
  }
}
