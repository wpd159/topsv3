package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusTokenVisitante;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorRiskProfileEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorRiskProfileRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorTokenRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceVisitorRiskService {

  private static final List<StatusChallengeVisitante> FAILURE_STATUSES = List.of(
      StatusChallengeVisitante.FAILED,
      StatusChallengeVisitante.BLOCKED,
      StatusChallengeVisitante.EXPIRED);

  private final ComplianceVisitorChallengeRepository challengeRepository;
  private final ComplianceVisitorRiskProfileRepository profileRepository;
  private final ComplianceVisitorTokenRepository tokenRepository;
  private final EventoVerificacaoEtariaRepository eventoRepository;
  private final ComplianceAgeGateProperties properties;

  public ComplianceVisitorRiskService(
      ComplianceVisitorChallengeRepository challengeRepository,
      ComplianceVisitorRiskProfileRepository profileRepository,
      ComplianceVisitorTokenRepository tokenRepository,
      EventoVerificacaoEtariaRepository eventoRepository,
      ComplianceAgeGateProperties properties) {
    this.challengeRepository = challengeRepository;
    this.profileRepository = profileRepository;
    this.tokenRepository = tokenRepository;
    this.eventoRepository = eventoRepository;
    this.properties = properties;
  }

  @Transactional
  public RiscoResultado avaliar(
      String sessionHash,
      NivelAcessoVisitante solicitado,
      EscopoConteudoVisitante escopo,
      String rota,
      UUID anuncioId,
      String ipHash,
      boolean proximoDaMaioridade,
      OffsetDateTime agora) {
    ComplianceVisitorRiskProfileEntity profile = profileRepository
        .findBySessionHashForUpdate(sessionHash)
        .orElseGet(() -> profileRepository.save(
            ComplianceVisitorRiskProfileEntity.criar(sessionHash, agora)));

    if (profile.bloqueioDefinitivoAtivo(agora)) {
      return registrar(
          profile,
          properties.getHardBlockScore(),
          DecisaoRiscoVisitante.HARD_BLOCK,
          solicitado,
          escopo,
          "BLOQUEIO_RISCO_ATIVO",
          rota,
          anuncioId,
          agora);
    }
    if (profile.bloqueioTemporarioAtivo(agora)) {
      return registrar(
          profile,
          Math.max(properties.getTempBlockScore(), profile.getScoreAtual()),
          DecisaoRiscoVisitante.TEMP_BLOCK,
          solicitado,
          escopo,
          "BLOQUEIO_TEMPORARIO_ATIVO",
          rota,
          anuncioId,
          agora);
    }

    if (!properties.isRiskEngineEnabled()) {
      return registrar(
          profile,
          0,
          DecisaoRiscoVisitante.ALLOW_LEVEL_1,
          solicitado,
          escopo,
          "RISCO_DESABILITADO",
          rota,
          anuncioId,
          agora);
    }

    OffsetDateTime janela = agora.minus(properties.riskWindow());
    OffsetDateTime rajada = agora.minus(properties.restrictedBurstWindow());
    long tentativas = challengeRepository.countBySessionHashAndCriadoEmAfter(sessionHash, janela);
    long falhas = challengeRepository.countBySessionHashAndStatusInAndAtualizadoEmAfter(
        sessionHash,
        FAILURE_STATUSES,
        janela);
    long explicitos = challengeRepository.countBySessionHashAndEscopoAndCriadoEmAfter(
        sessionHash,
        EscopoConteudoVisitante.CONTEUDO_EXPLICITO,
        janela);
    long acessosRestritos = challengeRepository.countBySessionHashAndCriadoEmAfter(
        sessionHash,
        rajada);
    long anunciosDistintos = challengeRepository.countDistinctAnunciosRecentes(
        sessionHash,
        rajada);
    long tokensAtivos = tokenRepository.countBySessionHashAndStatusAndExpiraEmAfter(
        sessionHash,
        StatusTokenVisitante.ACTIVE,
        agora);
    long tokensEmitidos = tokenRepository.countBySessionHashAndEmitidoEmAfter(
        sessionHash,
        janela);
    long falhasPorIp = ipHash == null
        ? 0
        : eventoRepository.countByIpHashAndResultadoAndCriadoEmAfter(
            ipHash,
            ResultadoVerificacaoEtaria.NEGADO,
            janela);
    List<EventoVerificacaoEtariaEntity> eventosSessao =
        eventoRepository.findTop200BySessionHashAndCriadoEmAfterOrderByCriadoEmAsc(
            sessionHash,
            janela);

    int score = Math.max(0, profile.getReputacaoInterna() * -1);
    if (tentativas >= properties.getMaxAttemptsPerWindow()) {
      score += 35;
    } else if (tentativas >= 3) {
      score += 15;
    }
    long falhasEfetivas = Math.max(falhas, profile.getFalhasConsecutivas());
    if (falhasEfetivas >= properties.getMaxFailuresPerWindow()) {
      score += 40;
    } else if (falhasEfetivas >= 2) {
      score += 18;
    }
    if (falhasPorIp >= properties.getMaxFailuresPerWindow() + 1L) {
      score += 25;
    }
    if (explicitos >= properties.getMaxExplicitAccessPerWindow()) {
      score += 45;
    } else if (explicitos >= 6) {
      score += 14;
    }
    if (acessosRestritos >= properties.getMaxRestrictedBurstAccesses()) {
      score += 20;
    }
    if (anunciosDistintos >= 7) {
      score += 12;
    }
    if (padraoNavegacaoAutomatizada(eventosSessao)) {
      score += 10;
    }
    if (tokensEmitidos >= 5) {
      score += 8;
    }
    if (proximoDaMaioridade) {
      score += 18;
    }
    score += escopo == EscopoConteudoVisitante.CONTEUDO_EXPLICITO ? 10 : 3;
    if (tokensAtivos > 0) {
      score = Math.max(0, (int) Math.floor(score * 0.3d) - 10);
    }

    DecisaoRiscoVisitante decisao;
    String motivo;
    if (score >= properties.getHardBlockScore()) {
      decisao = DecisaoRiscoVisitante.HARD_BLOCK;
      motivo = "RISCO_BLOQUEIO_DEFINITIVO";
    } else if (score >= properties.getTempBlockScore()) {
      boolean escalado = profile.getDecisaoAtual() == DecisaoRiscoVisitante.REVIEW_FLAG
          || profile.getDecisaoAtual() == DecisaoRiscoVisitante.REQUIRE_LEVEL_3
          || profile.getDecisaoAtual() == DecisaoRiscoVisitante.TEMP_BLOCK
          || profile.getDecisaoAtual() == DecisaoRiscoVisitante.HARD_BLOCK
          || profile.isSinalizadoRevisao();
      if (escalado && profile.getScoreAtual() >= properties.getReviewFlagScore()) {
        decisao = DecisaoRiscoVisitante.TEMP_BLOCK;
        motivo = "RISCO_BLOQUEIO_TEMPORARIO";
      } else {
        decisao = DecisaoRiscoVisitante.REVIEW_FLAG;
        motivo = "RISCO_REVISAO_DOCUMENTAL";
      }
    } else if (score >= properties.getReviewFlagScore()) {
      decisao = DecisaoRiscoVisitante.REVIEW_FLAG;
      motivo = "RISCO_REVISAO_DOCUMENTAL";
    } else if (score >= properties.getRequireLevel3Score()) {
      decisao = DecisaoRiscoVisitante.REQUIRE_LEVEL_3;
      motivo = "RISCO_NIVEL_FORTE";
    } else if (score >= properties.getRequireLevel2Score()) {
      decisao = DecisaoRiscoVisitante.REQUIRE_LEVEL_2;
      motivo = "RISCO_NIVEL_REFORCADO";
    } else {
      decisao = DecisaoRiscoVisitante.ALLOW_LEVEL_1;
      motivo = "RISCO_APROVADO";
    }
    return registrar(
        profile,
        score,
        decisao,
        solicitado,
        escopo,
        motivo,
        rota,
        anuncioId,
        agora);
  }

  @Transactional
  public void registrarFalha(String sessionHash, OffsetDateTime agora) {
    profileRepository.findBySessionHashForUpdate(sessionHash)
        .ifPresent(profile -> profile.registrarFalha(agora));
  }

  @Transactional
  public void registrarSucesso(String sessionHash, OffsetDateTime agora) {
    profileRepository.findBySessionHashForUpdate(sessionHash)
        .ifPresent(profile -> profile.registrarSucesso(agora));
  }

  private RiscoResultado registrar(
      ComplianceVisitorRiskProfileEntity profile,
      int score,
      DecisaoRiscoVisitante decisao,
      NivelAcessoVisitante solicitado,
      EscopoConteudoVisitante escopo,
      String motivo,
      String rota,
      UUID anuncioId,
      OffsetDateTime agora) {
    NivelAcessoVisitante efetivo = switch (decisao) {
      case REQUIRE_LEVEL_3, REVIEW_FLAG -> NivelAcessoVisitante.STRONG;
      case REQUIRE_LEVEL_2 -> max(
          solicitado,
          NivelAcessoVisitante.REINFORCED);
      case TEMP_BLOCK, HARD_BLOCK -> NivelAcessoVisitante.NONE;
      case ALLOW_LEVEL_1 -> solicitado;
    };
    OffsetDateTime temporario = decisao == DecisaoRiscoVisitante.TEMP_BLOCK
        ? agora.plus(properties.tempBlockDuration())
        : null;
    OffsetDateTime definitivo = decisao == DecisaoRiscoVisitante.HARD_BLOCK
        ? agora.plus(properties.hardBlockDuration())
        : null;
    profile.registrarAvaliacao(
        score,
        decisao,
        escopo,
        motivo,
        rota,
        anuncioId,
        temporario,
        definitivo,
        agora);
    return new RiscoResultado(
        score,
        decisao,
        efetivo,
        motivo,
        decisao == DecisaoRiscoVisitante.REVIEW_FLAG
            && properties.isDocumentOnDemandEnabled(),
        decisao == DecisaoRiscoVisitante.TEMP_BLOCK
            || decisao == DecisaoRiscoVisitante.HARD_BLOCK);
  }

  private NivelAcessoVisitante max(
      NivelAcessoVisitante first,
      NivelAcessoVisitante second) {
    return first.ordinal() >= second.ordinal() ? first : second;
  }

  private boolean padraoNavegacaoAutomatizada(
      List<EventoVerificacaoEtariaEntity> eventos) {
    if (eventos == null || eventos.size() < 4) {
      return false;
    }
    List<EventoVerificacaoEtariaEntity> ordenados = eventos.stream()
        .filter(item -> item.getCriadoEm() != null)
        .sorted(Comparator.comparing(EventoVerificacaoEtariaEntity::getCriadoEm))
        .toList();
    int intervalosMuitoCurtos = 0;
    for (int index = 1; index < ordenados.size(); index++) {
      long segundos = Duration.between(
          ordenados.get(index - 1).getCriadoEm(),
          ordenados.get(index).getCriadoEm()).getSeconds();
      if (segundos >= 0 && segundos <= 5) {
        intervalosMuitoCurtos++;
      }
    }
    return intervalosMuitoCurtos >= 3;
  }

  public record RiscoResultado(
      int score,
      DecisaoRiscoVisitante decisao,
      NivelAcessoVisitante nivelEfetivo,
      String motivo,
      boolean exigeDocumento,
      boolean bloqueado) {
  }
}
