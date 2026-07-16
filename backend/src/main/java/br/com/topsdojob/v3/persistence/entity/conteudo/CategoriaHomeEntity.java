package br.com.topsdojob.v3.persistence.entity.conteudo;

import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

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

  @Column(name = "imagem_object_key")
  private String imagemObjectKey;

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

  public String getImagemObjectKey() {
    return imagemObjectKey;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public static CategoriaHomeEntity criarAdministrativa(
      UUID id,
      CategoriaAnuncio categoria,
      String nome,
      String descricao,
      String imagemObjectKey,
      int ordem,
      boolean ativo) {
    CategoriaHomeEntity entity = new CategoriaHomeEntity();
    entity.id = id;
    entity.aplicarDados(categoria, nome, descricao, ordem, ativo);
    entity.imagemPublicaUrl = null;
    entity.imagemObjectKey = imagemObjectKey;
    return entity;
  }

  public void atualizarAdministrativa(
      CategoriaAnuncio categoria,
      String nome,
      String descricao,
      int ordem,
      boolean ativo) {
    aplicarDados(categoria, nome, descricao, ordem, ativo);
  }

  public void substituirImagem(String imagemObjectKey) {
    this.imagemPublicaUrl = null;
    this.imagemObjectKey = imagemObjectKey;
  }

  public static CategoriaHomeEntity criarFixtureHomologacao(
      UUID id,
      String categoriaEnum,
      String nome,
      String descricao,
      String imagemPublicaUrl,
      int ordem,
      boolean ativo) {
    CategoriaHomeEntity entity = new CategoriaHomeEntity();
    entity.id = id;
    entity.aplicarDados(categoria(categoriaEnum), nome, descricao, ordem, ativo);
    entity.imagemPublicaUrl = imagemPublicaUrl;
    entity.imagemObjectKey = null;
    return entity;
  }

  public boolean sincronizarFixtureHomologacao(
      String categoriaEnum,
      String nome,
      String descricao,
      String imagemPublicaUrl,
      int ordem,
      boolean ativo) {
    CategoriaAnuncio categoria = categoria(categoriaEnum);
    boolean alterada = !Objects.equals(this.categoriaEnum, categoria.name())
        || !Objects.equals(this.nome, nome)
        || !Objects.equals(this.descricao, descricao)
        || !Objects.equals(this.destino, categoria.destinoPublico())
        || !Objects.equals(this.imagemPublicaUrl, imagemPublicaUrl)
        || this.imagemObjectKey != null
        || !Objects.equals(this.ordem, ordem)
        || !Objects.equals(this.ativo, ativo);
    if (alterada) {
      aplicarDados(categoria, nome, descricao, ordem, ativo);
      this.imagemPublicaUrl = imagemPublicaUrl;
      this.imagemObjectKey = null;
    }
    return alterada;
  }

  private void aplicarDados(
      CategoriaAnuncio categoria,
      String nome,
      String descricao,
      int ordem,
      boolean ativo) {
    this.categoriaEnum = categoria.name();
    this.nome = nome;
    this.descricao = descricao;
    this.destino = categoria.destinoPublico();
    this.ordem = ordem;
    this.ativo = ativo;
  }

  private static CategoriaAnuncio categoria(String codigo) {
    return CategoriaAnuncio.porCodigo(codigo)
        .orElseThrow(() -> new IllegalArgumentException("categoria canonica invalida"));
  }
}
