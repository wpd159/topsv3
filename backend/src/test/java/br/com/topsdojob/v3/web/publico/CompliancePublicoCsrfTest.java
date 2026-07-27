package br.com.topsdojob.v3.web.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceGlobalAgeGateService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorDocumentService;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorVerificationService;
import br.com.topsdojob.v3.application.publico.compliance.dto.AgeGateGlobalStatusDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorAccessStatusDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeResponseDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorDocumentSubmitResponseDto;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@WebMvcTest(
    controllers = CompliancePublicoController.class,
    properties = "app.env=homologacao")
@ContextConfiguration(classes = {
    CompliancePublicoController.class,
    SecurityConfig.class,
    AdminSecurityErrorWriter.class
})
class CompliancePublicoCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private CsrfTokenRepository csrfTokenRepository;

  @MockBean
  private ComplianceGlobalAgeGateService globalService;

  @MockBean
  private ComplianceVisitorVerificationService verificationService;

  @MockBean
  private ComplianceVisitorAccessService accessService;

  @MockBean
  private ComplianceVisitorDocumentService documentService;

  @Test
  void getPublicoInicializaCsrfComRepositorioCanonicoDeCookie() throws Exception {
    when(globalService.status(any())).thenReturn(
        new AgeGateGlobalStatusDto(false, "GLOBAL_NAO_ACEITO", null));

    mockMvc.perform(get("/api/public/compliance/age-gate/status"))
        .andExpect(status().isOk());
    assertThat(csrfTokenRepository)
        .isInstanceOf(CookieCsrfTokenRepository.class);
  }

  @Test
  void todosOsPostsSaoRecusadosSemCsrf() throws Exception {
    UUID challengeId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();

    mockMvc.perform(post("/api/public/compliance/age-gate/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/public/compliance/visitor/challenge")
            .contentType(MediaType.APPLICATION_JSON)
            .content(challengeJson(anuncioId, midiaId)))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/public/compliance/visitor/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(verifyJson(challengeId)))
        .andExpect(status().isForbidden());
    mockMvc.perform(multipart("/api/public/compliance/visitor/document")
            .file(documento())
            .param("challengeId", challengeId.toString())
            .header("Idempotency-Key", "document-test"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/public/compliance/visitor/revoke")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void csrfValidoPermiteAvancarNosCincoContratos() throws Exception {
    UUID challengeId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    VisitorAccessStatusDto accessStatus = new VisitorAccessStatusDto(
        true,
        true,
        "REINFORCED",
        now.plusHours(1),
        false,
        "NONE",
        null,
        "VERIFIED",
        3,
        "ALLOW_LEVEL_1",
        null,
        null);

    when(globalService.aceitar(any(), any())).thenReturn(
        new ComplianceGlobalAgeGateService.AcceptResult(
            new AgeGateGlobalStatusDto(true, "GLOBAL_ACEITO", now.plusDays(7)),
            null,
            null));
    when(verificationService.iniciar(any(), any())).thenReturn(
        new ComplianceVisitorVerificationService.ChallengeResult(
            new VisitorChallengeResponseDto(
                challengeId,
                "CHALLENGE_ACTIVE",
                "REINFORCED",
                "MIDIA_RESTRITA",
                now.plusMinutes(10),
                false,
                false,
                3,
                null),
            null));
    when(verificationService.verificar(any(), any())).thenReturn(
        new ComplianceVisitorVerificationService.VerifyResult(
            accessStatus,
            null,
            null,
            null,
            org.springframework.http.HttpStatus.OK));
    when(documentService.submeter(eq(challengeId), eq("document-test"), any(), any()))
        .thenReturn(new ComplianceVisitorDocumentService.SubmitResult(
            new VisitorDocumentSubmitResponseDto(
                UUID.randomUUID(),
                challengeId,
                "DOCUMENT_PENDING",
                "PENDING",
                now,
                "Documento recebido para analise."),
            null));
    when(accessService.revogar(any(), any())).thenReturn(
        new ComplianceVisitorAccessService.RevokeResult(
            new VisitorAccessStatusDto(
                true,
                false,
                "NONE",
                null,
                false,
                "NONE",
                null,
                "GLOBAL_ACEITO",
                null,
                null,
                null,
                "ACESSO_REVOGADO"),
            null,
            null,
            null));

    mockMvc.perform(post("/api/public/compliance/age-gate/accept")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/public/compliance/visitor/challenge")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(challengeJson(anuncioId, midiaId)))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/public/compliance/visitor/verify")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(verifyJson(challengeId)))
        .andExpect(status().isOk());
    mockMvc.perform(multipart("/api/public/compliance/visitor/document")
            .file(documento())
            .param("challengeId", challengeId.toString())
            .header("Idempotency-Key", "document-test")
            .with(csrf()))
        .andExpect(status().isAccepted());
    mockMvc.perform(post("/api/public/compliance/visitor/revoke")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk());

    verify(documentService).submeter(
        eq(challengeId),
        eq("document-test"),
        any(),
        any());
  }

  private MockMultipartFile documento() {
    return new MockMultipartFile(
        "document",
        "sem-validade.pdf",
        "application/pdf",
        "%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  }

  private String challengeJson(UUID anuncioId, UUID midiaId) {
    return """
        {
          "level": "REINFORCED",
          "scope": "MIDIA_RESTRITA",
          "anuncioId": "%s",
          "midiaId": "%s",
          "route": "/anuncios/qa",
          "idempotencyKey": "challenge-test"
        }
        """.formatted(anuncioId, midiaId);
  }

  private String verifyJson(UUID challengeId) {
    return """
        {
          "challengeId": "%s",
          "dataNascimento": "01/01/1990",
          "confirmacaoDataNascimento": "01/01/1990",
          "cpf": "52998224725",
          "aceiteMaioridade": true,
          "aceiteConteudoRestrito": true,
          "aceitePrivacidade": true,
          "confirmacaoExplicita": false,
          "idempotencyKey": "verify-test"
        }
        """.formatted(challengeId);
  }
}
