package br.com.topsdojob.v3.persistence.entity.credito;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimento_credito")
public class MovimentoCreditoEntity {
  protected MovimentoCreditoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoMovimentoCredito tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "direcao")
  private DirecaoMovimentoCredito direcao;

  @Column(name = "quantidade")
  private Integer quantidade;

  @Column(name = "saldo_antes")
  private Integer saldoAntes;

  @Column(name = "saldo_depois")
  private Integer saldoDepois;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemMovimentoCredito origem;

  @Column(name = "referencia_tipo")
  private String referenciaTipo;

  @Column(name = "referencia_id")
  private UUID referenciaId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "observacao")
  private String observacao;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public TipoMovimentoCredito getTipo() {
    return tipo;
  }

  public DirecaoMovimentoCredito getDirecao() {
    return direcao;
  }

  public Integer getQuantidade() {
    return quantidade;
  }

  public Integer getSaldoAntes() {
    return saldoAntes;
  }

  public Integer getSaldoDepois() {
    return saldoDepois;
  }

  public OrigemMovimentoCredito getOrigem() {
    return origem;
  }

  public String getReferenciaTipo() {
    return referenciaTipo;
  }

  public UUID getReferenciaId() {
    return referenciaId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getObservacao() {
    return observacao;
  }

  public String getRequestId() {
    return requestId;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static MovimentoCreditoEntity registrar(
      UUID id,
      UUID usuarioId,
      TipoMovimentoCredito tipo,
      DirecaoMovimentoCredito direcao,
      int quantidade,
      int saldoAntes,
      int saldoDepois,
      OrigemMovimentoCredito origem,
      String referenciaTipo,
      UUID referenciaId,
      String idempotencyKey,
      UUID atorUsuarioId,
      String observacao,
      String requestId,
      OffsetDateTime criadoEm) {
    MovimentoCreditoEntity entity = new MovimentoCreditoEntity();
    entity.id = id;
    entity.usuarioId = usuarioId;
    entity.tipo = tipo;
    entity.direcao = direcao;
    entity.quantidade = quantidade;
    entity.saldoAntes = saldoAntes;
    entity.saldoDepois = saldoDepois;
    entity.origem = origem;
    entity.referenciaTipo = referenciaTipo;
    entity.referenciaId = referenciaId;
    entity.idempotencyKey = idempotencyKey;
    entity.atorUsuarioId = atorUsuarioId;
    entity.observacao = observacao;
    entity.requestId = requestId;
    entity.criadoEm = criadoEm;
    return entity;
  }

}
