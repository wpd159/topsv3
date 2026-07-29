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
import java.time.Duration;
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

  public void marcarProcessadoPorSimulacaoLocal(OffsetDateTime processadoEm) {
    this.status = StatusOutbox.PROCESSADO;
    this.processadoEm = processadoEm;
    this.erroResumido = null;
    this.atualizadoEm = processadoEm;
  }

  public void markDelivered(OffsetDateTime deliveredAt) {
    this.status = StatusOutbox.PROCESSADO;
    this.processadoEm = deliveredAt;
    this.proximaTentativaEm = null;
    this.erroResumido = null;
    this.atualizadoEm = deliveredAt;
  }

  public void registerDeliveryFailure(
      OffsetDateTime failedAt,
      int maxAttempts,
      Duration retryDelay,
      String summarizedError) {
    int nextAttempts = (this.tentativas == null ? 0 : this.tentativas) + 1;
    this.tentativas = nextAttempts;
    this.status = nextAttempts >= Math.max(maxAttempts, 1)
        ? StatusOutbox.ERRO
        : StatusOutbox.PENDENTE;
    this.proximaTentativaEm = this.status == StatusOutbox.PENDENTE
        ? failedAt.plus(retryDelay)
        : null;
    this.processadoEm = null;
    this.erroResumido = summarizedError == null
        ? "falha_sanitizada"
        : summarizedError.substring(0, Math.min(summarizedError.length(), 120));
    this.atualizadoEm = failedAt;
  }

  public void cancelDelivery(OffsetDateTime cancelledAt, String reason) {
    this.status = StatusOutbox.CANCELADO;
    this.proximaTentativaEm = null;
    this.processadoEm = null;
    this.erroResumido = reason == null
        ? "evento_nao_entregavel"
        : reason.substring(0, Math.min(reason.length(), 120));
    this.atualizadoEm = cancelledAt;
  }

}
