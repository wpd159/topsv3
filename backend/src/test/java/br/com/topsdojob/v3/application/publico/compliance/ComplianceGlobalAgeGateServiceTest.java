package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorSessionService.SessionContext;
import br.com.topsdojob.v3.application.publico.compliance.dto.AgeGateAcceptRequestDto;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class ComplianceGlobalAgeGateServiceTest {

  @Test
  void aceiteGlobalPersisteSeteDiasSemVirarTokenReforcado() {
    ComplianceSignedCookieService signedCookie = new ComplianceSignedCookieService(
        "age-gate-global-test-signing-value",
        "homologacao");
    ComplianceVisitorSessionService sessionService =
        mock(ComplianceVisitorSessionService.class);
    ComplianceVisitorAuditService auditService =
        mock(ComplianceVisitorAuditService.class);
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    MockHttpServletRequest request = new MockHttpServletRequest();
    SessionContext session = new SessionContext(
        UUID.randomUUID(),
        "1".repeat(64),
        "2".repeat(64),
        ResponseCookie.from("visitor_session_id", "session").build(),
        false);
    when(sessionService.obterOuCriar(any())).thenReturn(session);
    ComplianceGlobalAgeGateService service = new ComplianceGlobalAgeGateService(
        signedCookie,
        sessionService,
        properties,
        auditService);

    var accepted = service.aceitar(
        new AgeGateAcceptRequestDto("/anuncios/qa"),
        request);

    assertThat(accepted.status().accepted()).isTrue();
    assertThat(accepted.globalCookie().getMaxAge()).isEqualTo(Duration.ofDays(7));
    assertThat(accepted.globalCookie().toString())
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Lax");
    assertThat(accepted.globalCookie().getName())
        .isNotEqualTo(ComplianceSignedCookieService.ACCESS_COOKIE)
        .isNotEqualTo(ComplianceSignedCookieService.EXPLICIT_ACCESS_COOKIE);

    when(sessionService.cookies(
        eq(request),
        eq(ComplianceSignedCookieService.GLOBAL_COOKIE)))
        .thenReturn(List.of(accepted.globalCookie().getValue()));
    assertThat(service.status(request).accepted()).isTrue();
    assertThat(service.status(request).state()).isEqualTo("GLOBAL_ACEITO");
  }

  @Test
  void cookieLegadoHomônimoNaoOcultaCookieAtualAssinado() {
    ComplianceSignedCookieService signedCookie = new ComplianceSignedCookieService(
        "age-gate-duplicate-cookie-test-signing-value",
        "homologacao");
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    ComplianceVisitorSessionService sessionService = new ComplianceVisitorSessionService(
        signedCookie,
        properties,
        mock(MetricaPublicaHashService.class));
    ComplianceGlobalAgeGateService service = new ComplianceGlobalAgeGateService(
        signedCookie,
        sessionService,
        properties,
        mock(ComplianceVisitorAuditService.class));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    String signedValue = signedCookie.emitir(
        ComplianceSignedCookieService.TipoTokenAssinado.GLOBAL,
        UUID.randomUUID(),
        now,
        now.plus(properties.globalTtl()),
        null,
        null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie(ComplianceSignedCookieService.GLOBAL_COOKIE, "v1.legacy-value"),
        new Cookie(ComplianceSignedCookieService.GLOBAL_COOKIE, signedValue));

    assertThat(service.status(request).accepted()).isTrue();
    assertThat(service.status(request).state()).isEqualTo("GLOBAL_ACEITO");
  }
}
