package br.com.topsdojob.v3.persistence.entity.moderacao;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "revisao_anuncio")
public class RevisaoAnuncioEntity {
  protected RevisaoAnuncioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoRevisaoAnuncio tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusRevisaoAnuncio status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_solicitado", columnDefinition = "jsonb")
  private String payloadSolicitado;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "finalizado_em")
  private OffsetDateTime finalizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public TipoRevisaoAnuncio getTipo() {
    return tipo;
  }

  public StatusRevisaoAnuncio getStatus() {
    return status;
  }

  public String getPayloadSolicitado() {
    return payloadSolicitado;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getFinalizadoEm() {
    return finalizadoEm;
  }

  public void finalizar(StatusRevisaoAnuncio novoStatus, OffsetDateTime finalizadoEm) {
    this.status = novoStatus;
    this.finalizadoEm = finalizadoEm;
  }

  public static RevisaoAnuncioEntity abrir(
      UUID id,
      UUID anuncioId,
      TipoRevisaoAnuncio tipo,
      String payloadSolicitado,
      UUID criadoPor,
      OffsetDateTime criadoEm) {
    RevisaoAnuncioEntity entity = new RevisaoAnuncioEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.tipo = tipo;
    entity.status = StatusRevisaoAnuncio.ABERTA;
    entity.payloadSolicitado = payloadSolicitado == null ? "{}" : payloadSolicitado;
    entity.criadoPor = criadoPor;
    entity.criadoEm = criadoEm;
    entity.finalizadoEm = null;
    return entity;
  }

}
