package br.com.topsdojob.v3.persistence.entity.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
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
@Table(name = "compliance_visitor_risk_profile")
public class ComplianceVisitorRiskProfileEntity {

  protected ComplianceVisitorRiskProfileEntity() {
  }

  @Id
  private UUID id;

  @Column(name = "session_hash", nullable = false)
  private String sessionHash;

  @Column(name = "score_atual", nullable = false)
  private int scoreAtual;

  @Enumerated(EnumType.STRING)
  @Column(name = "decisao_atual", nullable = false)
  private DecisaoRiscoVisitante decisaoAtual;

  @Column(name = "reputacao_interna", nullable = false)
  private int reputacaoInterna;

  @Column(name = "falhas_consecutivas", nullable = false)
  private int falhasConsecutivas;

  @Column(name = "acessos_restritos", nullable = false)
  private int acessosRestritos;

  @Column(name = "acessos_explicitos", nullable = false)
  private int acessosExplicitos;

  @Column(name = "sinalizado_revisao", nullable = false)
  private boolean sinalizadoRevisao;

  @Column(name = "bloqueado_temporariamente_ate")
  private OffsetDateTime bloqueadoTemporariamenteAte;

  @Column(name = "bloqueado_definitivamente_ate")
  private OffsetDateTime bloqueadoDefinitivamenteAte;

  @Column(name = "ultimo_motivo_sanitizado")
  private String ultimoMotivoSanitizado;

  @Column(name = "ultima_rota_sanitizada")
  private String ultimaRotaSanitizada;

  @Column(name = "ultimo_anuncio_id")
  private UUID ultimoAnuncioId;

  @Column(name = "visto_em", nullable = false)
  private OffsetDateTime vistoEm;

  @Column(name = "ultimo_challenge_em")
  private OffsetDateTime ultimoChallengeEm;

  @Column(name = "criado_em", nullable = false)
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao", nullable = false)
  private long versao;

  public static ComplianceVisitorRiskProfileEntity criar(String sessionHash, OffsetDateTime agora) {
    ComplianceVisitorRiskProfileEntity entity = new ComplianceVisitorRiskProfileEntity();
    entity.id = UUID.randomUUID();
    entity.sessionHash = sessionHash;
    entity.scoreAtual = 0;
    entity.decisaoAtual = DecisaoRiscoVisitante.ALLOW_LEVEL_1;
    entity.reputacaoInterna = 0;
    entity.falhasConsecutivas = 0;
    entity.acessosRestritos = 0;
    entity.acessosExplicitos = 0;
    entity.sinalizadoRevisao = false;
    entity.vistoEm = agora;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public void registrarAvaliacao(
      int score,
      DecisaoRiscoVisitante decisao,
      EscopoConteudoVisitante escopo,
      String motivo,
      String rota,
      UUID anuncioId,
      OffsetDateTime bloqueioTemporario,
      OffsetDateTime bloqueioDefinitivo,
      OffsetDateTime agora) {
    scoreAtual = Math.max(0, score);
    decisaoAtual = decisao;
    sinalizadoRevisao = sinalizadoRevisao
        || decisao == DecisaoRiscoVisitante.REVIEW_FLAG;
    ultimoMotivoSanitizado = motivo;
    ultimaRotaSanitizada = rota;
    ultimoAnuncioId = anuncioId;
    vistoEm = agora;
    ultimoChallengeEm = agora;
    atualizadoEm = agora;
    if (escopo == EscopoConteudoVisitante.CONTEUDO_EXPLICITO) {
      acessosExplicitos++;
    } else {
      acessosRestritos++;
    }
    if (bloqueioTemporario != null) {
      bloqueadoTemporariamenteAte = bloqueioTemporario;
    }
    if (bloqueioDefinitivo != null) {
      bloqueadoDefinitivamenteAte = bloqueioDefinitivo;
    }
  }

  public void registrarFalha(OffsetDateTime agora) {
    falhasConsecutivas++;
    reputacaoInterna += 6;
    vistoEm = agora;
    atualizadoEm = agora;
  }

  public void registrarSucesso(OffsetDateTime agora) {
    falhasConsecutivas = 0;
    reputacaoInterna = Math.min(0, reputacaoInterna) - 1;
    vistoEm = agora;
    atualizadoEm = agora;
  }

  public boolean bloqueioDefinitivoAtivo(OffsetDateTime agora) {
    return bloqueadoDefinitivamenteAte != null && bloqueadoDefinitivamenteAte.isAfter(agora);
  }

  public boolean bloqueioTemporarioAtivo(OffsetDateTime agora) {
    return bloqueadoTemporariamenteAte != null && bloqueadoTemporariamenteAte.isAfter(agora);
  }

  public UUID getId() {
    return id;
  }

  public String getSessionHash() {
    return sessionHash;
  }

  public int getScoreAtual() {
    return scoreAtual;
  }

  public DecisaoRiscoVisitante getDecisaoAtual() {
    return decisaoAtual;
  }

  public int getReputacaoInterna() {
    return reputacaoInterna;
  }

  public int getFalhasConsecutivas() {
    return falhasConsecutivas;
  }

  public int getAcessosRestritos() {
    return acessosRestritos;
  }

  public int getAcessosExplicitos() {
    return acessosExplicitos;
  }

  public boolean isSinalizadoRevisao() {
    return sinalizadoRevisao;
  }

  public OffsetDateTime getBloqueadoTemporariamenteAte() {
    return bloqueadoTemporariamenteAte;
  }

  public OffsetDateTime getBloqueadoDefinitivamenteAte() {
    return bloqueadoDefinitivamenteAte;
  }

  public String getUltimoMotivoSanitizado() {
    return ultimoMotivoSanitizado;
  }

  public String getUltimaRotaSanitizada() {
    return ultimaRotaSanitizada;
  }

  public UUID getUltimoAnuncioId() {
    return ultimoAnuncioId;
  }

  public OffsetDateTime getVistoEm() {
    return vistoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }
}
