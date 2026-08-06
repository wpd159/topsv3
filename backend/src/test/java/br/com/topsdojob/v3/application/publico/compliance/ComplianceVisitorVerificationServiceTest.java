package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceChallengeContextService.Contexto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService.IssuedTokens;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorRiskService.RiscoResultado;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorSessionService.SessionContext;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorAccessStatusDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorVerifyRequestDto;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ComplianceVisitorVerificationServiceTest {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

  @Test
  void validaNascimentoCpfEAceitesAntesDeEmitirToken() {
    List<VisitorVerifyRequestDto> invalidRequests = List.of(
        request("31/02/1990", "31/02/1990", CpfVisitanteValidatorTest.cpfSintetico(),
            true, true, true),
        request("01/01/1990", "02/01/1990", CpfVisitanteValidatorTest.cpfSintetico(),
            true, true, true),
        request(
            LocalDate.now(ZoneOffset.UTC).minusYears(17).format(DATE),
            LocalDate.now(ZoneOffset.UTC).minusYears(17).format(DATE),
            CpfVisitanteValidatorTest.cpfSintetico(),
            true,
            true,
            true),
        request("01/01/1990", "01/01/1990", "11111111111", true, true, true),
        request("01/01/1990", "01/01/1990", CpfVisitanteValidatorTest.cpfSintetico(),
            true, false, true));

    for (VisitorVerifyRequestDto invalid : invalidRequests) {
      Fixture fixture = fixture(activeChallenge());

      assertThatThrownBy(() -> fixture.service.verificar(
          invalid,
          new MockHttpServletRequest()))
          .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
              assertThat(exception.getStatusCode())
                  .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
      verify(fixture.accessService, never()).emitir(any(), any(), any());
    }
  }

  @Test
  void verificacaoValidaEmiteTokenSemPersistirDadosInformadosNoChallenge() {
    ComplianceVisitorChallengeEntity challenge = activeChallenge();
    Fixture fixture = fixture(challenge);

    var result = fixture.service.verificar(
        validRequest("verify-success"),
        new MockHttpServletRequest());

    assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
    assertThat(result.status().verified()).isTrue();
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.VERIFIED);
    assertThat(challenge.toString())
        .doesNotContain(CpfVisitanteValidatorTest.cpfSintetico())
        .doesNotContain("01/01/1990");
    verify(fixture.riskService).registrarSucesso(
        fixture.session.sessionHash(),
        challenge.getVerificadoEm());
  }

  @Test
  void riscoDeRevisaoConduzADocumentPendingSemEmitirToken() {
    ComplianceVisitorChallengeEntity challenge = activeChallenge();
    Fixture fixture = fixture(challenge);
    when(fixture.riskService.avaliar(
        anyString(),
        any(),
        any(),
        anyString(),
        any(),
        anyString(),
        anyBoolean(),
        any())).thenReturn(new RiscoResultado(
            100,
            DecisaoRiscoVisitante.REVIEW_FLAG,
            NivelAcessoVisitante.STRONG,
            "RISCO_REVISAO_DOCUMENTAL",
            true,
            false));

    var result = fixture.service.verificar(
        validRequest("verify-document"),
        new MockHttpServletRequest());

    assertThat(result.httpStatus()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(result.status().state()).isEqualTo("DOCUMENT_PENDING");
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.DOCUMENT_PENDING);
    verify(fixture.accessService, never()).emitir(any(), any(), any());
  }

  @Test
  void tentativasInvalidasEsgotadasConduzemARetry() {
    ComplianceVisitorChallengeEntity challenge = activeChallenge();
    Fixture fixture = fixture(challenge);

    for (int attempt = 1; attempt <= 3; attempt++) {
      VisitorVerifyRequestDto invalid = request(
          "01/01/1990",
          "01/01/1990",
          "11111111111",
          true,
          true,
          true,
          "verify-retry-" + attempt);
      assertThatThrownBy(() -> fixture.service.verificar(
          invalid,
          new MockHttpServletRequest()))
          .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
              assertThat(exception.getStatusCode())
                  .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.FAILED);
    verify(fixture.accessService, never()).emitir(any(), any(), any());
  }

  @Test
  void riscoBloqueadoRetorna429SemEmitirToken() {
    ComplianceVisitorChallengeEntity challenge = activeChallenge();
    Fixture fixture = fixture(challenge);
    when(fixture.riskService.avaliar(
        anyString(),
        any(),
        any(),
        anyString(),
        any(),
        anyString(),
        anyBoolean(),
        any())).thenReturn(new RiscoResultado(
            215,
            DecisaoRiscoVisitante.HARD_BLOCK,
            NivelAcessoVisitante.NONE,
            "RISCO_BLOQUEIO",
            false,
            true));

    assertThatThrownBy(() -> fixture.service.verificar(
        validRequest("verify-blocked"),
        new MockHttpServletRequest()))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.BLOCKED);
    verify(fixture.accessService, never()).emitir(any(), any(), any());
  }

  @Test
  void challengeExpiradoEReplayDivergenteSaoRecusados() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity expired = challenge(now.minusMinutes(20), now.minusMinutes(10));
    Fixture expiredFixture = fixture(expired);
    assertThatThrownBy(() -> expiredFixture.service.verificar(
        validRequest("verify-expired"),
        new MockHttpServletRequest()))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GONE));

    ComplianceVisitorChallengeEntity verified = activeChallenge();
    Fixture replayFixture = fixture(verified);
    String firstHash = replayFixture.hashService.hash(
        "compliance-idempotencia",
        replayFixture.session.sessionHash() + "|verify-original");
    verified.verificar(firstHash, now);

    assertThatThrownBy(() -> replayFixture.service.verificar(
        validRequest("verify-different"),
        new MockHttpServletRequest()))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void challengeDeStoryIndependenteEhPersistidoSemContextoDeAnuncio() {
    ComplianceVisitorChallengeEntity contextChallenge = activeChallenge();
    Fixture fixture = fixture(contextChallenge);
    String storyId = UUID.randomUUID().toString();
    when(fixture.contextService.validar(any(), any())).thenReturn(
        new Contexto(null, null, storyId, "/stories"));
    VisitorChallengeRequestDto request = new VisitorChallengeRequestDto(
        "REINFORCED",
        "STORY",
        null,
        null,
        storyId,
        "/stories",
        "challenge-story-independente");

    var result = fixture.service.iniciar(request, new MockHttpServletRequest());

    ArgumentCaptor<ComplianceVisitorChallengeEntity> captor =
        ArgumentCaptor.forClass(ComplianceVisitorChallengeEntity.class);
    verify(fixture.repository).save(captor.capture());
    assertThat(result.response().scope()).isEqualTo("STORY");
    assertThat(captor.getValue().getAnuncioId()).isNull();
    assertThat(captor.getValue().getAnuncioMidiaId()).isNull();
    assertThat(captor.getValue().getStoryReferencia()).isEqualTo(storyId);
  }

  @Test
  void challengeExigeIdempotenciaERepeteSemCriarOutroRegistro() {
    ComplianceVisitorChallengeEntity contextChallenge = activeChallenge();
    Fixture fixture = fixture(contextChallenge);
    VisitorChallengeRequestDto missingKey = new VisitorChallengeRequestDto(
        "REINFORCED",
        "MIDIA_RESTRITA",
        contextChallenge.getAnuncioId(),
        contextChallenge.getAnuncioMidiaId(),
        null,
        "/anuncios/teste",
        null);

    assertThatThrownBy(() -> fixture.service.iniciar(
        missingKey,
        new MockHttpServletRequest()))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(fixture.repository, never()).save(any());

    VisitorChallengeRequestDto request = new VisitorChallengeRequestDto(
        "REINFORCED",
        "MIDIA_RESTRITA",
        contextChallenge.getAnuncioId(),
        contextChallenge.getAnuncioMidiaId(),
        null,
        "/anuncios/teste",
        "challenge-idempotente");
    var first = fixture.service.iniciar(request, new MockHttpServletRequest());
    var second = fixture.service.iniciar(request, new MockHttpServletRequest());

    assertThat(first.response().challengeId())
        .isEqualTo(second.response().challengeId());
    verify(fixture.repository, times(1)).save(any());
  }

  private Fixture fixture(ComplianceVisitorChallengeEntity challenge) {
    ComplianceVisitorChallengeRepository repository =
        mock(ComplianceVisitorChallengeRepository.class);
    ComplianceVisitorSessionService sessionService =
        mock(ComplianceVisitorSessionService.class);
    ComplianceGlobalAgeGateService globalService =
        mock(ComplianceGlobalAgeGateService.class);
    ComplianceChallengeContextService contextService =
        mock(ComplianceChallengeContextService.class);
    ComplianceVisitorRiskService riskService =
        mock(ComplianceVisitorRiskService.class);
    ComplianceVisitorAccessService accessService =
        mock(ComplianceVisitorAccessService.class);
    ComplianceVisitorAuditService auditService =
        mock(ComplianceVisitorAuditService.class);
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    MetricaPublicaHashService hashService =
        new MetricaPublicaHashService("hash-test-safe-value", "homologacao");
    SessionContext session = new SessionContext(
        UUID.randomUUID(),
        challenge.getSessionHash(),
        "2".repeat(64),
        ResponseCookie.from("visitor_session_id", "session").build(),
        false);
    when(globalService.aceito(any())).thenReturn(true);
    when(sessionService.obterOuCriar(any())).thenReturn(session);
    when(repository.findByIdForUpdate(any()))
        .thenReturn(Optional.of(challenge));
    AtomicReference<ComplianceVisitorChallengeEntity> initiated =
        new AtomicReference<>();
    when(repository.findBySessionHashAndIdempotenciaHash(anyString(), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(initiated.get()));
    when(repository.save(any(ComplianceVisitorChallengeEntity.class)))
        .thenAnswer(invocation -> {
          ComplianceVisitorChallengeEntity saved = invocation.getArgument(0);
          initiated.set(saved);
          return saved;
        });
    when(contextService.validar(any(), any())).thenReturn(new Contexto(
        challenge.getAnuncioId(),
        challenge.getAnuncioMidiaId(),
        null,
        "/anuncios/teste"));
    when(riskService.avaliar(
        anyString(),
        any(),
        any(),
        anyString(),
        any(),
        anyString(),
        anyBoolean(),
        any())).thenReturn(new RiscoResultado(
            3,
            DecisaoRiscoVisitante.ALLOW_LEVEL_1,
            NivelAcessoVisitante.REINFORCED,
            "RISCO_APROVADO",
            false,
            false));
    VisitorAccessStatusDto status = new VisitorAccessStatusDto(
        true,
        true,
        "REINFORCED",
        OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
        false,
        "NONE",
        null,
        "VERIFIED",
        3,
        "ALLOW_LEVEL_1",
        null,
        null);
    when(accessService.emitir(any(), any(), any())).thenReturn(new IssuedTokens(
        status,
        session.cookie(),
        ResponseCookie.from("visitor_access_token", "token").build(),
        null));
    ComplianceVisitorVerificationService service =
        new ComplianceVisitorVerificationService(
            repository,
            sessionService,
            globalService,
            contextService,
            riskService,
            accessService,
            auditService,
            properties,
            hashService,
            new CpfVisitanteValidator());
    return new Fixture(
        service,
        repository,
        contextService,
        riskService,
        accessService,
        hashService,
        session);
  }

  private ComplianceVisitorChallengeEntity activeChallenge() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    return challenge(now, now.plusMinutes(10));
  }

  private ComplianceVisitorChallengeEntity challenge(
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {
    return ComplianceVisitorChallengeEntity.criar(
        UUID.randomUUID(),
        "1".repeat(64),
        NivelAcessoVisitante.REINFORCED,
        NivelAcessoVisitante.REINFORCED,
        EscopoConteudoVisitante.MIDIA_RESTRITA,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "/anuncios/teste",
        0,
        DecisaoRiscoVisitante.ALLOW_LEVEL_1,
        "RISCO_APROVADO",
        false,
        false,
        "3".repeat(64),
        3,
        expiresAt,
        createdAt);
  }

  private VisitorVerifyRequestDto validRequest(String idempotencyKey) {
    return request(
        "01/01/1990",
        "01/01/1990",
        CpfVisitanteValidatorTest.cpfSintetico(),
        true,
        true,
        true,
        idempotencyKey);
  }

  private VisitorVerifyRequestDto request(
      String birth,
      String confirmation,
      String cpf,
      boolean adult,
      boolean restricted,
      boolean privacy) {
    return request(
        birth,
        confirmation,
        cpf,
        adult,
        restricted,
        privacy,
        "verify-invalid");
  }

  private VisitorVerifyRequestDto request(
      String birth,
      String confirmation,
      String cpf,
      boolean adult,
      boolean restricted,
      boolean privacy,
      String idempotencyKey) {
    return new VisitorVerifyRequestDto(
        UUID.randomUUID(),
        birth,
        confirmation,
        cpf,
        adult,
        restricted,
        privacy,
        false,
        idempotencyKey);
  }

  private record Fixture(
      ComplianceVisitorVerificationService service,
      ComplianceVisitorChallengeRepository repository,
      ComplianceChallengeContextService contextService,
      ComplianceVisitorRiskService riskService,
      ComplianceVisitorAccessService accessService,
      MetricaPublicaHashService hashService,
      SessionContext session) {
  }
}
