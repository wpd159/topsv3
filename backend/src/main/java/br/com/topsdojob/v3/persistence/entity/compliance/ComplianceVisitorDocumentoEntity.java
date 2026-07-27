package br.com.topsdojob.v3.persistence.entity.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusDocumentoVisitante;
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
@Table(name = "compliance_visitor_documento")
public class ComplianceVisitorDocumentoEntity {

  protected ComplianceVisitorDocumentoEntity() {
  }

  @Id
  private UUID id;

  @Column(name = "session_hash", nullable = false)
  private String sessionHash;

  @Column(name = "challenge_id", nullable = false)
  private UUID challengeId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private StatusDocumentoVisitante status;

  @Column(name = "storage_provider", nullable = false)
  private String storageProvider;

  @Column(name = "bucket", nullable = false)
  private String bucket;

  @Column(name = "chave_objeto", nullable = false)
  private String chaveObjeto;

  @Column(name = "mime_type", nullable = false)
  private String mimeType;

  @Column(name = "tamanho_bytes", nullable = false)
  private long tamanhoBytes;

  @Column(name = "sha256", nullable = false)
  private String sha256;

  @Column(name = "idempotencia_hash", nullable = false)
  private String idempotenciaHash;

  @Column(name = "motivo_publico_sanitizado")
  private String motivoPublicoSanitizado;

  @Column(name = "revisado_por")
  private UUID revisadoPor;

  @Column(name = "revisado_em")
  private OffsetDateTime revisadoEm;

  @Column(name = "criado_em", nullable = false)
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao", nullable = false)
  private long versao;

  public static ComplianceVisitorDocumentoEntity criarPendente(
      UUID id,
      String sessionHash,
      UUID challengeId,
      UUID anuncioId,
      String bucket,
      String chaveObjeto,
      String mimeType,
      long tamanhoBytes,
      String sha256,
      String idempotenciaHash,
      OffsetDateTime agora) {
    ComplianceVisitorDocumentoEntity entity = new ComplianceVisitorDocumentoEntity();
    entity.id = id;
    entity.sessionHash = sessionHash;
    entity.challengeId = challengeId;
    entity.anuncioId = anuncioId;
    entity.status = StatusDocumentoVisitante.PENDING;
    entity.storageProvider = "R2";
    entity.bucket = bucket;
    entity.chaveObjeto = chaveObjeto;
    entity.mimeType = mimeType;
    entity.tamanhoBytes = tamanhoBytes;
    entity.sha256 = sha256;
    entity.idempotenciaHash = idempotenciaHash;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public void decidir(
      StatusDocumentoVisitante novoStatus,
      UUID ator,
      String motivoPublico,
      OffsetDateTime agora) {
    if (status != StatusDocumentoVisitante.PENDING) {
      throw new IllegalStateException("documento ja decidido");
    }
    if (novoStatus != StatusDocumentoVisitante.APPROVED
        && novoStatus != StatusDocumentoVisitante.REJECTED) {
      throw new IllegalArgumentException("decisao documental invalida");
    }
    status = novoStatus;
    revisadoPor = ator;
    revisadoEm = agora;
    motivoPublicoSanitizado = motivoPublico;
    atualizadoEm = agora;
  }

  public UUID getId() {
    return id;
  }

  public String getSessionHash() {
    return sessionHash;
  }

  public UUID getChallengeId() {
    return challengeId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public StatusDocumentoVisitante getStatus() {
    return status;
  }

  public String getStorageProvider() {
    return storageProvider;
  }

  public String getBucket() {
    return bucket;
  }

  public String getChaveObjeto() {
    return chaveObjeto;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getTamanhoBytes() {
    return tamanhoBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public String getIdempotenciaHash() {
    return idempotenciaHash;
  }

  public String getMotivoPublicoSanitizado() {
    return motivoPublicoSanitizado;
  }

  public UUID getRevisadoPor() {
    return revisadoPor;
  }

  public OffsetDateTime getRevisadoEm() {
    return revisadoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }
}
