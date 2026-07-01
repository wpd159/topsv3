package br.com.topsdojob.v3.persistence.entity.backup;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.AmbienteTesteRestauracao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusTesteRestauracao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backup_teste_restauracao")
public class BackupTesteRestauracaoEntity {
  protected BackupTesteRestauracaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "artefato_id")
  private UUID artefatoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusTesteRestauracao status;

  @Enumerated(EnumType.STRING)
  @Column(name = "ambiente")
  private AmbienteTesteRestauracao ambiente;

  @Column(name = "iniciado_em")
  private OffsetDateTime iniciadoEm;

  @Column(name = "finalizado_em")
  private OffsetDateTime finalizadoEm;

  @Column(name = "responsavel_usuario_id")
  private UUID responsavelUsuarioId;

  @Column(name = "resultado_resumido")
  private String resultadoResumido;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getArtefatoId() {
    return artefatoId;
  }

  public StatusTesteRestauracao getStatus() {
    return status;
  }

  public AmbienteTesteRestauracao getAmbiente() {
    return ambiente;
  }

  public OffsetDateTime getIniciadoEm() {
    return iniciadoEm;
  }

  public OffsetDateTime getFinalizadoEm() {
    return finalizadoEm;
  }

  public UUID getResponsavelUsuarioId() {
    return responsavelUsuarioId;
  }

  public String getResultadoResumido() {
    return resultadoResumido;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
