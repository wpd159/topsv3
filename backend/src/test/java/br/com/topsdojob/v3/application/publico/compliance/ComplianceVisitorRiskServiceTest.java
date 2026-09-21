package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorRiskProfileEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorRiskProfileRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorTokenRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComplianceVisitorRiskServiceTest {

  @Test
  void riscoLimpoPermiteNivelSolicitado() {
    Fixture fixture = fixture(new ComplianceAgeGateProperties());

    var result = fixture.service.avaliar(
        "1".repeat(64),
        NivelAcessoVisitante.REINFORCED,
        EscopoConteudoVisitante.WHATSAPP,
        "/anuncios/teste",
        UUID.randomUUID(),
        "2".repeat(64),
        false,
        OffsetDateTime.now(ZoneOffset.UTC));

    assertThat(result.decisao()).isEqualTo(DecisaoRiscoVisitante.ALLOW_LEVEL_1);
    assertThat(result.nivelEfetivo()).isEqualTo(NivelAcessoVisitante.REINFORCED);
    assertThat(result.exigeDocumento()).isFalse();
    assertThat(result.bloqueado()).isFalse();
  }

  @Test
  void scoreDeRevisaoConduzAoFallbackDocumental() {
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    properties.setReviewFlagScore(1);
    properties.setRequireLevel2Score(1);
    properties.setRequireLevel3Score(50);
    properties.setTempBlockScore(100);
    properties.setHardBlockScore(200);
    Fixture fixture = fixture(properties);

    var result = fixture.service.avaliar(
        "3".repeat(64),
        NivelAcessoVisitante.REINFORCED,
        EscopoConteudoVisitante.MIDIA_RESTRITA,
        "/anuncios/teste",
        UUID.randomUUID(),
        "4".repeat(64),
        false,
        OffsetDateTime.now(ZoneOffset.UTC));

    assertThat(result.decisao()).isEqualTo(DecisaoRiscoVisitante.REVIEW_FLAG);
    assertThat(result.nivelEfetivo()).isEqualTo(NivelAcessoVisitante.STRONG);
    assertThat(result.exigeDocumento()).isTrue();
    assertThat(result.bloqueado()).isFalse();
  }

  @Test
  void scoreCriticoBloqueiaSemFornecedorExterno() {
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    properties.setHardBlockScore(1);
    Fixture fixture = fixture(properties);

    var result = fixture.service.avaliar(
        "5".repeat(64),
        NivelAcessoVisitante.STRONG,
        EscopoConteudoVisitante.CONTEUDO_EXPLICITO,
        "/anuncios/teste",
        UUID.randomUUID(),
        "6".repeat(64),
        true,
        OffsetDateTime.now(ZoneOffset.UTC));

    assertThat(result.decisao()).isEqualTo(DecisaoRiscoVisitante.HARD_BLOCK);
    assertThat(result.nivelEfetivo()).isEqualTo(NivelAcessoVisitante.NONE);
    assertThat(result.bloqueado()).isTrue();
  }

  @Test
  void bloqueioTemporarioAtivoImpedeNovoAcessoDoMesmoVisitante() {
    ComplianceAgeGateProperties properties = new ComplianceAgeGateProperties();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    String sessionHash = "7".repeat(64);
    ComplianceVisitorRiskProfileEntity profile =
        ComplianceVisitorRiskProfileEntity.criar(sessionHash, now.minusMinutes(5));
    profile.registrarAvaliacao(
        properties.getTempBlockScore(),
        DecisaoRiscoVisitante.TEMP_BLOCK,
        EscopoConteudoVisitante.STORY,
        "RISCO_BLOQUEIO_TEMPORARIO",
        "/stories",
        UUID.randomUUID(),
        now.plusMinutes(15),
        null,
        now.minusMinutes(1));
    Fixture fixture = fixture(properties, profile);

    var result = fixture.service.avaliar(
        sessionHash,
        NivelAcessoVisitante.REINFORCED,
        EscopoConteudoVisitante.STORY,
        "/stories",
        UUID.randomUUID(),
        "8".repeat(64),
        false,
        now);

    assertThat(result.decisao()).isEqualTo(DecisaoRiscoVisitante.TEMP_BLOCK);
    assertThat(result.nivelEfetivo()).isEqualTo(NivelAcessoVisitante.NONE);
    assertThat(result.bloqueado()).isTrue();
  }

  private Fixture fixture(ComplianceAgeGateProperties properties) {
    return fixture(properties, null);
  }

  @Test
  void consultaBloqueiosTemporarioEDefinitivoSemRenovarPrazoOuContadores() {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-20T12:00:00Z");
    for (boolean hard : new boolean[] {false, true}) {
      var profile = ComplianceVisitorRiskProfileEntity.criar("7".repeat(64), now.minusMinutes(5));
      profile.registrarAvaliacao(200, hard ? DecisaoRiscoVisitante.HARD_BLOCK : DecisaoRiscoVisitante.TEMP_BLOCK,
          EscopoConteudoVisitante.STORY, "SINTETICO", "/stories", null,
          hard ? null : now.plusMinutes(5), hard ? now.plusMinutes(5) : null, now);
      var fixture = fixture(new ComplianceAgeGateProperties(), profile);
      int count = profile.getAcessosRestritos();
      assertThat(fixture.service.bloqueadaEm(profile.getSessionHash(), now)).isTrue();
      assertThat(fixture.service.bloqueadaEm(profile.getSessionHash(), now.plusMinutes(5))).isFalse();
      assertThat(profile.getAcessosRestritos()).isEqualTo(count);
      assertThat(hard ? profile.getBloqueadoDefinitivamenteAte() : profile.getBloqueadoTemporariamenteAte())
          .isEqualTo(now.plusMinutes(5));
    }
  }

  private Fixture fixture(
      ComplianceAgeGateProperties properties,
      ComplianceVisitorRiskProfileEntity existingProfile) {
    ComplianceVisitorChallengeRepository challengeRepository =
        mock(ComplianceVisitorChallengeRepository.class);
    ComplianceVisitorRiskProfileRepository profileRepository =
        mock(ComplianceVisitorRiskProfileRepository.class);
    ComplianceVisitorTokenRepository tokenRepository =
        mock(ComplianceVisitorTokenRepository.class);
    EventoVerificacaoEtariaRepository eventRepository =
        mock(EventoVerificacaoEtariaRepository.class);
    when(profileRepository.findBySessionHashForUpdate(any()))
        .thenReturn(Optional.ofNullable(existingProfile));
    when(profileRepository.findBySessionHash(any()))
        .thenReturn(Optional.ofNullable(existingProfile));
    when(profileRepository.save(any(ComplianceVisitorRiskProfileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(eventRepository.findTop200BySessionHashAndCriadoEmAfterOrderByCriadoEmAsc(
        any(),
        any())).thenReturn(List.of());
    ComplianceVisitorRiskService service = new ComplianceVisitorRiskService(
        challengeRepository,
        profileRepository,
        tokenRepository,
        eventRepository,
        properties);
    return new Fixture(service);
  }

  private record Fixture(ComplianceVisitorRiskService service) {
  }
}
