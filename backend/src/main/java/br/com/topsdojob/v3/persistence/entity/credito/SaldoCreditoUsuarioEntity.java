package br.com.topsdojob.v3.persistence.entity.credito;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "saldo_credito_usuario")
public class SaldoCreditoUsuarioEntity {
  protected SaldoCreditoUsuarioEntity() {
  }

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "saldo_atual")
  private Integer saldoAtual;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public Integer getSaldoAtual() {
    return saldoAtual;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public Integer getVersao() {
    return versao;
  }
}
