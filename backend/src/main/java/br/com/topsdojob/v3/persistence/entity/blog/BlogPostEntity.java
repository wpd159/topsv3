package br.com.topsdojob.v3.persistence.entity.blog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blog_post")
public class BlogPostEntity {

  @Id
  private UUID id;

  @Column(name = "categoria_id")
  private UUID categoriaId;

  private String titulo;
  private String slug;
  private String resumo;
  private String conteudo;

  @Column(name = "autor_nome")
  private String autorNome;

  private String status;

  @Column(name = "seo_title")
  private String seoTitle;

  @Column(name = "seo_description")
  private String seoDescription;

  @Column(name = "sitemap_priority")
  private BigDecimal sitemapPriority;

  @Column(name = "change_frequency")
  private String changeFrequency;

  @Column(name = "imagem_capa_id")
  private UUID imagemCapaId;

  @Column(name = "imagem_og_id")
  private UUID imagemOgId;

  @Column(name = "criado_por_usuario_id")
  private UUID criadoPorUsuarioId;

  @Column(name = "atualizado_por_usuario_id")
  private UUID atualizadoPorUsuarioId;

  @Column(name = "criado_request_id")
  private String criadoRequestId;

  @Column(name = "publicado_em")
  private OffsetDateTime publicadoEm;

  @Column(name = "arquivado_em")
  private OffsetDateTime arquivadoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  private long versao;

  protected BlogPostEntity() {
  }

  public static BlogPostEntity criarRascunho(
      UUID id,
      UUID atorId,
      String requestId,
      OffsetDateTime agora) {
    BlogPostEntity entity = new BlogPostEntity();
    entity.id = id;
    entity.status = "RASCUNHO";
    entity.criadoPorUsuarioId = atorId;
    entity.atualizadoPorUsuarioId = atorId;
    entity.criadoRequestId = requestId;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public void atualizar(
      UUID categoriaId,
      String titulo,
      String slug,
      String resumo,
      String conteudo,
      String autorNome,
      String seoTitle,
      String seoDescription,
      BigDecimal sitemapPriority,
      String changeFrequency,
      UUID imagemCapaId,
      UUID imagemOgId,
      UUID atorId,
      OffsetDateTime agora) {
    this.categoriaId = categoriaId;
    this.titulo = titulo;
    this.slug = slug;
    this.resumo = resumo;
    this.conteudo = conteudo;
    this.autorNome = autorNome;
    this.seoTitle = seoTitle;
    this.seoDescription = seoDescription;
    this.sitemapPriority = sitemapPriority;
    this.changeFrequency = changeFrequency;
    this.imagemCapaId = imagemCapaId;
    this.imagemOgId = imagemOgId;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void publicar(OffsetDateTime agora, UUID atorId) {
    this.status = "PUBLICADO";
    if (this.publicadoEm == null) {
      this.publicadoEm = agora;
    }
    this.arquivadoEm = null;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void retirar(OffsetDateTime agora, UUID atorId) {
    this.status = "RASCUNHO";
    this.arquivadoEm = null;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void arquivar(OffsetDateTime agora, UUID atorId) {
    this.status = "ARQUIVADO";
    this.arquivadoEm = agora;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public UUID getId() { return id; }
  public UUID getCategoriaId() { return categoriaId; }
  public String getTitulo() { return titulo; }
  public String getSlug() { return slug; }
  public String getResumo() { return resumo; }
  public String getConteudo() { return conteudo; }
  public String getAutorNome() { return autorNome; }
  public String getStatus() { return status; }
  public String getSeoTitle() { return seoTitle; }
  public String getSeoDescription() { return seoDescription; }
  public BigDecimal getSitemapPriority() { return sitemapPriority; }
  public String getChangeFrequency() { return changeFrequency; }
  public UUID getImagemCapaId() { return imagemCapaId; }
  public UUID getImagemOgId() { return imagemOgId; }
  public UUID getCriadoPorUsuarioId() { return criadoPorUsuarioId; }
  public UUID getAtualizadoPorUsuarioId() { return atualizadoPorUsuarioId; }
  public String getCriadoRequestId() { return criadoRequestId; }
  public OffsetDateTime getPublicadoEm() { return publicadoEm; }
  public OffsetDateTime getArquivadoEm() { return arquivadoEm; }
  public OffsetDateTime getCriadoEm() { return criadoEm; }
  public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
  public long getVersao() { return versao; }
}
