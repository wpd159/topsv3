package br.com.topsdojob.v3.persistence.entity.backup;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FrequenciaBackup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backup_politica")
public class BackupPoliticaEntity {
  protected BackupPoliticaEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "codigo")
  private String codigo;

  @Column(name = "nome")
  private String nome;

  @Column(name = "descricao")
  private String descricao;

  @Column(name = "retencao_dias")
  private Integer retencaoDias;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequencia")
  private FrequenciaBackup frequencia;

  @Column(name = "criptografia_obrigatoria")
  private Boolean criptografiaObrigatoria;

  @Column(name = "ativo")
  private Boolean ativo;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getNome() {
    return nome;
  }

  public String getDescricao() {
    return descricao;
  }

  public Integer getRetencaoDias() {
    return retencaoDias;
  }

  public FrequenciaBackup getFrequencia() {
    return frequencia;
  }

  public Boolean getCriptografiaObrigatoria() {
    return criptografiaObrigatoria;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public Integer getVersao() {
    return versao;
  }

}
