package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceChallengeContextService.Contexto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService.IssuedTokens;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorRiskService.RiscoResultado;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorAccessStatusDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeResponseDto;
import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorVerifyRequestDto;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EstadoPublicoAgeGate;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.MetodoVerificacaoEtaria;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceVisitorVerificationService {

  private static final DateTimeFormatter BIRTH_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  private final ComplianceVisitorChallengeRepository challengeRepository;
  private final ComplianceVisitorSessionService sessionService;
  private final ComplianceGlobalAgeGateService globalService;
  private final ComplianceChallengeContextService contextService;
  private final ComplianceVisitorRiskService riskService;
  private final ComplianceVisitorAccessService accessService;
  private final ComplianceVisitorAuditService auditService;
  private final ComplianceAgeGateProperties properties;
  private final MetricaPublicaHashService hashService;
  private final CpfVisitanteValidator cpfValidator;

  public ComplianceVisitorVerificationService(
      ComplianceVisitorChallengeRepository challengeRepository,
      ComplianceVisitorSessionService sessionService,
      ComplianceGlobalAgeGateService globalService,
      ComplianceChallengeContextService contextService,
      ComplianceVisitorRiskService riskService,
      ComplianceVisitorAccessService accessService,
      ComplianceVisitorAuditService auditService,
      ComplianceAgeGateProperties properties,
      MetricaPublicaHashService hashService,
      CpfVisitanteValidator cpfValidator) {
    this.challengeRepository = challengeRepository;
    this.sessionService = sessionService;
    this.globalService = globalService;
    this.contextService = contextService;
    this.riskService = riskService;
    this.accessService = accessService;
    this.auditService = auditService;
    this.properties = properties;
    this.hashService = hashService;
    this.cpfValidator = cpfValidator;
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public ChallengeResult iniciar(
      VisitorChallengeRequestDto request,
      HttpServletRequest httpRequest) {
    if (!globalService.aceito(httpRequest)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "aceite global necessario antes da verificacao reforcada");
    }
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(httpRequest);
    EscopoConteudoVisitante escopo = escopo(request == null ? null : request.scope());
    NivelAcessoVisitante solicitado = nivel(request == null ? null : request.level(), escopo);
    Contexto contexto = contextService.validar(request, escopo);
    String idempotenciaHash = idempotenciaHash(
        session.sessionHash(),
        request == null ? null : request.idempotencyKey());
    if (idempotenciaHash == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "identificador de idempotencia obrigatorio");
    }
    var existente = challengeRepository.findBySessionHashAndIdempotenciaHash(
        session.sessionHash(),
        idempotenciaHash);
    if (existente.isPresent()) {
      validarMesmoContexto(existente.get(), solicitado, escopo, contexto);
      return new ChallengeResult(toResponse(existente.get()), session.cookie());
    }
    long tentativas = challengeRepository.countBySessionHashAndCriadoEmAfter(
        session.sessionHash(),
        agora.minus(properties.riskWindow()));
    if (tentativas >= properties.getMaxAttemptsPerWindow()) {
      auditService.registrar(
          ResultadoVerificacaoEtaria.NEGADO,
          MetodoVerificacaoEtaria.DECLARACAO,
          contexto.anuncioId(),
          null,
          session.sessionHash(),
          EstadoPublicoAgeGate.BLOCKED.name(),
          escopo.name(),
          "RATE_LIMIT_CHALLENGE",
          null,
          httpRequest);
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "limite temporario de verificacoes atingido");
    }
    RiscoResultado risco = riskService.avaliar(
        session.sessionHash(),
        solicitado,
        escopo,
        contexto.rotaSanitizada(),
        contexto.anuncioId(),
        ipHash(httpRequest),
        false,
        agora);
    boolean explicit = escopo == EscopoConteudoVisitante.CONTEUDO_EXPLICITO;
    ComplianceVisitorChallengeEntity challenge =
        ComplianceVisitorChallengeEntity.criar(
            UUID.randomUUID(),
            session.sessionHash(),
            solicitado,
            risco.nivelEfetivo(),
            escopo,
            contexto.anuncioId(),
            contexto.midiaId(),
            contexto.storyReferencia(),
            contexto.rotaSanitizada(),
            risco.score(),
            risco.decisao(),
            risco.motivo(),
            explicit,
            risco.exigeDocumento(),
            idempotenciaHash,
            explicit ? 2 : 3,
            agora.plus(properties.challengeTtl()),
            agora);
    challengeRepository.save(challenge);
    auditService.registrar(
        risco.bloqueado()
            ? ResultadoVerificacaoEtaria.NEGADO
            : ResultadoVerificacaoEtaria.INDETERMINADO,
        MetodoVerificacaoEtaria.DECLARACAO,
        contexto.anuncioId(),
        challenge.getId(),
        session.sessionHash(),
        risco.bloqueado()
            ? EstadoPublicoAgeGate.BLOCKED.name()
            : EstadoPublicoAgeGate.CHALLENGE_ACTIVE.name(),
        escopo.name(),
        risco.motivo(),
        null,
        httpRequest);
    return new ChallengeResult(toResponse(challenge), session.cookie());
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public VerifyResult verificar(
      VisitorVerifyRequestDto request,
      HttpServletRequest httpRequest) {
    if (!globalService.aceito(httpRequest)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "aceite global necessario");
    }
    if (request == null || request.challengeId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "challenge obrigatorio");
    }
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    ComplianceVisitorSessionService.SessionContext session =
        sessionService.obterOuCriar(httpRequest);
    ComplianceVisitorChallengeEntity challenge = challengeRepository
        .findByIdForUpdate(request.challengeId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "challenge nao encontrado"));
    if (!session.sessionHash().equals(challenge.getSessionHash())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "challenge nao encontrado");
    }
    String verificacaoHash = idempotenciaHash(
        session.sessionHash(),
        request.idempotencyKey());
    if (verificacaoHash == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "identificador de idempotencia obrigatorio");
    }
    if (challenge.getStatus() == StatusChallengeVisitante.VERIFIED) {
      if (!verificacaoHash.equals(challenge.getVerificacaoIdempotenciaHash())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "challenge ja utilizado");
      }
      IssuedTokens tokens = accessService.emitir(challenge, session, agora);
      return VerifyResult.verificado(tokens);
    }
    if (challenge.getStatus() == StatusChallengeVisitante.DOCUMENT_PENDING
        && verificacaoHash.equals(challenge.getVerificacaoIdempotenciaHash())) {
      return VerifyResult.documentoPendente(
          statusDocumentoPendente(challenge),
          session.cookie());
    }
    if (challenge.expiradaEm(agora)) {
      challenge.expirar(agora);
      auditarFalha(challenge, session, "CHALLENGE_EXPIRADO", httpRequest);
      throw new ResponseStatusException(HttpStatus.GONE, "challenge expirado");
    }
    boolean documentoAprovado =
        challenge.getStatus() == StatusChallengeVisitante.DOCUMENT_APPROVED;
    if (challenge.getStatus() != StatusChallengeVisitante.ACTIVE && !documentoAprovado) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "challenge em estado incompativel");
    }

    DadosValidados dados = validarDados(request, challenge, session, agora, httpRequest);
    RiscoResultado risco = riskService.avaliar(
        session.sessionHash(),
        challenge.getNivelSolicitado(),
        challenge.getEscopo(),
        challenge.getRotaSanitizada(),
        challenge.getAnuncioId(),
        ipHash(httpRequest),
        dados.proximoDaMaioridade(),
        agora);
    challenge.atualizarRisco(
        risco.score(),
        risco.decisao(),
        risco.nivelEfetivo(),
        risco.motivo(),
        risco.exigeDocumento(),
        agora);
    if (risco.bloqueado()) {
      auditarFalha(challenge, session, risco.motivo(), httpRequest);
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "verificacao temporariamente bloqueada");
    }
    if (risco.exigeDocumento() && !documentoAprovado) {
      challenge.marcarDocumentoPendente(verificacaoHash, agora);
      auditService.registrar(
          ResultadoVerificacaoEtaria.INDETERMINADO,
          MetodoVerificacaoEtaria.DOCUMENTO,
          challenge.getAnuncioId(),
          challenge.getId(),
          session.sessionHash(),
          EstadoPublicoAgeGate.DOCUMENT_PENDING.name(),
          challenge.getEscopo().name(),
          "DOCUMENTO_NECESSARIO",
          "PENDING",
          httpRequest);
      return VerifyResult.documentoPendente(
          statusDocumentoPendente(challenge),
          session.cookie());
    }

    challenge.verificar(verificacaoHash, agora);
    riskService.registrarSucesso(session.sessionHash(), agora);
    IssuedTokens tokens = accessService.emitir(challenge, session, agora);
    auditService.registrar(
        ResultadoVerificacaoEtaria.PERMITIDO,
        MetodoVerificacaoEtaria.DECLARACAO,
        challenge.getAnuncioId(),
        challenge.getId(),
        session.sessionHash(),
        EstadoPublicoAgeGate.VERIFIED.name(),
        challenge.getEscopo().name(),
        "VERIFICACAO_APROVADA",
        documentoAprovado ? "APPROVED" : null,
        httpRequest);
    return VerifyResult.verificado(tokens);
  }

  private DadosValidados validarDados(
      VisitorVerifyRequestDto request,
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorSessionService.SessionContext session,
      OffsetDateTime agora,
      HttpServletRequest httpRequest) {
    LocalDate nascimento;
    LocalDate confirmacao;
    try {
      nascimento = LocalDate.parse(
          request.dataNascimento() == null ? "" : request.dataNascimento(),
          BIRTH_DATE_FORMAT);
      confirmacao = LocalDate.parse(
          request.confirmacaoDataNascimento() == null
              ? ""
              : request.confirmacaoDataNascimento(),
          BIRTH_DATE_FORMAT);
    } catch (DateTimeParseException exception) {
      falhar(challenge, session, "NASCIMENTO_INVALIDO", agora, httpRequest);
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "data de nascimento invalida");
    }
    if (!nascimento.equals(confirmacao)) {
      falhar(challenge, session, "NASCIMENTOS_DIVERGENTES", agora, httpRequest);
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "datas de nascimento divergentes");
    }
    LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
    if (nascimento.isAfter(hoje.minusYears(18))) {
      falhar(challenge, session, "MENOR_DE_IDADE", agora, httpRequest);
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "idade minima nao confirmada");
    }
    if (!cpfValidator.valido(request.cpf())) {
      falhar(challenge, session, "CPF_INVALIDO", agora, httpRequest);
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CPF invalido");
    }
    if (!Boolean.TRUE.equals(request.aceiteMaioridade())
        || !Boolean.TRUE.equals(request.aceiteConteudoRestrito())
        || !Boolean.TRUE.equals(request.aceitePrivacidade())) {
      falhar(challenge, session, "ACEITES_AUSENTES", agora, httpRequest);
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "aceites obrigatorios nao confirmados");
    }
    if (challenge.isExigeAceiteExplicito()
        && !Boolean.TRUE.equals(request.confirmacaoExplicita())) {
      falhar(challenge, session, "ACEITE_EXPLICITO_AUSENTE", agora, httpRequest);
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "confirmacao explicita obrigatoria");
    }
    return new DadosValidados(nascimento.isAfter(hoje.minusYears(19)));
  }

  private void falhar(
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorSessionService.SessionContext session,
      String motivo,
      OffsetDateTime agora,
      HttpServletRequest request) {
    boolean esgotado = challenge.registrarTentativaInvalida(motivo, agora);
    riskService.registrarFalha(session.sessionHash(), agora);
    auditService.registrar(
        ResultadoVerificacaoEtaria.NEGADO,
        MetodoVerificacaoEtaria.DECLARACAO,
        challenge.getAnuncioId(),
        challenge.getId(),
        session.sessionHash(),
        esgotado
            ? EstadoPublicoAgeGate.RETRY.name()
            : EstadoPublicoAgeGate.CHALLENGE_ACTIVE.name(),
        challenge.getEscopo().name(),
        motivo,
        null,
        request);
  }

  private void auditarFalha(
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorSessionService.SessionContext session,
      String motivo,
      HttpServletRequest request) {
    auditService.registrar(
        ResultadoVerificacaoEtaria.NEGADO,
        MetodoVerificacaoEtaria.DECLARACAO,
        challenge.getAnuncioId(),
        challenge.getId(),
        session.sessionHash(),
        challenge.getStatus() == StatusChallengeVisitante.EXPIRED
            ? EstadoPublicoAgeGate.EXPIRED.name()
            : EstadoPublicoAgeGate.BLOCKED.name(),
        challenge.getEscopo().name(),
        motivo,
        null,
        request);
  }

  private VisitorChallengeResponseDto toResponse(
      ComplianceVisitorChallengeEntity challenge) {
    EstadoPublicoAgeGate state = switch (challenge.getStatus()) {
      case ACTIVE -> EstadoPublicoAgeGate.CHALLENGE_ACTIVE;
      case VERIFIED -> EstadoPublicoAgeGate.VERIFIED;
      case FAILED -> EstadoPublicoAgeGate.RETRY;
      case BLOCKED -> EstadoPublicoAgeGate.BLOCKED;
      case EXPIRED -> EstadoPublicoAgeGate.EXPIRED;
      case DOCUMENT_PENDING -> EstadoPublicoAgeGate.DOCUMENT_PENDING;
      case DOCUMENT_APPROVED -> EstadoPublicoAgeGate.DOCUMENT_APPROVED;
      case DOCUMENT_REJECTED -> EstadoPublicoAgeGate.DOCUMENT_REJECTED;
    };
    return new VisitorChallengeResponseDto(
        challenge.getId(),
        state.name(),
        challenge.getNivelEfetivo().name(),
        challenge.getEscopo().name(),
        challenge.getExpiraEm(),
        challenge.isExigeAceiteExplicito(),
        challenge.isExigeDocumento(),
        challenge.getMaxTentativas(),
        reasonPublic(challenge.getMotivoSanitizado()));
  }

  private VisitorAccessStatusDto statusDocumentoPendente(
      ComplianceVisitorChallengeEntity challenge) {
    return new VisitorAccessStatusDto(
        true,
        false,
        NivelAcessoVisitante.NONE.name(),
        null,
        false,
        NivelAcessoVisitante.NONE.name(),
        null,
        EstadoPublicoAgeGate.DOCUMENT_PENDING.name(),
        challenge.getRiscoScore(),
        challenge.getRiscoDecisao().name(),
        StatusChallengeVisitante.DOCUMENT_PENDING.name(),
        "Envie um documento sintetico valido para analise manual.");
  }

  private EscopoConteudoVisitante escopo(String value) {
    try {
      return EscopoConteudoVisitante.valueOf(
          value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "escopo protegido invalido");
    }
  }

  private NivelAcessoVisitante nivel(
      String value,
      EscopoConteudoVisitante escopo) {
    NivelAcessoVisitante requested;
    try {
      requested = value == null || value.isBlank()
          ? NivelAcessoVisitante.REINFORCED
          : NivelAcessoVisitante.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nivel de verificacao invalido");
    }
    if (requested == NivelAcessoVisitante.NONE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nivel de verificacao invalido");
    }
    if (escopo == EscopoConteudoVisitante.CONTEUDO_EXPLICITO) {
      return NivelAcessoVisitante.STRONG;
    }
    return requested.ordinal() < NivelAcessoVisitante.REINFORCED.ordinal()
        ? NivelAcessoVisitante.REINFORCED
        : requested;
  }

  private String idempotenciaHash(String sessionHash, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > 160 || !normalized.matches("[A-Za-z0-9._:-]+")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "identificador de idempotencia invalido");
    }
    return hashService.hash("compliance-idempotencia", sessionHash + "|" + normalized);
  }

  private void validarMesmoContexto(
      ComplianceVisitorChallengeEntity challenge,
      NivelAcessoVisitante nivel,
      EscopoConteudoVisitante escopo,
      Contexto contexto) {
    if (challenge.getNivelSolicitado() != nivel
        || challenge.getEscopo() != escopo
        || !java.util.Objects.equals(challenge.getAnuncioId(), contexto.anuncioId())
        || !java.util.Objects.equals(challenge.getAnuncioMidiaId(), contexto.midiaId())
        || !java.util.Objects.equals(challenge.getStoryReferencia(), contexto.storyReferencia())
        || !java.util.Objects.equals(challenge.getRotaSanitizada(), contexto.rotaSanitizada())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "identificador de idempotencia usado em outro contexto");
    }
  }

  private String ipHash(HttpServletRequest request) {
    return hashService.hash(
        "ip",
        request == null ? null : request.getRemoteAddr());
  }

  private String reasonPublic(String reason) {
    if (reason == null) {
      return null;
    }
    return switch (reason) {
      case "BLOQUEIO_RISCO_ATIVO", "BLOQUEIO_TEMPORARIO_ATIVO",
          "RISCO_BLOQUEIO_DEFINITIVO", "RISCO_BLOQUEIO_TEMPORARIO" ->
          "Verificacao temporariamente indisponivel.";
      case "RISCO_REVISAO_DOCUMENTAL", "DOCUMENTO_NECESSARIO" ->
          "Analise documental necessaria.";
      case "DOCUMENTO_REJEITADO" -> "Documento rejeitado. Envie um novo arquivo valido.";
      default -> null;
    };
  }

  private record DadosValidados(boolean proximoDaMaioridade) {
  }

  public record ChallengeResult(
      VisitorChallengeResponseDto response,
      ResponseCookie sessionCookie) {
  }

  public record VerifyResult(
      VisitorAccessStatusDto status,
      ResponseCookie sessionCookie,
      ResponseCookie generalCookie,
      ResponseCookie explicitCookie,
      HttpStatus httpStatus) {

    static VerifyResult verificado(IssuedTokens tokens) {
      return new VerifyResult(
          tokens.status(),
          tokens.sessionCookie(),
          tokens.generalCookie(),
          tokens.explicitCookie(),
          HttpStatus.OK);
    }

    static VerifyResult documentoPendente(
        VisitorAccessStatusDto status,
        ResponseCookie sessionCookie) {
      return new VerifyResult(
          status,
          sessionCookie,
          null,
          null,
          HttpStatus.ACCEPTED);
    }
  }
}
