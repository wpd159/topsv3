package br.com.topsdojob.v3.persistence.entity.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoTokenVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusTokenVisitante;
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
@Table(name = "compliance_visitor_token")
public class ComplianceVisitorTokenEntity {

  protected ComplianceVisitorTokenEntity() {
  }

  @Id
  private UUID id;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "session_hash", nullable = false)
  private String sessionHash;

  @Column(name = "user_agent_hash", nullable = false)
  private String userAgentHash;

  @Column(name = "challenge_id", nullable = false)
  private UUID challengeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "escopo_token", nullable = false)
  private EscopoTokenVisitante escopoAutorizacao;

  @Enumerated(EnumType.STRING)
  @Column(name = "nivel_acesso", nullable = false)
  private NivelAcessoVisitante nivelAcesso;

  @Enumerated(EnumType.STRING)
  @Column(name = "escopo_conteudo")
  private EscopoConteudoVisitante escopoConteudo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private StatusTokenVisitante status;

  @Column(name = "risco_score", nullable = false)
  private int riscoScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "risco_decisao", nullable = false)
  private DecisaoRiscoVisitante riscoDecisao;

  @Column(name = "emitido_em", nullable = false)
  private OffsetDateTime emitidoEm;

  @Column(name = "expira_em", nullable = false)
  private OffsetDateTime expiraEm;

  @Column(name = "revogado_em")
  private OffsetDateTime revogadoEm;

  @Column(name = "motivo_revogacao")
  private String motivoRevogacao;

  @Column(name = "ultima_validacao_em")
  private OffsetDateTime ultimaValidacaoEm;

  @Column(name = "criado_em", nullable = false)
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao", nullable = false)
  private long versao;

  public static ComplianceVisitorTokenEntity emitir(
      UUID id,
      String tokenHash,
      String sessionHash,
      String userAgentHash,
      UUID challengeId,
      EscopoTokenVisitante escopoAutorizacao,
      NivelAcessoVisitante nivelAcesso,
      EscopoConteudoVisitante escopoConteudo,
      int riscoScore,
      DecisaoRiscoVisitante riscoDecisao,
      OffsetDateTime emitidoEm,
      OffsetDateTime expiraEm) {
    ComplianceVisitorTokenEntity entity = new ComplianceVisitorTokenEntity();
    entity.id = id;
    entity.tokenHash = tokenHash;
    entity.sessionHash = sessionHash;
    entity.userAgentHash = userAgentHash;
    entity.challengeId = challengeId;
    entity.escopoAutorizacao = escopoAutorizacao;
    entity.nivelAcesso = nivelAcesso;
    entity.escopoConteudo = escopoConteudo;
    entity.status = StatusTokenVisitante.ACTIVE;
    entity.riscoScore = Math.max(0, riscoScore);
    entity.riscoDecisao = riscoDecisao;
    entity.emitidoEm = emitidoEm;
    entity.expiraEm = expiraEm;
    entity.criadoEm = emitidoEm;
    entity.atualizadoEm = emitidoEm;
    return entity;
  }

  public boolean ativaEm(OffsetDateTime agora) {
    return status == StatusTokenVisitante.ACTIVE && expiraEm.isAfter(agora);
  }

  public void registrarValidacao(OffsetDateTime agora) {
    if (status == StatusTokenVisitante.ACTIVE) {
      ultimaValidacaoEm = agora;
      atualizadoEm = agora;
    }
  }

  public void expirar(OffsetDateTime agora) {
    if (status == StatusTokenVisitante.ACTIVE) {
      status = StatusTokenVisitante.EXPIRED;
      atualizadoEm = agora;
    }
  }

  public void revogar(String motivo, OffsetDateTime agora) {
    if (status == StatusTokenVisitante.ACTIVE) {
      status = StatusTokenVisitante.REVOKED;
      revogadoEm = agora;
      motivoRevogacao = motivo;
      atualizadoEm = agora;
    }
  }

  public UUID getId() {
    return id;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getSessionHash() {
    return sessionHash;
  }

  public String getUserAgentHash() {
    return userAgentHash;
  }

  public UUID getChallengeId() {
    return challengeId;
  }

  public EscopoTokenVisitante getEscopoToken() {
    return escopoAutorizacao;
  }

  public NivelAcessoVisitante getNivelAcesso() {
    return nivelAcesso;
  }

  public EscopoConteudoVisitante getEscopoConteudo() {
    return escopoConteudo;
  }

  public StatusTokenVisitante getStatus() {
    return status;
  }

  public int getRiscoScore() {
    return riscoScore;
  }

  public DecisaoRiscoVisitante getRiscoDecisao() {
    return riscoDecisao;
  }

  public OffsetDateTime getEmitidoEm() {
    return emitidoEm;
  }

  public OffsetDateTime getExpiraEm() {
    return expiraEm;
  }

  public OffsetDateTime getRevogadoEm() {
    return revogadoEm;
  }

  public String getMotivoRevogacao() {
    return motivoRevogacao;
  }
}
