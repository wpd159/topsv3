package br.com.topsdojob.v3.persistence.entity.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_visitor_challenge")
public class ComplianceVisitorChallengeEntity {

  protected ComplianceVisitorChallengeEntity() {
  }

  @Id
  private UUID id;

  @Column(name = "session_hash", nullable = false)
  private String sessionHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "nivel_solicitado", nullable = false)
  private NivelAcessoVisitante nivelSolicitado;

  @Enumerated(EnumType.STRING)
  @Column(name = "nivel_efetivo", nullable = false)
  private NivelAcessoVisitante nivelEfetivo;

  @Enumerated(EnumType.STRING)
  @Column(name = "escopo", nullable = false)
  private EscopoConteudoVisitante escopo;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "anuncio_midia_id")
  private UUID anuncioMidiaId;

  @Column(name = "story_referencia")
  private String storyReferencia;

  @Column(name = "rota_sanitizada")
  private String rotaSanitizada;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private StatusChallengeVisitante status;

  @Column(name = "tentativas", nullable = false)
  private int tentativas;

  @Column(name = "max_tentativas", nullable = false)
  private int maxTentativas;

  @Column(name = "risco_score", nullable = false)
  private int riscoScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "risco_decisao", nullable = false)
  private DecisaoRiscoVisitante riscoDecisao;

  @Column(name = "motivo_sanitizado")
  private String motivoSanitizado;

  @Column(name = "exige_aceite_explicito", nullable = false)
  private boolean exigeAceiteExplicito;

  @Column(name = "exige_documento", nullable = false)
  private boolean exigeDocumento;

  @Column(name = "idempotencia_hash", nullable = false)
  private String idempotenciaHash;

  @Column(name = "verificacao_idempotencia_hash")
  private String verificacaoIdempotenciaHash;

  @Column(name = "expira_em", nullable = false)
  private OffsetDateTime expiraEm;

  @Column(name = "verificado_em")
  private OffsetDateTime verificadoEm;

  @Column(name = "criado_em", nullable = false)
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao", nullable = false)
  private long versao;

  public static ComplianceVisitorChallengeEntity criar(
      UUID id,
      String sessionHash,
      NivelAcessoVisitante nivelSolicitado,
      NivelAcessoVisitante nivelEfetivo,
      EscopoConteudoVisitante escopo,
      UUID anuncioId,
      UUID anuncioMidiaId,
      String storyReferencia,
      String rotaSanitizada,
      int riscoScore,
      DecisaoRiscoVisitante riscoDecisao,
      String motivoSanitizado,
      boolean exigeAceiteExplicito,
      boolean exigeDocumento,
      String idempotenciaHash,
      int maxTentativas,
      OffsetDateTime expiraEm,
      OffsetDateTime agora) {
    ComplianceVisitorChallengeEntity entity = new ComplianceVisitorChallengeEntity();
    entity.id = id;
    entity.sessionHash = sessionHash;
    entity.nivelSolicitado = nivelSolicitado;
    entity.nivelEfetivo = nivelEfetivo;
    entity.escopo = escopo;
    entity.anuncioId = anuncioId;
    entity.anuncioMidiaId = anuncioMidiaId;
    entity.storyReferencia = storyReferencia;
    entity.rotaSanitizada = rotaSanitizada;
    entity.status = riscoDecisao == DecisaoRiscoVisitante.TEMP_BLOCK
            || riscoDecisao == DecisaoRiscoVisitante.HARD_BLOCK
        ? StatusChallengeVisitante.BLOCKED
        : StatusChallengeVisitante.ACTIVE;
    entity.tentativas = 0;
    entity.maxTentativas = maxTentativas;
    entity.riscoScore = Math.max(0, riscoScore);
    entity.riscoDecisao = riscoDecisao;
    entity.motivoSanitizado = motivoSanitizado;
    entity.exigeAceiteExplicito = exigeAceiteExplicito;
    entity.exigeDocumento = exigeDocumento;
    entity.idempotenciaHash = idempotenciaHash;
    entity.expiraEm = expiraEm;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public boolean expiradaEm(OffsetDateTime agora) {
    return !expiraEm.isAfter(agora);
  }

  public void expirar(OffsetDateTime agora) {
    if (status == StatusChallengeVisitante.ACTIVE) {
      status = StatusChallengeVisitante.EXPIRED;
      motivoSanitizado = "CHALLENGE_EXPIRADO";
      atualizadoEm = agora;
    }
  }

  public boolean registrarTentativaInvalida(String motivo, OffsetDateTime agora) {
    tentativas++;
    motivoSanitizado = motivo;
    atualizadoEm = agora;
    if (tentativas >= maxTentativas) {
      status = StatusChallengeVisitante.FAILED;
      return true;
    }
    return false;
  }

  public void atualizarRisco(
      int score,
      DecisaoRiscoVisitante decisao,
      NivelAcessoVisitante nivel,
      String motivo,
      boolean documento,
      OffsetDateTime agora) {
    riscoScore = Math.max(0, score);
    riscoDecisao = decisao;
    nivelEfetivo = nivel;
    motivoSanitizado = motivo;
    exigeDocumento = documento;
    atualizadoEm = agora;
    if (decisao == DecisaoRiscoVisitante.TEMP_BLOCK
        || decisao == DecisaoRiscoVisitante.HARD_BLOCK) {
      status = StatusChallengeVisitante.BLOCKED;
    }
  }

  public void marcarDocumentoPendente(
      String verificacaoIdempotenciaHash,
      OffsetDateTime agora) {
    status = StatusChallengeVisitante.DOCUMENT_PENDING;
    exigeDocumento = true;
    this.verificacaoIdempotenciaHash = verificacaoIdempotenciaHash;
    motivoSanitizado = "DOCUMENTO_NECESSARIO";
    if (expiraEm.isBefore(agora.plusDays(1))) {
      expiraEm = agora.plusDays(1);
    }
    atualizadoEm = agora;
  }

  public void marcarDocumentoAprovado(
      OffsetDateTime agora,
      OffsetDateTime novaExpiracao) {
    if (novaExpiracao == null || !novaExpiracao.isAfter(agora)) {
      throw new IllegalArgumentException("expiracao de retomada invalida");
    }
    status = StatusChallengeVisitante.DOCUMENT_APPROVED;
    motivoSanitizado = "DOCUMENTO_APROVADO";
    if (expiraEm == null || expiraEm.isBefore(novaExpiracao)) {
      expiraEm = novaExpiracao;
    }
    atualizadoEm = agora;
  }

  public void marcarDocumentoRejeitado(String motivo, OffsetDateTime agora) {
    status = StatusChallengeVisitante.DOCUMENT_REJECTED;
    motivoSanitizado = motivo;
    atualizadoEm = agora;
  }

  public void reabrirSubmissaoDocumento(OffsetDateTime agora) {
    if (status != StatusChallengeVisitante.DOCUMENT_REJECTED
        && status != StatusChallengeVisitante.DOCUMENT_PENDING) {
      throw new IllegalStateException("challenge nao aceita documento");
    }
    status = StatusChallengeVisitante.DOCUMENT_PENDING;
    motivoSanitizado = "DOCUMENTO_REENVIADO";
    atualizadoEm = agora;
  }

  public void verificar(String verificacaoIdempotenciaHash, OffsetDateTime agora) {
    status = StatusChallengeVisitante.VERIFIED;
    this.verificacaoIdempotenciaHash = verificacaoIdempotenciaHash;
    verificadoEm = agora;
    motivoSanitizado = "VERIFICACAO_APROVADA";
    atualizadoEm = agora;
  }

  public UUID getId() {
    return id;
  }

  public String getSessionHash() {
    return sessionHash;
  }

  public NivelAcessoVisitante getNivelSolicitado() {
    return nivelSolicitado;
  }

  public NivelAcessoVisitante getNivelEfetivo() {
    return nivelEfetivo;
  }

  public EscopoConteudoVisitante getEscopo() {
    return escopo;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getAnuncioMidiaId() {
    return anuncioMidiaId;
  }

  public String getStoryReferencia() {
    return storyReferencia;
  }

  public String getRotaSanitizada() {
    return rotaSanitizada;
  }

  public StatusChallengeVisitante getStatus() {
    return status;
  }

  public int getTentativas() {
    return tentativas;
  }

  public int getMaxTentativas() {
    return maxTentativas;
  }

  public int getRiscoScore() {
    return riscoScore;
  }

  public DecisaoRiscoVisitante getRiscoDecisao() {
    return riscoDecisao;
  }

  public String getMotivoSanitizado() {
    return motivoSanitizado;
  }

  public boolean isExigeAceiteExplicito() {
    return exigeAceiteExplicito;
  }

  public boolean isExigeDocumento() {
    return exigeDocumento;
  }

  public String getIdempotenciaHash() {
    return idempotenciaHash;
  }

  public String getVerificacaoIdempotenciaHash() {
    return verificacaoIdempotenciaHash;
  }

  public OffsetDateTime getExpiraEm() {
    return expiraEm;
  }

  public OffsetDateTime getVerificadoEm() {
    return verificadoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }
}
