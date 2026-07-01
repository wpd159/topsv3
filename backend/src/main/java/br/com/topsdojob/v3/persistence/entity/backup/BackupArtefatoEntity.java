package br.com.topsdojob.v3.persistence.entity.backup;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoBackupArtefato;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backup_artefato")
public class BackupArtefatoEntity {
  protected BackupArtefatoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "execucao_id")
  private UUID execucaoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoBackupArtefato tipo;

  @Column(name = "storage_provider")
  private String storageProvider;

  @Column(name = "bucket")
  private String bucket;

  @Column(name = "chave_objeto")
  private String chaveObjeto;

  @Column(name = "tamanho_bytes")
  private Long tamanhoBytes;

  @Column(name = "sha256")
  private String sha256;

  @Column(name = "criptografado")
  private Boolean criptografado;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getExecucaoId() {
    return execucaoId;
  }

  public TipoBackupArtefato getTipo() {
    return tipo;
  }

  public String getStorageProvider() {
    return storageProvider;
  }

  public String getBucket() {
    return bucket;
  }

  public String getChaveObjeto() {
    return chaveObjeto;
  }

  public Long getTamanhoBytes() {
    return tamanhoBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public Boolean getCriptografado() {
    return criptografado;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
