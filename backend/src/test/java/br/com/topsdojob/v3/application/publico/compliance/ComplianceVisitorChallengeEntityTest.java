package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComplianceVisitorChallengeEntityTest {

  @Test
  void controlaTentativasExpiracaoEAntiReplay() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity challenge = challenge(now, 2);

    assertThat(challenge.registrarTentativaInvalida("CPF_INVALIDO", now.plusSeconds(1)))
        .isFalse();
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.ACTIVE);
    assertThat(challenge.registrarTentativaInvalida("CPF_INVALIDO", now.plusSeconds(2)))
        .isTrue();
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.FAILED);

    ComplianceVisitorChallengeEntity expiring = challenge(now.minusMinutes(20), 3);
    assertThat(expiring.expiradaEm(now)).isTrue();
    expiring.expirar(now);
    assertThat(expiring.getStatus()).isEqualTo(StatusChallengeVisitante.EXPIRED);
  }

  @Test
  void documentoAprovadoNaoEmiteAcessoNemVerificaChallenge() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity challenge = challenge(now, 3);

    challenge.marcarDocumentoPendente("a".repeat(64), now.plusSeconds(1));
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.DOCUMENT_PENDING);
    assertThat(challenge.getVerificacaoIdempotenciaHash()).isEqualTo("a".repeat(64));

    OffsetDateTime retomadaExpiraEm = now.plusDays(2);
    challenge.marcarDocumentoAprovado(now.plusSeconds(2), retomadaExpiraEm);
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.DOCUMENT_APPROVED);
    assertThat(challenge.getVerificadoEm()).isNull();
    assertThat(challenge.getExpiraEm()).isEqualTo(retomadaExpiraEm);

    challenge.verificar("b".repeat(64), now.plusSeconds(3));
    assertThat(challenge.getStatus()).isEqualTo(StatusChallengeVisitante.VERIFIED);
    assertThat(challenge.getVerificadoEm()).isNotNull();
  }

  private ComplianceVisitorChallengeEntity challenge(OffsetDateTime now, int maxAttempts) {
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
        "2".repeat(64),
        maxAttempts,
        now.plusMinutes(10),
        now);
  }
}
