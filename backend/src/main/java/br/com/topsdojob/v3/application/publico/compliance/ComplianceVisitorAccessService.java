package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceSignedCookieService.TipoTokenAssinado;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorAccessStatusDto;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoTokenVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EstadoPublicoAgeGate;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusTokenVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorTokenEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorDocumentoRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceVisitorAccessService {

  private final ComplianceSignedCookieService signedCookieService;
  private final ComplianceVisitorSessionService sessionService;
  private final ComplianceGlobalAgeGateService globalService;
  private final ComplianceAgeGateProperties properties;
  private final ComplianceVisitorTokenRepository tokenRepository;
  private final ComplianceVisitorChallengeRepository challengeRepository;
  private final ComplianceVisitorDocumentoRepository documentoRepository;
  private final MetricaPublicaHashService hashService;

  public ComplianceVisitorAccessService(
      ComplianceSignedCookieService signedCookieService,
      ComplianceVisitorSessionService sessionService,
      ComplianceGlobalAgeGateService globalService,
      ComplianceAgeGateProperties properties,
      ComplianceVisitorTokenRepository tokenRepository,
      ComplianceVisitorChallengeRepository challengeRepository,
      ComplianceVisitorDocumentoRepository documentoRepository,
      MetricaPublicaHashService hashService) {
    this.signedCookieService = signedCookieService;
    this.sessionService = sessionService;
    this.globalService = globalService;
    this.properties = properties;
    this.tokenRepository = tokenRepository;
    this.challengeRepository = challengeRepository;
    this.documentoRepository = documentoRepository;
    this.hashService = hashService;
  }

  @Transactional(readOnly = true)
  public StatusResult status(HttpServletRequest request) {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(request);
    boolean globalAccepted = globalService.aceito(request);
    Optional<ComplianceVisitorTokenEntity> general = validar(
        sessionService.cookie(request, ComplianceSignedCookieService.ACCESS_COOKIE),
        TipoTokenAssinado.ACCESS,
        EscopoTokenVisitante.GENERAL,
        session.sessionHash(),
        session.userAgentHash(),
        agora);
    Optional<ComplianceVisitorTokenEntity> explicit = validar(
        sessionService.cookie(request, ComplianceSignedCookieService.EXPLICIT_ACCESS_COOKIE),
        TipoTokenAssinado.EXPLICIT,
        EscopoTokenVisitante.EXPLICIT,
        session.sessionHash(),
        session.userAgentHash(),
        agora);
    ComplianceVisitorChallengeEntity latest = challengeRepository
        .findTopBySessionHashOrderByAtualizadoEmDescCriadoEmDescIdDesc(
            session.sessionHash())
        .orElse(null);
    VisitorAccessStatusDto status = statusDto(
        globalAccepted,
        general.orElse(null),
        explicit.orElse(null),
        latest);
    return new StatusResult(status, session.cookie());
  }

  @Transactional(readOnly = true)
  public boolean autorizado(
      HttpServletRequest request,
      EscopoConteudoVisitante escopo) {
    if (!globalService.aceito(request)) {
      return false;
    }
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(request);
    if (session.nova()) {
      return false;
    }
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    if (escopo == EscopoConteudoVisitante.CONTEUDO_EXPLICITO) {
      return validar(
          sessionService.cookie(request, ComplianceSignedCookieService.EXPLICIT_ACCESS_COOKIE),
          TipoTokenAssinado.EXPLICIT,
          EscopoTokenVisitante.EXPLICIT,
          session.sessionHash(),
          session.userAgentHash(),
          agora).isPresent();
    }
    return validar(
        sessionService.cookie(request, ComplianceSignedCookieService.ACCESS_COOKIE),
        TipoTokenAssinado.ACCESS,
        EscopoTokenVisitante.GENERAL,
        session.sessionHash(),
        session.userAgentHash(),
        agora)
        .filter(token -> token.getNivelAcesso().ordinal()
            >= NivelAcessoVisitante.REINFORCED.ordinal())
        .isPresent();
  }

  @Transactional
  public IssuedTokens emitir(
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorSessionService.SessionContext session,
      OffsetDateTime agora) {
    OffsetDateTime emitidoEm = agora.withOffsetSameInstant(ZoneOffset.UTC).withNano(0);
    List<ComplianceVisitorTokenEntity> existentes = tokenRepository
        .findByChallengeIdAndStatus(challenge.getId(), StatusTokenVisitante.ACTIVE);
    if (!existentes.isEmpty()) {
      return reconstruir(challenge, session, existentes, emitidoEm);
    }

    Duration generalTtl = ttl(challenge.getNivelEfetivo());
    ComplianceVisitorTokenEntity general = criarToken(
        challenge,
        session,
        EscopoTokenVisitante.GENERAL,
        emitidoEm,
        emitidoEm.plus(generalTtl));
    tokenRepository.save(general);
    String rawGeneral = raw(general);
    ResponseCookie generalCookie = signedCookieService.cookie(
        ComplianceSignedCookieService.ACCESS_COOKIE,
        rawGeneral,
        generalTtl);

    ComplianceVisitorTokenEntity explicit = null;
    ResponseCookie explicitCookie = null;
    if (challenge.isExigeAceiteExplicito()) {
      explicit = criarToken(
          challenge,
          session,
          EscopoTokenVisitante.EXPLICIT,
          emitidoEm,
          emitidoEm.plus(properties.explicitTokenTtl()));
      tokenRepository.save(explicit);
      explicitCookie = signedCookieService.cookie(
          ComplianceSignedCookieService.EXPLICIT_ACCESS_COOKIE,
          raw(explicit),
          properties.explicitTokenTtl());
    }
    return new IssuedTokens(
        statusDto(true, general, explicit, challenge),
        session.cookie(),
        generalCookie,
        explicitCookie);
  }

  @Transactional
  public RevokeResult revogar(HttpServletRequest request, String motivo) {
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(request);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    tokenRepository.findBySessionHashAndStatusForUpdate(
            session.sessionHash(),
            StatusTokenVisitante.ACTIVE)
        .forEach(token -> token.revogar(motivo, agora));
    return new RevokeResult(
        new VisitorAccessStatusDto(
            globalService.aceito(request),
            false,
            NivelAcessoVisitante.NONE.name(),
            null,
            false,
            NivelAcessoVisitante.NONE.name(),
            null,
            globalService.aceito(request)
                ? EstadoPublicoAgeGate.GLOBAL_ACEITO.name()
                : EstadoPublicoAgeGate.GLOBAL_NAO_ACEITO.name(),
            null,
            null,
            null,
            "ACESSO_REVOGADO"),
        session.cookie(),
        signedCookieService.expirar(ComplianceSignedCookieService.ACCESS_COOKIE),
        signedCookieService.expirar(ComplianceSignedCookieService.EXPLICIT_ACCESS_COOKIE));
  }

  private Optional<ComplianceVisitorTokenEntity> validar(
      String raw,
      TipoTokenAssinado tipo,
      EscopoTokenVisitante escopo,
      String sessionHash,
      String userAgentHash,
      OffsetDateTime agora) {
    var signed = signedCookieService.validar(raw, tipo, agora);
    if (signed.isEmpty()) {
      return Optional.empty();
    }
    String tokenHash = hashService.hash("compliance-token", raw);
    Optional<ComplianceVisitorTokenEntity> persisted = tokenRepository.findByTokenHash(tokenHash);
    if (persisted.isEmpty()) {
      return Optional.empty();
    }
    ComplianceVisitorTokenEntity token = persisted.get();
    if (!token.getId().equals(signed.get().id())
        || !sessionHash.equals(token.getSessionHash())
        || !userAgentHash.equals(token.getUserAgentHash())
        || token.getEscopoToken() != escopo
        || !token.getNivelAcesso().name().equals(signed.get().nivel())
        || !token.getEscopoToken().name().equals(signed.get().escopo())) {
      return Optional.empty();
    }
    return token.ativaEm(agora) ? Optional.of(token) : Optional.empty();
  }

  private ComplianceVisitorTokenEntity criarToken(
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorSessionService.SessionContext session,
      EscopoTokenVisitante escopo,
      OffsetDateTime emitidoEm,
      OffsetDateTime expiraEm) {
    UUID id = UUID.randomUUID();
    String raw = signedCookieService.emitir(
        escopo == EscopoTokenVisitante.EXPLICIT
            ? TipoTokenAssinado.EXPLICIT
            : TipoTokenAssinado.ACCESS,
        id,
        emitidoEm,
        expiraEm,
        escopo.name(),
        challenge.getNivelEfetivo().name());
    return ComplianceVisitorTokenEntity.emitir(
        id,
        hashService.hash("compliance-token", raw),
        session.sessionHash(),
        session.userAgentHash(),
        challenge.getId(),
        escopo,
        challenge.getNivelEfetivo(),
        challenge.getEscopo(),
        challenge.getRiscoScore(),
        challenge.getRiscoDecisao(),
        emitidoEm,
        expiraEm);
  }

  private IssuedTokens reconstruir(
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorSessionService.SessionContext session,
      List<ComplianceVisitorTokenEntity> existentes,
      OffsetDateTime agora) {
    ComplianceVisitorTokenEntity general = existentes.stream()
        .filter(item -> item.getEscopoToken() == EscopoTokenVisitante.GENERAL)
        .filter(item -> item.ativaEm(agora))
        .filter(item -> session.sessionHash().equals(item.getSessionHash()))
        .filter(item -> session.userAgentHash().equals(item.getUserAgentHash()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.GONE,
            "acesso expirado; inicie uma nova verificacao"));
    ComplianceVisitorTokenEntity explicit = existentes.stream()
        .filter(item -> item.getEscopoToken() == EscopoTokenVisitante.EXPLICIT)
        .filter(item -> item.ativaEm(agora))
        .filter(item -> session.sessionHash().equals(item.getSessionHash()))
        .filter(item -> session.userAgentHash().equals(item.getUserAgentHash()))
        .findFirst()
        .orElse(null);
    if (challenge.isExigeAceiteExplicito() && explicit == null) {
      throw new ResponseStatusException(
          HttpStatus.GONE,
          "acesso explicito expirado; inicie uma nova verificacao");
    }
    return new IssuedTokens(
        statusDto(true, general, explicit, challenge),
        session.cookie(),
        signedCookieService.cookie(
            ComplianceSignedCookieService.ACCESS_COOKIE,
            raw(general),
            Duration.between(agora, general.getExpiraEm())),
        explicit == null
            ? null
            : signedCookieService.cookie(
                ComplianceSignedCookieService.EXPLICIT_ACCESS_COOKIE,
                raw(explicit),
                Duration.between(agora, explicit.getExpiraEm())));
  }

  private String raw(ComplianceVisitorTokenEntity token) {
    return signedCookieService.emitir(
        token.getEscopoToken() == EscopoTokenVisitante.EXPLICIT
            ? TipoTokenAssinado.EXPLICIT
            : TipoTokenAssinado.ACCESS,
        token.getId(),
        token.getEmitidoEm(),
        token.getExpiraEm(),
        token.getEscopoToken().name(),
        token.getNivelAcesso().name());
  }

  private Duration ttl(NivelAcessoVisitante nivel) {
    return switch (nivel) {
      case LIGHT -> properties.lightTokenTtl();
      case REINFORCED -> properties.reinforcedTokenTtl();
      case STRONG -> properties.strongTokenTtl();
      case NONE -> throw new IllegalArgumentException("nivel de acesso invalido");
    };
  }

  private VisitorAccessStatusDto statusDto(
      boolean globalAccepted,
      ComplianceVisitorTokenEntity general,
      ComplianceVisitorTokenEntity explicit,
      ComplianceVisitorChallengeEntity latest) {
    String state;
    if (latest == null) {
      state = globalAccepted
          ? EstadoPublicoAgeGate.GLOBAL_ACEITO.name()
          : EstadoPublicoAgeGate.GLOBAL_NAO_ACEITO.name();
    } else if (latest.getStatus() == StatusChallengeVisitante.VERIFIED
        && general == null) {
      state = EstadoPublicoAgeGate.EXPIRED.name();
    } else {
      state = estado(latest.getStatus()).name();
    }
    return new VisitorAccessStatusDto(
        globalAccepted,
        globalAccepted && general != null,
        general == null ? NivelAcessoVisitante.NONE.name() : general.getNivelAcesso().name(),
        general == null ? null : general.getExpiraEm(),
        globalAccepted && explicit != null,
        explicit == null ? NivelAcessoVisitante.NONE.name() : explicit.getNivelAcesso().name(),
        explicit == null ? null : explicit.getExpiraEm(),
        state,
        latest == null ? null : latest.getRiscoScore(),
        latest == null ? null : latest.getRiscoDecisao().name(),
        latest == null || !latest.getStatus().name().startsWith("DOCUMENT_")
            ? null
            : latest.getStatus().name(),
        latest == null ? null : motivoPublico(latest));
  }

  private String motivoPublico(ComplianceVisitorChallengeEntity challenge) {
    return switch (challenge.getStatus()) {
      case DOCUMENT_PENDING -> "Documento em analise.";
      case DOCUMENT_APPROVED ->
          "Documento aprovado. Repita a verificacao para emitir o acesso.";
      case DOCUMENT_REJECTED -> documentoRepository
          .findTopByChallengeIdOrderByCriadoEmDesc(challenge.getId())
          .map(item -> item.getMotivoPublicoSanitizado())
          .filter(item -> !item.isBlank())
          .orElse("Documento rejeitado. Envie um novo arquivo valido.");
      case BLOCKED -> "Verificacao temporariamente indisponivel.";
      case EXPIRED -> "A verificacao expirou. Inicie novamente.";
      case FAILED -> "Nao foi possivel confirmar os dados. Tente novamente.";
      case ACTIVE, VERIFIED -> null;
    };
  }

  private EstadoPublicoAgeGate estado(StatusChallengeVisitante status) {
    return switch (status) {
      case ACTIVE -> EstadoPublicoAgeGate.CHALLENGE_ACTIVE;
      case VERIFIED -> EstadoPublicoAgeGate.VERIFIED;
      case FAILED -> EstadoPublicoAgeGate.RETRY;
      case BLOCKED -> EstadoPublicoAgeGate.BLOCKED;
      case EXPIRED -> EstadoPublicoAgeGate.EXPIRED;
      case DOCUMENT_PENDING -> EstadoPublicoAgeGate.DOCUMENT_PENDING;
      case DOCUMENT_APPROVED -> EstadoPublicoAgeGate.DOCUMENT_APPROVED;
      case DOCUMENT_REJECTED -> EstadoPublicoAgeGate.DOCUMENT_REJECTED;
    };
  }

  public record StatusResult(
      VisitorAccessStatusDto status,
      ResponseCookie sessionCookie) {
  }

  public record IssuedTokens(
      VisitorAccessStatusDto status,
      ResponseCookie sessionCookie,
      ResponseCookie generalCookie,
      ResponseCookie explicitCookie) {
  }

  public record RevokeResult(
      VisitorAccessStatusDto status,
      ResponseCookie sessionCookie,
      ResponseCookie generalExpiredCookie,
      ResponseCookie explicitExpiredCookie) {
  }
}
