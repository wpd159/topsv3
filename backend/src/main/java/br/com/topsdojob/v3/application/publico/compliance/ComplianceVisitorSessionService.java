package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceSignedCookieService.TipoTokenAssinado;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class ComplianceVisitorSessionService {

  private final ComplianceSignedCookieService signedCookieService;
  private final ComplianceAgeGateProperties properties;
  private final MetricaPublicaHashService hashService;

  public ComplianceVisitorSessionService(
      ComplianceSignedCookieService signedCookieService,
      ComplianceAgeGateProperties properties,
      MetricaPublicaHashService hashService) {
    this.signedCookieService = signedCookieService;
    this.properties = properties;
    this.hashService = hashService;
  }

  public SessionContext obterOuCriar(HttpServletRequest request) {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    String existente = cookie(request, ComplianceSignedCookieService.SESSION_COOKIE);
    var validado = signedCookieService.validar(existente, TipoTokenAssinado.SESSION, agora);
    UUID sessionId = validado.map(item -> item.id()).orElseGet(UUID::randomUUID);
    OffsetDateTime emitidoEm = validado.map(item -> item.emitidoEm()).orElse(agora);
    OffsetDateTime expiraEm = validado
        .map(item -> item.expiraEm())
        .orElseGet(() -> agora.plus(properties.sessionTtl()));
    String valor = validado.isPresent()
        ? existente
        : signedCookieService.emitir(
            TipoTokenAssinado.SESSION,
            sessionId,
            emitidoEm,
            expiraEm,
            null,
            null);
    ResponseCookie responseCookie = signedCookieService.cookie(
        ComplianceSignedCookieService.SESSION_COOKIE,
        valor,
        java.time.Duration.between(agora, expiraEm));
    return new SessionContext(
        sessionId,
        hashService.hash("compliance-session", sessionId.toString()),
        hashService.hash("user-agent", userAgent(request)),
        responseCookie,
        validado.isEmpty());
  }

  private String userAgent(HttpServletRequest request) {
    if (request == null) {
      return "ausente";
    }
    String value = request.getHeader("User-Agent");
    return value == null || value.isBlank() ? "ausente" : value;
  }

  public String cookie(HttpServletRequest request, String name) {
    return cookies(request, name).stream()
        .findFirst()
        .orElse(null);
  }

  public List<String> cookies(HttpServletRequest request, String name) {
    if (request == null || request.getCookies() == null) {
      return List.of();
    }
    return Arrays.stream(request.getCookies())
        .filter(item -> name.equals(item.getName()))
        .map(Cookie::getValue)
        .toList();
  }

  public record SessionContext(
      UUID sessionId,
      String sessionHash,
      String userAgentHash,
      ResponseCookie cookie,
      boolean nova) {
  }
}
