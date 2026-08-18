package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceSignedCookieService.TipoTokenAssinado;
import br.com.topsdojob.v3.application.publico.compliance.dto.AgeGateAcceptRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.AgeGateGlobalStatusDto;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EstadoPublicoAgeGate;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.MetodoVerificacaoEtaria;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceGlobalAgeGateService {

  private final ComplianceSignedCookieService signedCookieService;
  private final ComplianceVisitorSessionService sessionService;
  private final ComplianceAgeGateProperties properties;
  private final ComplianceVisitorAuditService auditService;

  public ComplianceGlobalAgeGateService(
      ComplianceSignedCookieService signedCookieService,
      ComplianceVisitorSessionService sessionService,
      ComplianceAgeGateProperties properties,
      ComplianceVisitorAuditService auditService) {
    this.signedCookieService = signedCookieService;
    this.sessionService = sessionService;
    this.properties = properties;
    this.auditService = auditService;
  }

  public AgeGateGlobalStatusDto status(HttpServletRequest request) {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    var aceiteAssinado = sessionService
        .cookies(request, ComplianceSignedCookieService.GLOBAL_COOKIE)
        .stream()
        .map(value -> signedCookieService.validar(
            value,
            TipoTokenAssinado.GLOBAL,
            agora))
        .flatMap(Optional::stream)
        .findFirst();
    return aceiteAssinado
        .map(value -> new AgeGateGlobalStatusDto(
            true,
            EstadoPublicoAgeGate.GLOBAL_ACEITO.name(),
            value.expiraEm()))
        .orElseGet(() -> new AgeGateGlobalStatusDto(
            false,
            EstadoPublicoAgeGate.GLOBAL_NAO_ACEITO.name(),
            null));
  }

  public boolean aceito(HttpServletRequest request) {
    return status(request).accepted();
  }

  @Transactional
  public AcceptResult aceitar(
      AgeGateAcceptRequestDto request,
      HttpServletRequest httpRequest) {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    OffsetDateTime expiraEm = agora.plus(properties.globalTtl());
    String valor = signedCookieService.emitir(
        TipoTokenAssinado.GLOBAL,
        UUID.randomUUID(),
        agora,
        expiraEm,
        null,
        null);
    ResponseCookie globalCookie = signedCookieService.cookie(
        ComplianceSignedCookieService.GLOBAL_COOKIE,
        valor,
        properties.globalTtl());
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(httpRequest);
    auditService.registrar(
        ResultadoVerificacaoEtaria.PERMITIDO,
        MetodoVerificacaoEtaria.DECLARACAO,
        null,
        null,
        session.sessionHash(),
        EstadoPublicoAgeGate.GLOBAL_ACEITO.name(),
        null,
        origemSanitizada(request == null ? null : request.originPath()),
        null,
        httpRequest);
    return new AcceptResult(
        new AgeGateGlobalStatusDto(
            true,
            EstadoPublicoAgeGate.GLOBAL_ACEITO.name(),
            expiraEm),
        globalCookie,
        session.cookie());
  }

  private String origemSanitizada(String value) {
    if (value == null || value.isBlank()) {
      return "ACEITE_GLOBAL";
    }
    String path = value.trim();
    if (!path.startsWith("/") || path.length() > 160 || path.contains("://")) {
      return "ACEITE_GLOBAL";
    }
    return "ACEITE_GLOBAL";
  }

  public record AcceptResult(
      AgeGateGlobalStatusDto status,
      ResponseCookie globalCookie,
      ResponseCookie sessionCookie) {
  }
}
