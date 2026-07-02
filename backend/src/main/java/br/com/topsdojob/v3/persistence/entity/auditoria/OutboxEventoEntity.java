package br.com.topsdojob.v3.persistence.entity.auditoria;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
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
@Table(name = "outbox_evento")
public class OutboxEventoEntity {
  protected OutboxEventoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "aggregate_tipo")
  private String aggregateTipo;

  @Column(name = "aggregate_id")
  private UUID aggregateId;

  @Column(name = "tipo_evento")
  private String tipoEvento;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", columnDefinition = "jsonb")
  private String payloadJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusOutbox status;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "tentativas")
  private Integer tentativas;

  @Column(name = "proxima_tentativa_em")
  private OffsetDateTime proximaTentativaEm;

  @Column(name = "processado_em")
  private OffsetDateTime processadoEm;

  @Column(name = "erro_resumido")
  private String erroResumido;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public String getAggregateTipo() {
    return aggregateTipo;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getTipoEvento() {
    return tipoEvento;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public StatusOutbox getStatus() {
    return status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Integer getTentativas() {
    return tentativas;
  }

  public OffsetDateTime getProximaTentativaEm() {
    return proximaTentativaEm;
  }

  public OffsetDateTime getProcessadoEm() {
    return processadoEm;
  }

  public String getErroResumido() {
    return erroResumido;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public static OutboxEventoEntity registrarPendente(
      UUID id,
      String aggregateTipo,
      UUID aggregateId,
      String tipoEvento,
      String payloadJson,
      String idempotencyKey,
      OffsetDateTime criadoEm) {
    OutboxEventoEntity entity = new OutboxEventoEntity();
    entity.id = id;
    entity.aggregateTipo = aggregateTipo;
    entity.aggregateId = aggregateId;
    entity.tipoEvento = tipoEvento;
    entity.payloadJson = payloadJson;
    entity.status = StatusOutbox.PENDENTE;
    entity.idempotencyKey = idempotencyKey;
    entity.tentativas = 0;
    entity.proximaTentativaEm = null;
    entity.processadoEm = null;
    entity.erroResumido = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

}
