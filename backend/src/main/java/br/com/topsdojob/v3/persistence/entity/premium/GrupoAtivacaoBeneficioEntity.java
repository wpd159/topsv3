package br.com.topsdojob.v3.persistence.entity.premium;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "grupo_ativacao_beneficio")
public class GrupoAtivacaoBeneficioEntity {
  protected GrupoAtivacaoBeneficioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoGrupoAtivacaoBeneficio tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemBeneficio origem;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "campanha_codigo")
  private String campanhaCodigo;

  @Column(name = "validade_inicio_em")
  private OffsetDateTime validadeInicioEm;

  @Column(name = "validade_fim_em")
  private OffsetDateTime validadeFimEm;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusGrupoAtivacaoBeneficio status;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "observacao")
  private String observacao;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public TipoGrupoAtivacaoBeneficio getTipo() {
    return tipo;
  }

  public OrigemBeneficio getOrigem() {
    return origem;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getCampanhaCodigo() {
    return campanhaCodigo;
  }

  public OffsetDateTime getValidadeInicioEm() {
    return validadeInicioEm;
  }

  public OffsetDateTime getValidadeFimEm() {
    return validadeFimEm;
  }

  public StatusGrupoAtivacaoBeneficio getStatus() {
    return status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getObservacao() {
    return observacao;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
