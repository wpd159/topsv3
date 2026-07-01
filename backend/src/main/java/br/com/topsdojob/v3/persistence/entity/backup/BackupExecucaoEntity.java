package br.com.topsdojob.v3.persistence.entity.backup;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusBackupExecucao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backup_execucao")
public class BackupExecucaoEntity {
  protected BackupExecucaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "politica_id")
  private UUID politicaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusBackupExecucao status;

  @Column(name = "iniciado_em")
  private OffsetDateTime iniciadoEm;

  @Column(name = "finalizado_em")
  private OffsetDateTime finalizadoEm;

  @Column(name = "solicitado_por")
  private UUID solicitadoPor;

  @Column(name = "erro_resumido")
  private String erroResumido;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getPoliticaId() {
    return politicaId;
  }

  public StatusBackupExecucao getStatus() {
    return status;
  }

  public OffsetDateTime getIniciadoEm() {
    return iniciadoEm;
  }

  public OffsetDateTime getFinalizadoEm() {
    return finalizadoEm;
  }

  public UUID getSolicitadoPor() {
    return solicitadoPor;
  }

  public String getErroResumido() {
    return erroResumido;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
