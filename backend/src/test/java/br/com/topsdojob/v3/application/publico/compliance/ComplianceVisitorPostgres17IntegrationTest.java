package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceChallengeContextService.Contexto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorRiskService.RiscoResultado;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorVerifyRequestDto;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusTokenVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorTokenEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorTokenRepository;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresInitializer;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import br.com.topsdojob.v3.web.publico.CompliancePublicoController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Servicos e transacoes reais; cookies assinados e dados exclusivamente sinteticos. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class, initializers = PostgresInitializer.class)
@Import({ComplianceVisitorVerificationService.class, ComplianceVisitorAccessService.class,
    ComplianceVisitorSessionService.class, ComplianceGlobalAgeGateService.class,
    ComplianceAgeGateProperties.class, CpfVisitanteValidator.class,
    ComplianceVisitorPostgres17IntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "COMPLIANCE_POSTGRES17_ENABLED", matches = "true")
class ComplianceVisitorPostgres17IntegrationTest {

  @Autowired private ComplianceVisitorVerificationService verification;
  @Autowired private ComplianceVisitorAccessService access;
  @Autowired private ComplianceVisitorSessionService sessions;
  @Autowired private ComplianceGlobalAgeGateService global;
  @Autowired private ComplianceVisitorChallengeRepository challenges;
  @Autowired private ComplianceVisitorTokenRepository tokens;
  @Autowired private MetricaPublicaHashService hashes;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private ComplianceChallengeContextService contexts;
  @MockBean private ComplianceVisitorRiskService risk;
  @MockBean private ComplianceVisitorAuditService audit;

  @AfterAll
  static void cleanup() throws Exception {
    PostgresSupport.stop();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void primeiraEmissaoRepeticaoRevogacaoENovoChallengeMantemIndiceReal(boolean explicit)
      throws Exception {
    MockHttpServletRequest request = acceptedSession();
    var challenge = challenge(request, explicit, UUID.randomUUID().toString());
    allowRisk(explicit);
    var body = verifyRequest(challenge.getId(), "verify-synthetic");

    var first = verification.verificar(body, request);
    List<ComplianceVisitorTokenEntity> firstTokens = tokens.findByChallengeId(challenge.getId());
    assertThat(first.httpStatus()).isEqualTo(HttpStatus.OK);
    assertThat(first.status().verified()).isTrue();
    assertThat(firstTokens).hasSize(explicit ? 2 : 1);
    assertThat(firstTokens).allSatisfy(token -> assertThat(token.getStatus()).isEqualTo(StatusTokenVisitante.ACTIVE));
    var repeat = verification.verificar(body, request);
    assertThat(repeat.generalCookie().getValue()).isEqualTo(first.generalCookie().getValue());
    assertThat(repeat.status().expiresAt()).isEqualTo(first.status().expiresAt());
    assertThat(tokens.findByChallengeId(challenge.getId())).extracting(ComplianceVisitorTokenEntity::getId)
        .containsExactlyInAnyOrderElementsOf(firstTokens.stream().map(ComplianceVisitorTokenEntity::getId).toList());

    Cookie[] cookies = request.getCookies();
    request.setCookies(cookies[0], cookies[1],
        new Cookie(ComplianceSignedCookieService.ACCESS_COOKIE, first.generalCookie().getValue()));
    assertThat(access.autorizado(request, EscopoConteudoVisitante.STORY)).isTrue();
    access.revogar(request, "REVOGACAO_SINTETICA");
    assertThat(access.autorizado(request, EscopoConteudoVisitante.STORY)).isFalse();

    // Calls run outside a surrounding test transaction: the unique constraint is observed
    // at flush/commit in the real service proxy, not hidden by a rollback-only test fixture.
    assertThatThrownBy(() -> verification.verificar(body, request))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GONE));
    var mvc = MockMvcBuilders.standaloneSetup(
        new CompliancePublicoController(global, verification, access, null)).build();
    mvc.perform(post("/api/public/compliance/visitor/verify")
        .cookie(request.getCookies()).header("User-Agent", "compliance-synthetic")
        .contentType(MediaType.APPLICATION_JSON)
        .content(new ObjectMapper().writeValueAsString(body)))
        .andExpect(status().isGone());
    assertThat(tokens.findByChallengeId(challenge.getId())).hasSize(firstTokens.size())
        .allSatisfy(token -> assertThat(token.getStatus()).isEqualTo(StatusTokenVisitante.REVOKED));
    assertThat(access.autorizado(request, EscopoConteudoVisitante.STORY)).isFalse();

    var next = challenge(request, explicit, UUID.randomUUID().toString());
    assertThat(verification.verificar(verifyRequest(next.getId(), "new-valid-verification"), request)
        .status().verified()).isTrue();
    assertThat(tokens.findByChallengeId(next.getId())).hasSize(explicit ? 2 : 1);
    assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version::integer = 53 and success", Long.class))
        .isEqualTo(1L);
    assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname = 'compliance_token_challenge_scope_uk'", String.class))
        .contains("UNIQUE", "challenge_id, escopo_token");
  }

  @Test
  void retomadaConsultaSessaoNivelEscopoEContextoSemCriarOutroChallenge() {
    MockHttpServletRequest request = acceptedSession();
    String story = UUID.randomUUID().toString();
    var pending = challenge(request, false, story);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    pending.marcarDocumentoPendente(hashes.hash("verify", "pending"), now);
    pending = challenges.saveAndFlush(pending);
    allowRisk(false);
    when(contexts.validar(any(), any())).thenAnswer(invocation -> {
      VisitorChallengeRequestDto input = invocation.getArgument(0);
      return new Contexto(null, null, input.storyId(), input.route());
    });
    var input = new VisitorChallengeRequestDto("REINFORCED", "STORY", null, null,
        story, "/stories", "reload-synthetic");
    long before = challenges.count();
    var resumed = verification.iniciar(input, request).response();
    assertThat(resumed.challengeId()).isEqualTo(pending.getId());
    assertThat(resumed.state()).isEqualTo("DOCUMENT_PENDING");
    assertThat(challenges.count()).isEqualTo(before);
    assertThat(tokens.findByChallengeId(pending.getId())).isEmpty();

    assertThat(verification.iniciar(input, acceptedSession()).response().challengeId()).isNotEqualTo(pending.getId());
    for (VisitorChallengeRequestDto incompatible : List.of(
        new VisitorChallengeRequestDto("STRONG", "STORY", null, null, story, "/stories", "other-level"),
        new VisitorChallengeRequestDto("REINFORCED", "WHATSAPP", null, null, story, "/stories", "other-scope"),
        new VisitorChallengeRequestDto("REINFORCED", "STORY", null, null, "other-story", "/stories", "other-story"),
        new VisitorChallengeRequestDto("REINFORCED", "STORY", null, null, story, "/another-route", "other-route"))) {
      assertThat(verification.iniciar(incompatible, request).response().challengeId()).isNotEqualTo(pending.getId());
    }
    pending.marcarDocumentoAprovado(now, now.plusDays(2));
    pending = challenges.saveAndFlush(pending);
    assertThat(verification.iniciar(input, request).response().state()).isEqualTo("DOCUMENT_APPROVED");
    assertThat(tokens.findByChallengeId(pending.getId())).isEmpty();
    assertThat(verification.verificar(verifyRequest(pending.getId(), "confirm-after-reload"), request).status().verified()).isTrue();
  }

  private MockHttpServletRequest acceptedSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("User-Agent", "compliance-synthetic");
    var accepted = global.aceitar(null, request);
    request.setCookies(new Cookie(ComplianceSignedCookieService.SESSION_COOKIE, accepted.sessionCookie().getValue()),
        new Cookie(ComplianceSignedCookieService.GLOBAL_COOKIE, accepted.globalCookie().getValue()));
    return request;
  }

  private ComplianceVisitorChallengeEntity challenge(MockHttpServletRequest request, boolean explicit, String story) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var level = explicit ? NivelAcessoVisitante.STRONG : NivelAcessoVisitante.REINFORCED;
    var entity = ComplianceVisitorChallengeEntity.criar(UUID.randomUUID(), sessions.obterOuCriar(request).sessionHash(),
        level, level, explicit ? EscopoConteudoVisitante.CONTEUDO_EXPLICITO : EscopoConteudoVisitante.STORY,
        null, null, story, "/stories", 0, DecisaoRiscoVisitante.ALLOW_LEVEL_1, "SINTETICO", explicit, false,
        hashes.hash("challenge", UUID.randomUUID().toString()), 3, now.plusMinutes(10), now);
    return challenges.saveAndFlush(entity);
  }

  private void allowRisk(boolean explicit) {
    when(risk.avaliar(anyString(), any(), any(), anyString(), any(), anyString(), anyBoolean(), any()))
        .thenReturn(new RiscoResultado(0, DecisaoRiscoVisitante.ALLOW_LEVEL_1,
            explicit ? NivelAcessoVisitante.STRONG : NivelAcessoVisitante.REINFORCED, "SINTETICO", false, false));
  }

  private VisitorVerifyRequestDto verifyRequest(UUID id, String key) {
    return new VisitorVerifyRequestDto(id, "01/01/1990", "01/01/1990", CpfVisitanteValidatorTest.cpfSintetico(),
        true, true, true, true, key);
  }

  @TestConfiguration
  static class TestBeans {
    @Bean ComplianceSignedCookieService signedCookieService() {
      return new ComplianceSignedCookieService("compliance-synthetic-signing-value-not-production", "homologacao");
    }
    @Bean MetricaPublicaHashService hashService() {
      return new MetricaPublicaHashService("compliance-synthetic-hash-not-production", "homologacao");
    }
  }
}
