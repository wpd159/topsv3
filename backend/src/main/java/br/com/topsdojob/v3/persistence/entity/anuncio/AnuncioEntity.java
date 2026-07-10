package br.com.topsdojob.v3.persistence.entity.anuncio;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio")
public class AnuncioEntity {
  protected AnuncioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "slug")
  private String slug;

  @Column(name = "titulo")
  private String titulo;

  @Column(name = "descricao")
  private String descricao;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusAnuncio status;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_moderacao")
  private StatusModeracaoAnuncio statusModeracao;

  @Column(name = "categoria")
  private String categoria;

  @Column(name = "preco", precision = 12, scale = 2)
  private BigDecimal preco;

  @Column(name = "whatsapp_normalizado")
  private String whatsappNormalizado;

  @Column(name = "publicado_em")
  private OffsetDateTime publicadoEm;

  @Column(name = "ultima_publicacao_em")
  private OffsetDateTime ultimaPublicacaoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "removido_em")
  private OffsetDateTime removidoEm;

  @Column(name = "origem_importacao_id")
  private UUID origemImportacaoId;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getSlug() {
    return slug;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getDescricao() {
    return descricao;
  }

  public StatusAnuncio getStatus() {
    return status;
  }

  public StatusModeracaoAnuncio getStatusModeracao() {
    return statusModeracao;
  }

  public String getCategoria() {
    return categoria;
  }

  public BigDecimal getPreco() {
    return preco;
  }

  public String getWhatsappNormalizado() {
    return whatsappNormalizado;
  }

  public OffsetDateTime getPublicadoEm() {
    return publicadoEm;
  }

  public OffsetDateTime getUltimaPublicacaoEm() {
    return ultimaPublicacaoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public OffsetDateTime getRemovidoEm() {
    return removidoEm;
  }

  public UUID getOrigemImportacaoId() {
    return origemImportacaoId;
  }

  public Integer getVersao() {
    return versao;
  }

  public void aplicarModeracao(
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      OffsetDateTime atualizadoEm) {
    this.status = status;
    this.statusModeracao = statusModeracao;
    this.atualizadoEm = atualizadoEm;
  }

  public void remeterParaRevisao(OffsetDateTime atualizadoEm) {
    this.status = StatusAnuncio.PENDENTE_REVISAO;
    this.statusModeracao = StatusModeracaoAnuncio.PENDENTE;
    this.atualizadoEm = atualizadoEm;
  }

  public static AnuncioEntity criarSolicitacaoLocal(
      UUID id,
      UUID usuarioId,
      String slug,
      String titulo,
      String descricao,
      String categoria,
      BigDecimal preco,
      String whatsappNormalizado,
      OffsetDateTime criadoEm) {
    AnuncioEntity entity = new AnuncioEntity();
    entity.id = id;
    entity.usuarioId = usuarioId;
    entity.slug = slug;
    entity.titulo = titulo;
    entity.descricao = descricao;
    entity.status = StatusAnuncio.PENDENTE_REVISAO;
    entity.statusModeracao = StatusModeracaoAnuncio.PENDENTE;
    entity.categoria = categoria;
    entity.preco = preco;
    entity.whatsappNormalizado = whatsappNormalizado;
    entity.publicadoEm = null;
    entity.ultimaPublicacaoEm = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    entity.removidoEm = null;
    entity.origemImportacaoId = null;
    entity.versao = 0;
    return entity;
  }

}
