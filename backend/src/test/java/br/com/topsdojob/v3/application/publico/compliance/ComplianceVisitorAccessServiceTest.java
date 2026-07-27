package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorSessionService.SessionContext;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoTokenVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusDocumentoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusTokenVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorDocumentoEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorTokenEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorDocumentoRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorTokenRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class ComplianceVisitorAccessServiceTest {

  private static final String SIGNING_VALUE =
      "age-gate-test-signing-value-not-used-outside-tests";

  @Test
  void aceiteGlobalIsoladoNaoAutorizaEscopoProtegido() {
    Fixture fixture = fixture();
    when(fixture.globalService.aceito(any())).thenReturn(true);
    when(fixture.sessionService.obterOuCriar(any())).thenReturn(fixture.session);

    assertThat(fixture.service.autorizado(
        new MockHttpServletRequest(),
        EscopoConteudoVisitante.MIDIA_RESTRITA)).isFalse();
    assertThat(fixture.tokenRepository.findByTokenHash(any())).isNotPresent();
  }

  @Test
  void emiteTokenGeralHashPersistidoEReconheceNoMesmoNavegador() {
    Fixture fixture = fixture();
    List<ComplianceVisitorTokenEntity> persisted = new ArrayList<>();
    when(fixture.tokenRepository.save(any(ComplianceVisitorTokenEntity.class)))
        .thenAnswer(invocation -> {
          ComplianceVisitorTokenEntity storedAccess = invocation.getArgument(0);
          persisted.add(storedAccess);
          return storedAccess;
        });
    when(fixture.tokenRepository.findByChallengeIdAndStatus(
        any(),
        eq(StatusTokenVisitante.ACTIVE))).thenReturn(List.of());
    ComplianceVisitorChallengeEntity challenge = challenge(
        fixture.session.sessionHash(),
        EscopoConteudoVisitante.MIDIA_RESTRITA,
        false);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);

    var issued = fixture.service.emitir(challenge, fixture.session, now);

    assertThat(persisted).hasSize(1);
    ComplianceVisitorTokenEntity storedAccess = persisted.get(0);
    assertThat(storedAccess.getTokenHash()).matches("[0-9a-f]{64}");
    assertThat(issued.generalCookie().getValue()).isNotEqualTo(storedAccess.getTokenHash());
    assertThat(issued.generalCookie().toString())
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Lax");

    when(fixture.globalService.aceito(any())).thenReturn(true);
    when(fixture.sessionService.obterOuCriar(any())).thenReturn(fixture.session);
    when(fixture.sessionService.cookie(
        any(),
        eq(ComplianceSignedCookieService.ACCESS_COOKIE)))
        .thenReturn(issued.generalCookie().getValue());
    when(fixture.tokenRepository.findByTokenHash(storedAccess.getTokenHash()))
        .thenReturn(Optional.of(storedAccess));

    assertThat(fixture.service.autorizado(
        new MockHttpServletRequest(),
        EscopoConteudoVisitante.WHATSAPP)).isTrue();
    assertThat(fixture.service.autorizado(
        new MockHttpServletRequest(),
        EscopoConteudoVisitante.CONTEUDO_EXPLICITO)).isFalse();
  }

  @Test
  void escopoExplicitoExigeTokenDistinto() {
    Fixture fixture = fixture();
    List<ComplianceVisitorTokenEntity> persisted = new ArrayList<>();
    when(fixture.tokenRepository.save(any(ComplianceVisitorTokenEntity.class)))
        .thenAnswer(invocation -> {
          ComplianceVisitorTokenEntity storedAccess = invocation.getArgument(0);
          persisted.add(storedAccess);
          return storedAccess;
        });
    when(fixture.tokenRepository.findByChallengeIdAndStatus(
        any(),
        eq(StatusTokenVisitante.ACTIVE))).thenReturn(List.of());
    ComplianceVisitorChallengeEntity challenge = challenge(
        fixture.session.sessionHash(),
        EscopoConteudoVisitante.CONTEUDO_EXPLICITO,
        true);

    var issued = fixture.service.emitir(
        challenge,
        fixture.session,
        OffsetDateTime.now(ZoneOffset.UTC).withNano(0));

    assertThat(persisted).hasSize(2);
    assertThat(issued.generalCookie()).isNotNull();
    assertThat(issued.explicitCookie()).isNotNull();
    assertThat(issued.explicitCookie().getMaxAge())
        .isEqualTo(fixture.properties.explicitTokenTtl());
    assertThat(persisted)
        .extracting(ComplianceVisitorTokenEntity::getEscopoToken)
        .extracting(Enum::name)
        .containsExactlyInAnyOrder("GENERAL", "EXPLICIT");
  }

  @Test
  void tokenPersistidoExpiradoNaoAutorizaMesmoComCookieAssinado() {
    Fixture fixture = fixture();
    List<ComplianceVisitorTokenEntity> persisted = new ArrayList<>();
    when(fixture.tokenRepository.save(any(ComplianceVisitorTokenEntity.class)))
        .thenAnswer(invocation -> {
          ComplianceVisitorTokenEntity storedAccess = invocation.getArgument(0);
          persisted.add(storedAccess);
          return storedAccess;
        });
    when(fixture.tokenRepository.findByChallengeIdAndStatus(
        any(),
        eq(StatusTokenVisitante.ACTIVE))).thenReturn(List.of());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    var issued = fixture.service.emitir(
        challenge(
            fixture.session.sessionHash(),
            EscopoConteudoVisitante.MIDIA_RESTRITA,
            false),
        fixture.session,
        now);
    ComplianceVisitorTokenEntity storedAccess = persisted.get(0);
    storedAccess.expirar(now.plusSeconds(1));

    when(fixture.globalService.aceito(any())).thenReturn(true);
    when(fixture.sessionService.obterOuCriar(any())).thenReturn(fixture.session);
    when(fixture.sessionService.cookie(
        any(),
        eq(ComplianceSignedCookieService.ACCESS_COOKIE)))
        .thenReturn(issued.generalCookie().getValue());
    when(fixture.tokenRepository.findByTokenHash(storedAccess.getTokenHash()))
        .thenReturn(Optional.of(storedAccess));

    assertThat(fixture.service.autorizado(
        new MockHttpServletRequest(),
        EscopoConteudoVisitante.MIDIA_RESTRITA)).isFalse();
  }

  @Test
  void revogacaoInvalidaTokensAtivosEExpiraOsDoisCookies() {
    Fixture fixture = fixture();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorTokenEntity storedAccess = ComplianceVisitorTokenEntity.emitir(
        UUID.randomUUID(),
        "7".repeat(64),
        fixture.session.sessionHash(),
        fixture.session.userAgentHash(),
        UUID.randomUUID(),
        EscopoTokenVisitante.GENERAL,
        NivelAcessoVisitante.REINFORCED,
        EscopoConteudoVisitante.WHATSAPP,
        0,
        DecisaoRiscoVisitante.ALLOW_LEVEL_1,
        now,
        now.plusMinutes(60));
    when(fixture.sessionService.obterOuCriar(any())).thenReturn(fixture.session);
    when(fixture.globalService.aceito(any())).thenReturn(true);
    when(fixture.tokenRepository.findBySessionHashAndStatusForUpdate(
        fixture.session.sessionHash(),
        StatusTokenVisitante.ACTIVE)).thenReturn(List.of(storedAccess));

    var result = fixture.service.revogar(
        new MockHttpServletRequest(),
        "REVOGACAO_VISITANTE");

    assertThat(storedAccess.getStatus()).isEqualTo(StatusTokenVisitante.REVOKED);
    assertThat(storedAccess.getMotivoRevogacao()).isEqualTo("REVOGACAO_VISITANTE");
    assertThat(result.status().verified()).isFalse();
    assertThat(result.generalExpiredCookie().getMaxAge()).isZero();
    assertThat(result.explicitExpiredCookie().getMaxAge()).isZero();
  }

  @Test
  void statusDocumentalRejeitadoExibeSomenteMotivoPublicoSanitizado() {
    Fixture fixture = fixture();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity challenge = challenge(
        fixture.session.sessionHash(),
        EscopoConteudoVisitante.MIDIA_RESTRITA,
        false);
    challenge.marcarDocumentoPendente("4".repeat(64), now);
    challenge.marcarDocumentoRejeitado("DOCUMENTO_REJEITADO", now);
    ComplianceVisitorDocumentoEntity documento =
        ComplianceVisitorDocumentoEntity.criarPendente(
            UUID.randomUUID(),
            fixture.session.sessionHash(),
            challenge.getId(),
            challenge.getAnuncioId(),
            "bucket-privado",
            "hml/preprod/documentos/compliance/visitor/teste.pdf",
            "application/pdf",
            100,
            "5".repeat(64),
            "6".repeat(64),
            now);
    documento.decidir(
        StatusDocumentoVisitante.REJECTED,
        UUID.randomUUID(),
        "Documento ilegivel; envie uma nova imagem.",
        now);
    when(fixture.globalService.aceito(any())).thenReturn(true);
    when(fixture.sessionService.obterOuCriar(any())).thenReturn(fixture.session);
    when(fixture.challengeRepository
        .findTopBySessionHashOrderByAtualizadoEmDescCriadoEmDescIdDesc(
            fixture.session.sessionHash())).thenReturn(Optional.of(challenge));
    when(fixture.documentoRepository.findTopByChallengeIdOrderByCriadoEmDesc(
        challenge.getId())).thenReturn(Optional.of(documento));

    var result = fixture.service.status(new MockHttpServletRequest());

    assertThat(result.status().state()).isEqualTo("DOCUMENT_REJECTED");
    assertThat(result.status().reasonPublic())
        .isEqualTo("Documento ilegivel; envie uma nova imagem.")
        .doesNotContain("DOCUMENTO_REJEITADO");
  }

  @Test
  void statusPriorizaChallengeDocumentalAtualizadoMaisRecentemente() {
    Fixture fixture = fixture();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity challenge = challenge(
        fixture.session.sessionHash(),
        EscopoConteudoVisitante.CONTEUDO_EXPLICITO,
        true);
    challenge.marcarDocumentoPendente("4".repeat(64), now.plusSeconds(1));
    when(fixture.globalService.aceito(any())).thenReturn(true);
    when(fixture.sessionService.obterOuCriar(any())).thenReturn(fixture.session);
    when(fixture.challengeRepository
        .findTopBySessionHashOrderByAtualizadoEmDescCriadoEmDescIdDesc(
            fixture.session.sessionHash())).thenReturn(Optional.of(challenge));

    var result = fixture.service.status(new MockHttpServletRequest());

    assertThat(result.status().state()).isEqualTo("DOCUMENT_PENDING");
    assertThat(result.status().documentStatus()).isEqualTo("DOCUMENT_PENDING");
    verify(fixture.challengeRepository)
        .findTopBySessionHashOrderByAtualizadoEmDescCriadoEmDescIdDesc(
            fixture.session.sessionHash());
  }

  private Fixture fixture() {
    ComplianceSignedCookieService signedCookie =
        new ComplianceSignedCookieService(SIGNING_VALUE, "homologacao");
    ComplianceVisitorSessionService sessionService =
        mock(ComplianceVisitorSessionService.class);
    ComplianceGlobalAgeGateService globalService =
        mock(ComplianceGlobalAgeGateService.class);
    ComplianceVisitorTokenRepository tokenRepository =
        mock(ComplianceVisitorTokenRepository.class);
    ComplianceVisitorChallengeRepository challengeRepository =
        mock(ComplianceVisitorChallengeRepository.class);
    ComplianceVisitorDocumentoRepository documentoRepository =
        mock(ComplianceVisitorDocumentoRepository.class);
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    MetricaPublicaHashService hashService =
        new MetricaPublicaHashService("hash-test-safe-value", "homologacao");
    SessionContext session = new SessionContext(
        UUID.randomUUID(),
        "1".repeat(64),
        "2".repeat(64),
        ResponseCookie.from("visitor_session_id", "session").build(),
        false);
    ComplianceVisitorAccessService service = new ComplianceVisitorAccessService(
        signedCookie,
        sessionService,
        globalService,
        properties,
        tokenRepository,
        challengeRepository,
        documentoRepository,
        hashService);
    return new Fixture(
        service,
        sessionService,
        globalService,
        tokenRepository,
        challengeRepository,
        documentoRepository,
        properties,
        session);
  }

  private ComplianceVisitorChallengeEntity challenge(
      String sessionHash,
      EscopoConteudoVisitante scope,
      boolean explicit) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity challenge =
        ComplianceVisitorChallengeEntity.criar(
            UUID.randomUUID(),
            sessionHash,
            explicit ? NivelAcessoVisitante.STRONG : NivelAcessoVisitante.REINFORCED,
            explicit ? NivelAcessoVisitante.STRONG : NivelAcessoVisitante.REINFORCED,
            scope,
            UUID.randomUUID(),
            explicit ? null : UUID.randomUUID(),
            null,
            "/anuncios/teste",
            4,
            DecisaoRiscoVisitante.ALLOW_LEVEL_1,
            "RISCO_APROVADO",
            explicit,
            false,
            "3".repeat(64),
            3,
            now.plusMinutes(10),
            now);
    challenge.verificar("4".repeat(64), now);
    return challenge;
  }

  private record Fixture(
      ComplianceVisitorAccessService service,
      ComplianceVisitorSessionService sessionService,
      ComplianceGlobalAgeGateService globalService,
      ComplianceVisitorTokenRepository tokenRepository,
      ComplianceVisitorChallengeRepository challengeRepository,
      ComplianceVisitorDocumentoRepository documentoRepository,
      ComplianceAgeGateProperties properties,
      SessionContext session) {
  }
}
