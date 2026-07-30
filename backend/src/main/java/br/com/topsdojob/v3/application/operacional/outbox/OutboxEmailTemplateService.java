package br.com.topsdojob.v3.application.operacional.outbox;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.TokenSegurancaEntity;
import br.com.topsdojob.v3.persistence.repository.TokenSegurancaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxEmailTemplateService {
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final ObjectMapper objectMapper;
  private final UsuarioRepository users;
  private final TokenSegurancaRepository securityTokens;
  private final OutboxSecretProtector secretProtector;
  private final String canonicalDomain;
  private final AccountEmailLayout accountEmailLayout;

  public OutboxEmailTemplateService(
      ObjectMapper objectMapper,
      UsuarioRepository users,
      TokenSegurancaRepository securityTokens,
      OutboxSecretProtector secretProtector,
      @Value("${app.canonical-domain}") String canonicalDomain) {
    this.objectMapper = objectMapper;
    this.users = users;
    this.securityTokens = securityTokens;
    this.secretProtector = secretProtector;
    this.canonicalDomain = canonicalDomain(canonicalDomain);
    this.accountEmailLayout = new AccountEmailLayout(this.canonicalDomain);
  }

  public OutboxEmailMessage render(OutboxEventoEntity outbox) {
    JsonNode payload = payload(outbox);
    if (payload.path("communicationVersion").asInt(0) != OutboxEmailPayloadFactory.COMMUNICATION_VERSION) {
      throw new IllegalStateException("evento legado nao elegivel para entrega");
    }
    UsuarioEntity recipient = recipient(payload);
    String email = recipient.getEmailNormalizado();
    if (email == null || !EMAIL.matcher(email).matches()) {
      throw new IllegalStateException("destinatario sem e-mail valido");
    }
    Content content = switch (outbox.getTipoEvento()) {
      case "AUTH_CONFIRMACAO_CONTA_SOLICITADA", "AUTH_CONFIRMACAO_CONTA_REENVIADA" -> {
        requireActiveToken(payload, "CONFIRMACAO_EMAIL");
        yield confirmation(payload);
      }
      case "AUTH_RECUPERACAO_SENHA_SOLICITADA" -> {
        requireActiveToken(payload, "RECUPERACAO_SENHA");
        yield passwordRecovery(payload);
      }
      case "MODERACAO_REPROVADA" -> moderationRejected(payload);
      case "MODERACAO_SOLICITAR_AJUSTE" -> moderationAdjustment(payload);
      case "STAFF_CONVITE_CRIADO" -> staffInvite(payload);
      default -> throw new IllegalStateException("template de entrega nao suportado");
    };
    return new OutboxEmailMessage(
        outbox.getId(),
        outbox.getIdempotencyKey(),
        email,
        content.subject(),
        content.text(),
        content.html());
  }

  private Content confirmation(JsonNode payload) {
    String code = protectedCode(payload);
    String minutes = expiryMinutes(payload);
    AccountEmailLayout.Rendered rendered = accountEmailLayout.render(
        "Confirme sua conta — Tops do Job",
        "Confirme sua conta",
        "Use o código de seis dígitos abaixo para concluir seu cadastro com segurança.",
        code,
        "O código expira em " + minutes + " minutos.",
        "Acessar a Tops do Job",
        canonicalDomain,
        "Não compartilhe este código com ninguém.",
        "Se você não solicitou este cadastro, ignore esta mensagem.");
    return new Content(rendered.subject(), rendered.text(), rendered.html());
  }

  private Content passwordRecovery(JsonNode payload) {
    String code = protectedCode(payload);
    String minutes = expiryMinutes(payload);
    AccountEmailLayout.Rendered rendered = accountEmailLayout.render(
        "Recuperação de senha — Tops do Job",
        "Redefina sua senha",
        "Use o código de seis dígitos abaixo no fluxo Esqueci minha senha.",
        code,
        "O código expira em " + minutes + " minutos.",
        "Redefinir minha senha",
        canonicalDomain,
        "Nunca compartilhe este código ou sua senha.",
        "Se você não reconhece esta solicitação, ignore esta mensagem.");
    return new Content(rendered.subject(), rendered.text(), rendered.html());
  }

  private Content moderationRejected(JsonNode payload) {
    String title = text(payload, "anuncioTitulo", "seu anuncio", 120);
    String reason = text(payload, "motivoSanitizado", "Consulte os detalhes no painel.", 2_000);
    String link = safeLink(payload.path("linkEdicao").asText(""));
    return content(
        "Seu anuncio precisa de alteracoes",
        "A analise de " + title + " encontrou pendencias. Motivo: " + reason
            + ". Corrija e reenvie pelo painel: " + link,
        "Seu anuncio precisa de alteracoes",
        "A analise de " + title + " encontrou pendencias.",
        null,
        "Motivo: " + reason,
        link);
  }

  private Content moderationAdjustment(JsonNode payload) {
    String title = text(payload, "anuncioTitulo", "seu anuncio", 120);
    String reason = text(payload, "motivoSanitizado", "Consulte os detalhes no painel.", 2_000);
    String link = safeLink(payload.path("linkEdicao").asText(""));
    return content(
        "Ajustes solicitados no seu anuncio",
        "Foram solicitados ajustes em " + title + ". Motivo: " + reason
            + ". Edite o anuncio pelo painel: " + link,
        "Ajustes solicitados",
        "Foram solicitados ajustes em " + title + ".",
        null,
        "Motivo: " + reason,
        link);
  }

  private Content staffInvite(JsonNode payload) {
    String role = text(payload, "papel", "equipe administrativa", 40);
    return content(
        "Seu acesso administrativo foi criado",
        "Seu acesso de " + role + " foi criado na Tops do Job. Abra " + canonicalDomain
            + " e use Esqueci minha senha para definir sua credencial. Nenhuma senha foi enviada por e-mail.",
        "Acesso administrativo criado",
        "Seu acesso de " + role + " foi criado.",
        null,
        "Use Esqueci minha senha para definir sua credencial com seguranca.",
        canonicalDomain);
  }

  private Content content(
      String subject,
      String text,
      String heading,
      String lead,
      String code,
      String note,
      String link) {
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html><html lang=\"pt-BR\"><body style=\"margin:0;background:#f4f4f5;color:#18181b;font-family:Arial,sans-serif\">")
        .append("<main style=\"max-width:560px;margin:0 auto;padding:24px\">")
        .append("<section style=\"background:#fff;border:1px solid #e4e4e7;padding:24px\">")
        .append("<h1 style=\"font-size:22px;margin:0 0 16px\">").append(escape(heading)).append("</h1>")
        .append("<p style=\"font-size:16px;line-height:1.5\">").append(escape(lead)).append("</p>");
    if (code != null) {
      html.append("<p style=\"font-size:28px;font-weight:700;letter-spacing:4px;text-align:center\">")
          .append(escape(code)).append("</p>");
    }
    html.append("<p style=\"font-size:15px;line-height:1.5\">").append(escape(note)).append("</p>")
        .append("<p><a href=\"").append(escape(link))
        .append("\" style=\"color:#c80072;font-weight:700\">Acessar a Tops do Job</a></p>")
        .append("<p style=\"font-size:12px;color:#71717a\">Mensagem automatica. Nao envie documentos, senhas ou dados pessoais por resposta.</p>")
        .append("</section></main></body></html>");
    return new Content(subject, text, html.toString());
  }

  private String protectedCode(JsonNode payload) {
    String code = secretProtector.reveal(payload.path("codigoProtegido").asText(""));
    if (!code.matches("\\d{6}")) {
      throw new IllegalStateException("codigo protegido invalido");
    }
    return code;
  }

  private void requireActiveToken(JsonNode payload, String expectedType) {
    try {
      UUID tokenId = UUID.fromString(payload.path("tokenSegurancaId").asText(""));
      UUID recipientId = UUID.fromString(payload.path("destinatarioUsuarioId").asText(""));
      TokenSegurancaEntity securityRecord = securityTokens.findById(tokenId)
          .orElseThrow(() -> new OutboxPermanentDeliveryException("token de comunicacao ausente"));
      if (!recipientId.equals(securityRecord.getUsuarioId())
          || !expectedType.equals(securityRecord.getTipo())
          || securityRecord.getConsumidoEm() != null
          || !securityRecord.getExpiraEm().isAfter(OffsetDateTime.now())) {
        throw new OutboxPermanentDeliveryException("token de comunicacao expirado ou substituido");
      }
    } catch (IllegalArgumentException exception) {
      throw new OutboxPermanentDeliveryException("referencia de token invalida", exception);
    }
  }

  private String expiryMinutes(JsonNode payload) {
    try {
      OffsetDateTime expiry = OffsetDateTime.parse(payload.path("expiraEm").asText());
      long minutes = Math.max(1, ChronoUnit.MINUTES.between(OffsetDateTime.now(), expiry));
      return Long.toString(Math.min(minutes, 60));
    } catch (RuntimeException exception) {
      return "15";
    }
  }

  private UsuarioEntity recipient(JsonNode payload) {
    try {
      UUID id = UUID.fromString(payload.path("destinatarioUsuarioId").asText(""));
      return users.findById(id)
          .orElseThrow(() -> new IllegalStateException("destinatario da comunicacao nao encontrado"));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("destinatario da comunicacao invalido", exception);
    }
  }

  private JsonNode payload(OutboxEventoEntity outbox) {
    try {
      return objectMapper.readTree(outbox.getPayloadJson());
    } catch (Exception exception) {
      throw new IllegalStateException("payload da comunicacao invalido", exception);
    }
  }

  private String canonicalDomain(String value) {
    try {
      URI uri = URI.create(value == null ? "" : value.trim().replaceAll("/+$", ""));
      if (!List.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IllegalArgumentException("dominio canonico invalido");
      }
      return uri.toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("dominio canonico invalido para comunicacoes", exception);
    }
  }

  private String safeLink(String value) {
    try {
      URI link = URI.create(value);
      URI canonical = URI.create(canonicalDomain);
      if (!canonical.getScheme().equals(link.getScheme())
          || !canonical.getHost().equals(link.getHost())
          || link.getUserInfo() != null) {
        throw new IllegalArgumentException("link fora do dominio canonico");
      }
      return link.toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("link canonico da comunicacao invalido", exception);
    }
  }

  private String text(JsonNode payload, String field, String fallback, int maxLength) {
    String value = payload.path(field).asText("")
        .replaceAll("(?is)<[^>]*>", " ")
        .replaceAll("(?i)javascript\\s*:", "")
        .trim()
        .replaceAll("\\s+", " ");
    if (value.isBlank()) {
      return fallback;
    }
    return value.length() > maxLength ? value.substring(0, maxLength) : value;
  }

  private String escape(String value) {
    return value == null ? "" : value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private record Content(String subject, String text, String html) {
  }
}
