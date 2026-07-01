package br.com.topsdojob.v3.persistence.entity.premium;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
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
@Table(name = "ativacao_beneficio")
public class AtivacaoBeneficioEntity {
  protected AtivacaoBeneficioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "beneficio_id")
  private UUID beneficioId;

  @Column(name = "opcao_id")
  private UUID opcaoId;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "grupo_ativacao_id")
  private UUID grupoAtivacaoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemBeneficio origem;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "campanha_codigo")
  private String campanhaCodigo;

  @Column(name = "inicio_em")
  private OffsetDateTime inicioEm;

  @Column(name = "fim_em")
  private OffsetDateTime fimEm;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusAtivacaoBeneficio status;

  @Column(name = "custo_creditos_snapshot")
  private Integer custoCreditosSnapshot;

  @Column(name = "preco_snapshot", precision = 12, scale = 2)
  private BigDecimal precoSnapshot;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "revogada_em")
  private OffsetDateTime revogadaEm;

  @Column(name = "motivo_revogacao")
  private String motivoRevogacao;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getBeneficioId() {
    return beneficioId;
  }

  public UUID getOpcaoId() {
    return opcaoId;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getGrupoAtivacaoId() {
    return grupoAtivacaoId;
  }

  public OrigemBeneficio getOrigem() {
    return origem;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getCampanhaCodigo() {
    return campanhaCodigo;
  }

  public OffsetDateTime getInicioEm() {
    return inicioEm;
  }

  public OffsetDateTime getFimEm() {
    return fimEm;
  }

  public StatusAtivacaoBeneficio getStatus() {
    return status;
  }

  public Integer getCustoCreditosSnapshot() {
    return custoCreditosSnapshot;
  }

  public BigDecimal getPrecoSnapshot() {
    return precoSnapshot;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public OffsetDateTime getRevogadaEm() {
    return revogadaEm;
  }

  public String getMotivoRevogacao() {
    return motivoRevogacao;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
