package br.com.topsdojob.v3.persistence.entity.financeiro;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pagamento")
public class PagamentoEntity {
  protected PagamentoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "plano_credito_id")
  private UUID planoCreditoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provedor")
  private ProvedorPagamento provedor;

  @Enumerated(EnumType.STRING)
  @Column(name = "metodo")
  private MetodoPagamento metodo;

  @Column(name = "txid")
  private String txid;

  @Column(name = "identificador_provedor")
  private String identificadorProvedor;

  @Column(name = "valor", precision = 12, scale = 2)
  private BigDecimal valor;

  @Column(name = "moeda", columnDefinition = "char(3)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String moeda;

  @Column(name = "quantidade_creditos")
  private Integer quantidadeCreditos;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_interno")
  private StatusInternoPagamento statusInterno;

  @Column(name = "status_provedor")
  private String statusProvedor;

  @Column(name = "expiracao_em")
  private OffsetDateTime expiracaoEm;

  @Column(name = "aprovado_em")
  private OffsetDateTime aprovadoEm;

  @Column(name = "cancelado_em")
  private OffsetDateTime canceladoEm;

  @Column(name = "creditado_em")
  private OffsetDateTime creditadoEm;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getPlanoCreditoId() {
    return planoCreditoId;
  }

  public ProvedorPagamento getProvedor() {
    return provedor;
  }

  public MetodoPagamento getMetodo() {
    return metodo;
  }

  public String getTxid() {
    return txid;
  }

  public String getIdentificadorProvedor() {
    return identificadorProvedor;
  }

  public BigDecimal getValor() {
    return valor;
  }

  public String getMoeda() {
    return moeda;
  }

  public Integer getQuantidadeCreditos() {
    return quantidadeCreditos;
  }

  public StatusInternoPagamento getStatusInterno() {
    return statusInterno;
  }

  public String getStatusProvedor() {
    return statusProvedor;
  }

  public OffsetDateTime getExpiracaoEm() {
    return expiracaoEm;
  }

  public OffsetDateTime getAprovadoEm() {
    return aprovadoEm;
  }

  public OffsetDateTime getCanceladoEm() {
    return canceladoEm;
  }

  public OffsetDateTime getCreditadoEm() {
    return creditadoEm;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
