package br.com.topsdojob.v3.persistence.entity.localizacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "estado")
public class EstadoEntity {
  protected EstadoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "uf", columnDefinition = "char(2)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String uf;

  @Column(name = "nome")
  private String nome;

  @Column(name = "nome_normalizado")
  private String nomeNormalizado;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public String getUf() {
    return uf;
  }

  public String getNome() {
    return nome;
  }

  public String getNomeNormalizado() {
    return nomeNormalizado;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
