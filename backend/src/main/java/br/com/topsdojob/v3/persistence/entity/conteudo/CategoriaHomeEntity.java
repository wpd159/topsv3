package br.com.topsdojob.v3.persistence.entity.conteudo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.Objects;

@Entity
@Table(name = "categoria_home")
public class CategoriaHomeEntity {
  protected CategoriaHomeEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "categoria_enum")
  private String categoriaEnum;

  @Column(name = "nome")
  private String nome;

  @Column(name = "descricao")
  private String descricao;

  @Column(name = "destino")
  private String destino;

  @Column(name = "imagem_publica_url")
  private String imagemPublicaUrl;

  @Column(name = "ordem")
  private Integer ordem;

  @Column(name = "ativo")
  private Boolean ativo;

  public UUID getId() {
    return id;
  }

  public String getCategoriaEnum() {
    return categoriaEnum;
  }

  public String getNome() {
    return nome;
  }

  public String getDescricao() {
    return descricao;
  }

  public String getDestino() {
    return destino;
  }

  public String getImagemPublicaUrl() {
    return imagemPublicaUrl;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public static CategoriaHomeEntity criarFixtureHomologacao(
      UUID id,
      String categoriaEnum,
      String nome,
      String descricao,
      String destino,
      String imagemPublicaUrl,
      int ordem,
      boolean ativo) {
    CategoriaHomeEntity entity = new CategoriaHomeEntity();
    entity.id = id;
    entity.categoriaEnum = categoriaEnum;
    entity.nome = nome;
    entity.descricao = descricao;
    entity.destino = destino;
    entity.imagemPublicaUrl = imagemPublicaUrl;
    entity.ordem = ordem;
    entity.ativo = ativo;
    return entity;
  }

  public boolean sincronizarFixtureHomologacao(
      String categoriaEnum,
      String nome,
      String descricao,
      String destino,
      String imagemPublicaUrl,
      int ordem,
      boolean ativo) {
    boolean alterada = !Objects.equals(this.categoriaEnum, categoriaEnum)
        || !Objects.equals(this.nome, nome)
        || !Objects.equals(this.descricao, descricao)
        || !Objects.equals(this.destino, destino)
        || !Objects.equals(this.imagemPublicaUrl, imagemPublicaUrl)
        || !Objects.equals(this.ordem, ordem)
        || !Objects.equals(this.ativo, ativo);
    if (alterada) {
      this.categoriaEnum = categoriaEnum;
      this.nome = nome;
      this.descricao = descricao;
      this.destino = destino;
      this.imagemPublicaUrl = imagemPublicaUrl;
      this.ordem = ordem;
      this.ativo = ativo;
    }
    return alterada;
  }
}
