package br.com.topsdojob.v3.persistence.entity.blog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blog_categoria")
public class BlogCategoriaEntity {

  @Id
  private UUID id;

  private String nome;
  private String slug;
  private Integer ordem;
  private Boolean ativa;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  private long versao;

  protected BlogCategoriaEntity() {
  }

  public static BlogCategoriaEntity criar(
      UUID id, String nome, String slug, int ordem, boolean ativa, OffsetDateTime agora) {
    BlogCategoriaEntity entity = new BlogCategoriaEntity();
    entity.id = id;
    entity.criadoEm = agora;
    entity.atualizar(nome, slug, ordem, ativa, agora);
    return entity;
  }

  public void atualizar(String nome, String slug, int ordem, boolean ativa, OffsetDateTime agora) {
    this.nome = nome;
    this.slug = slug;
    this.ordem = ordem;
    this.ativa = ativa;
    this.atualizadoEm = agora;
  }

  public UUID getId() { return id; }
  public String getNome() { return nome; }
  public String getSlug() { return slug; }
  public Integer getOrdem() { return ordem; }
  public Boolean getAtiva() { return ativa; }
  public OffsetDateTime getCriadoEm() { return criadoEm; }
  public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
  public long getVersao() { return versao; }
}
