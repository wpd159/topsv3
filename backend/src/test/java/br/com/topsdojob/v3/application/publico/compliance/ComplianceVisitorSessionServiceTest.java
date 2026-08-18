package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceSignedCookieService.TipoTokenAssinado;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ComplianceVisitorSessionServiceTest {

  private static final String SIGNING_VALUE =
      "visitor-session-test-signing-value-not-used-outside-tests";

  @Test
  void cookieInvalidoHomonimoNaoOcultaSessaoAssinadaValida() {
    ComplianceSignedCookieService signedCookie = new ComplianceSignedCookieService(
        SIGNING_VALUE,
        "homologacao");
    ComplianceVisitorSessionService service = service(signedCookie);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    UUID sessionId = UUID.randomUUID();
    String validSession = signedCookie.emitir(
        TipoTokenAssinado.SESSION,
        sessionId,
        now.minusMinutes(1),
        now.plusMinutes(30),
        null,
        null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie(ComplianceSignedCookieService.SESSION_COOKIE, "sessao-legada-invalida"),
        new Cookie(ComplianceSignedCookieService.SESSION_COOKIE, validSession));

    var result = service.obterOuCriar(request);

    assertThat(result.sessionId()).isEqualTo(sessionId);
    assertThat(result.cookie().getValue()).isEqualTo(validSession);
    assertThat(result.nova()).isFalse();
  }

  @Test
  void cookiesInvalidosContinuamGerandoNovaSessaoAssinada() {
    ComplianceSignedCookieService signedCookie = new ComplianceSignedCookieService(
        SIGNING_VALUE,
        "homologacao");
    ComplianceVisitorSessionService service = service(signedCookie);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie(ComplianceSignedCookieService.SESSION_COOKIE, "sessao-invalida-a"),
        new Cookie(ComplianceSignedCookieService.SESSION_COOKIE, "sessao-invalida-b"));

    var result = service.obterOuCriar(request);

    assertThat(result.nova()).isTrue();
    assertThat(signedCookie.validar(
        result.cookie().getValue(),
        TipoTokenAssinado.SESSION,
        OffsetDateTime.now(ZoneOffset.UTC).withNano(0))).isPresent();
  }

  private ComplianceVisitorSessionService service(ComplianceSignedCookieService signedCookie) {
    return new ComplianceVisitorSessionService(
        signedCookie,
        new ComplianceAgeGateProperties(),
        new MetricaPublicaHashService(
            "visitor-session-test-hash-value-not-used-outside-tests",
            "homologacao"));
  }
}
