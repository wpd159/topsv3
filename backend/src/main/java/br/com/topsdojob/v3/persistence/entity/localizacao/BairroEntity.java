package br.com.topsdojob.v3.persistence.entity.localizacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bairro")
public class BairroEntity {
  protected BairroEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "cidade_id")
  private UUID cidadeId;

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

  public UUID getCidadeId() {
    return cidadeId;
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

  public static BairroEntity criarFixtureHomologacao(
      UUID id,
      UUID cidadeId,
      String nome,
      String nomeNormalizado,
      String slug,
      OffsetDateTime criadoEm) {
    BairroEntity entity = new BairroEntity();
    entity.id = id;
    entity.cidadeId = cidadeId;
    entity.nome = nome;
    entity.nomeNormalizado = nomeNormalizado;
    entity.slug = slug;
    entity.criadoEm = criadoEm;
    return entity;
  }

}
