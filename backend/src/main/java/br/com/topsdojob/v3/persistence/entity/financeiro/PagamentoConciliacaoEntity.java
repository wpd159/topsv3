package br.com.topsdojob.v3.persistence.entity.financeiro;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusConciliacaoPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento_conciliacao")
public class PagamentoConciliacaoEntity {
  protected PagamentoConciliacaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "pagamento_id")
  private UUID pagamentoId;

  @Column(name = "movimento_credito_id")
  private UUID movimentoCreditoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemConciliacaoPagamento origem;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusConciliacaoPagamento status;

  @Column(name = "valor_confirmado", precision = 12, scale = 2)
  private BigDecimal valorConfirmado;

  @Column(name = "creditos_confirmados")
  private Integer creditosConfirmados;

  @Column(name = "aprovado_em")
  private OffsetDateTime aprovadoEm;

  @Column(name = "creditado_em")
  private OffsetDateTime creditadoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getPagamentoId() {
    return pagamentoId;
  }

  public UUID getMovimentoCreditoId() {
    return movimentoCreditoId;
  }

  public OrigemConciliacaoPagamento getOrigem() {
    return origem;
  }

  public StatusConciliacaoPagamento getStatus() {
    return status;
  }

  public BigDecimal getValorConfirmado() {
    return valorConfirmado;
  }

  public Integer getCreditosConfirmados() {
    return creditosConfirmados;
  }

  public OffsetDateTime getAprovadoEm() {
    return aprovadoEm;
  }

  public OffsetDateTime getCreditadoEm() {
    return creditadoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
