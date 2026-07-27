package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceSignedCookieService.TipoTokenAssinado;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComplianceSignedCookieServiceTest {

  private static final String SAFE_SIGNING_VALUE =
      "age-gate-test-signing-value-not-used-outside-tests";

  @Test
  void cookieAssinadoPreservaEscopoSemDadosPessoais() {
    ComplianceSignedCookieService service =
        new ComplianceSignedCookieService(SAFE_SIGNING_VALUE, "homologacao");
    OffsetDateTime issuedAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    OffsetDateTime expiresAt = issuedAt.plusMinutes(60);

    String raw = service.emitir(
        TipoTokenAssinado.ACCESS,
        UUID.randomUUID(),
        issuedAt,
        expiresAt,
        "GENERAL",
        "REINFORCED");

    var parsed = service.validar(raw, TipoTokenAssinado.ACCESS, issuedAt.plusMinutes(1));

    assertThat(parsed).isPresent();
    assertThat(parsed.orElseThrow().escopo()).isEqualTo("GENERAL");
    assertThat(parsed.orElseThrow().nivel()).isEqualTo("REINFORCED");
    assertThat(raw)
        .doesNotContain("cpf")
        .doesNotContain("nascimento")
        .doesNotContain("aceite");
  }

  @Test
  void adulteracaoTipoDivergenteEExpiracaoSaoRecusados() {
    ComplianceSignedCookieService service =
        new ComplianceSignedCookieService(SAFE_SIGNING_VALUE, "homologacao");
    OffsetDateTime issuedAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    OffsetDateTime expiresAt = issuedAt.plusMinutes(5);
    String raw = service.emitir(
        TipoTokenAssinado.ACCESS,
        UUID.randomUUID(),
        issuedAt,
        expiresAt,
        "GENERAL",
        "REINFORCED");

    assertThat(service.validar(
        raw.substring(0, raw.length() - 1) + "x",
        TipoTokenAssinado.ACCESS,
        issuedAt)).isEmpty();
    assertThat(service.validar(raw, TipoTokenAssinado.EXPLICIT, issuedAt)).isEmpty();
    assertThat(service.validar(raw, TipoTokenAssinado.ACCESS, expiresAt)).isEmpty();
  }

  @Test
  void atributosDosCookiesSeguemAmbienteETtl() {
    ComplianceSignedCookieService homologacao =
        new ComplianceSignedCookieService(SAFE_SIGNING_VALUE, "homologacao");
    String secureCookie = homologacao.cookie(
        ComplianceSignedCookieService.GLOBAL_COOKIE,
        "valor-assinado",
        java.time.Duration.ofDays(7)).toString();

    assertThat(secureCookie)
        .contains("Max-Age=604800")
        .contains("Path=/")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax");

    ComplianceSignedCookieService local =
        new ComplianceSignedCookieService("", "local");
    assertThat(local.cookie(
        ComplianceSignedCookieService.GLOBAL_COOKIE,
        "valor-local",
        java.time.Duration.ofDays(7)).toString())
        .doesNotContain("Secure");
  }

  @Test
  void segredoObrigatorioForaDoAmbienteLocal() {
    assertThatThrownBy(() -> new ComplianceSignedCookieService("", "homologacao"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_AGE_GATE_SIGNING_VALUE");
  }
}
