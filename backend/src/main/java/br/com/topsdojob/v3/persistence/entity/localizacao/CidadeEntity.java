package br.com.topsdojob.v3.persistence.entity.localizacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cidade")
public class CidadeEntity {
  protected CidadeEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "estado_id")
  private UUID estadoId;

  @Column(name = "nome")
  private String nome;

  @Column(name = "nome_normalizado")
  private String nomeNormalizado;

  @Column(name = "slug")
  private String slug;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getEstadoId() {
    return estadoId;
  }

  public String getNome() {
    return nome;
  }

  public String getNomeNormalizado() {
    return nomeNormalizado;
  }

  public String getSlug() {
    return slug;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
