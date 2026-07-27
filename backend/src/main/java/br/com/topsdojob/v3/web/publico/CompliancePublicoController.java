package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceGlobalAgeGateService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorDocumentService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorVerificationService;
import br.com.topsdojob.v3.application.publico.compliance.dto.AgeGateAcceptRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.AgeGateGlobalStatusDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorAccessStatusDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeResponseDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorDocumentSubmitResponseDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorRevokeRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorVerifyRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public/compliance")
public class CompliancePublicoController {

  private final ComplianceGlobalAgeGateService globalService;
  private final ComplianceVisitorVerificationService verificationService;
  private final ComplianceVisitorAccessService accessService;
  private final ComplianceVisitorDocumentService documentService;

  public CompliancePublicoController(
      ComplianceGlobalAgeGateService globalService,
      ComplianceVisitorVerificationService verificationService,
      ComplianceVisitorAccessService accessService,
      ComplianceVisitorDocumentService documentService) {
    this.globalService = globalService;
    this.verificationService = verificationService;
    this.accessService = accessService;
    this.documentService = documentService;
  }

  @GetMapping("/age-gate/status")
  public AgeGateGlobalStatusDto globalStatus(
      HttpServletRequest request,
      CsrfToken csrfToken) {
    if (csrfToken != null) {
      csrfToken.getToken();
    }
    return globalService.status(request);
  }

  @PostMapping("/age-gate/accept")
  public ResponseEntity<AgeGateGlobalStatusDto> aceitar(
      @RequestBody(required = false) AgeGateAcceptRequestDto body,
      HttpServletRequest request) {
    var result = globalService.aceitar(body, request);
    return cookies(
        ResponseEntity.ok(),
        result.globalCookie(),
        result.sessionCookie())
        .body(result.status());
  }

  @PostMapping("/visitor/challenge")
  public ResponseEntity<VisitorChallengeResponseDto> challenge(
      @RequestBody VisitorChallengeRequestDto body,
      HttpServletRequest request) {
    var result = verificationService.iniciar(body, request);
    return cookies(ResponseEntity.ok(), result.sessionCookie())
        .body(result.response());
  }

  @PostMapping("/visitor/verify")
  public ResponseEntity<VisitorAccessStatusDto> verificar(
      @RequestBody VisitorVerifyRequestDto body,
      HttpServletRequest request) {
    var result = verificationService.verificar(body, request);
    return cookies(
        ResponseEntity.status(result.httpStatus()),
        result.sessionCookie(),
        result.generalCookie(),
        result.explicitCookie())
        .body(result.status());
  }

  @GetMapping("/visitor/status")
  public ResponseEntity<VisitorAccessStatusDto> visitorStatus(
      HttpServletRequest request,
      CsrfToken csrfToken) {
    if (csrfToken != null) {
      csrfToken.getToken();
    }
    var result = accessService.status(request);
    return cookies(ResponseEntity.ok(), result.sessionCookie())
        .body(result.status());
  }

  @PostMapping(
      value = "/visitor/document",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<VisitorDocumentSubmitResponseDto> documento(
      @RequestParam UUID challengeId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestPart("document") MultipartFile document,
      HttpServletRequest request) {
    var result = documentService.submeter(
        challengeId,
        idempotencyKey,
        document,
        request);
    return cookies(ResponseEntity.accepted(), result.sessionCookie())
        .body(result.response());
  }

  @PostMapping("/visitor/revoke")
  public ResponseEntity<VisitorAccessStatusDto> revogar(
      @RequestBody(required = false) VisitorRevokeRequestDto body,
      HttpServletRequest request) {
    var result = accessService.revogar(request, "REVOGACAO_VISITANTE");
    return cookies(
        ResponseEntity.ok(),
        result.sessionCookie(),
        result.generalExpiredCookie(),
        result.explicitExpiredCookie())
        .body(result.status());
  }

  private static ResponseEntity.BodyBuilder cookies(
      ResponseEntity.BodyBuilder builder,
      ResponseCookie... cookies) {
    for (ResponseCookie cookie : cookies) {
      if (cookie != null) {
        builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
      }
    }
    return builder.header(HttpHeaders.CACHE_CONTROL, "no-store");
  }
}
