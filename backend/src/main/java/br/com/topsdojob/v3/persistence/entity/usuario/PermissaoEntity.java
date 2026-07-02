package br.com.topsdojob.v3.persistence.entity.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "permissao")
public class PermissaoEntity {
  protected PermissaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "codigo")
  private String codigo;

  @Column(name = "descricao")
  private String descricao;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getDescricao() {
    return descricao;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }
}
