package br.com.topsdojob.v3.persistence.entity.metrica;

import br.com.topsdojob.v3.domain.metrica.MetricaTipos.MetodoVerificacaoEtaria;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "evento_verificacao_etaria")
public class EventoVerificacaoEtariaEntity {

  protected EventoVerificacaoEtariaEntity() {
  }

  public static EventoVerificacaoEtariaEntity registrar(
      ResultadoVerificacaoEtaria resultado,
      String ipHash,
      String userAgentHash,
      String requestId,
      OffsetDateTime criadoEm) {
    EventoVerificacaoEtariaEntity entity = new EventoVerificacaoEtariaEntity();
    entity.id = UUID.randomUUID();
    entity.resultado = resultado;
    entity.metodo = MetodoVerificacaoEtaria.DECLARACAO;
    entity.ipHash = ipHash;
    entity.userAgentHash = userAgentHash;
    entity.requestId = requestId;
    entity.criadoEm = criadoEm;
    return entity;
  }

  @Id
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "resultado", nullable = false)
  private ResultadoVerificacaoEtaria resultado;

  @Enumerated(EnumType.STRING)
  @Column(name = "metodo", nullable = false)
  private MetodoVerificacaoEtaria metodo;

  @Column(name = "ip_hash")
  private String ipHash;

  @Column(name = "user_agent_hash")
  private String userAgentHash;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "challenge_id")
  private UUID challengeId;

  @Column(name = "session_hash")
  private String sessionHash;

  @Column(name = "estado")
  private String estado;

  @Column(name = "escopo")
  private String escopo;

  @Column(name = "motivo_sanitizado")
  private String motivoSanitizado;

  @Column(name = "documento_status")
  private String documentoStatus;

  @Column(name = "criado_em", nullable = false)
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public ResultadoVerificacaoEtaria getResultado() {
    return resultado;
  }

  public MetodoVerificacaoEtaria getMetodo() {
    return metodo;
  }

  public String getRequestId() {
    return requestId;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public UUID getChallengeId() {
    return challengeId;
  }

  public String getSessionHash() {
    return sessionHash;
  }

  public String getEstado() {
    return estado;
  }

  public String getEscopo() {
    return escopo;
  }

  public String getMotivoSanitizado() {
    return motivoSanitizado;
  }

  public String getDocumentoStatus() {
    return documentoStatus;
  }

  public static EventoVerificacaoEtariaEntity registrarCompliance(
      ResultadoVerificacaoEtaria resultado,
      MetodoVerificacaoEtaria metodo,
      UUID anuncioId,
      UUID challengeId,
      String sessionHash,
      String estado,
      String escopo,
      String motivoSanitizado,
      String documentoStatus,
      String ipHash,
      String userAgentHash,
      String requestId,
      OffsetDateTime criadoEm) {
    EventoVerificacaoEtariaEntity entity = new EventoVerificacaoEtariaEntity();
    entity.id = UUID.randomUUID();
    entity.anuncioId = anuncioId;
    entity.resultado = resultado;
    entity.metodo = metodo;
    entity.challengeId = challengeId;
    entity.sessionHash = sessionHash;
    entity.estado = estado;
    entity.escopo = escopo;
    entity.motivoSanitizado = motivoSanitizado;
    entity.documentoStatus = documentoStatus;
    entity.ipHash = ipHash;
    entity.userAgentHash = userAgentHash;
    entity.requestId = requestId;
    entity.criadoEm = criadoEm;
    return entity;
  }
}
