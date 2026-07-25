package br.com.topsdojob.v3.persistence.entity.seo;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemSeoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusSeoConteudo;
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
@Table(name = "seo_conteudo_pagina")
public class SeoConteudoPaginaEntity {
  protected SeoConteudoPaginaEntity() {
  }

  public static SeoConteudoPaginaEntity criarPublicada(
      UUID id,
      UUID seoUrlId,
      String chave,
      String titulo,
      String corpoMarkdown,
      UUID atorId,
      OffsetDateTime agora) {
    SeoConteudoPaginaEntity entity = new SeoConteudoPaginaEntity();
    entity.id = id;
    entity.seoUrlId = seoUrlId;
    entity.chave = chave;
    entity.titulo = titulo;
    entity.corpoMarkdown = corpoMarkdown;
    entity.status = StatusSeoConteudo.PUBLICADO;
    entity.origem = OrigemSeoConteudo.ADMIN;
    entity.criadoPor = atorId;
    entity.aprovadoPor = atorId;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    entity.aprovadoEm = agora;
    entity.versao = 0;
    return entity;
  }

  public void publicar(String titulo, String corpoMarkdown, UUID atorId, OffsetDateTime agora) {
    this.titulo = titulo;
    this.corpoMarkdown = corpoMarkdown;
    this.status = StatusSeoConteudo.PUBLICADO;
    this.origem = OrigemSeoConteudo.ADMIN;
    this.aprovadoPor = atorId;
    this.atualizadoEm = agora;
    this.aprovadoEm = agora;
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "seo_url_id")
  private UUID seoUrlId;

  @Column(name = "chave")
  private String chave;

  @Column(name = "titulo")
  private String titulo;

  @Column(name = "corpo_markdown")
  private String corpoMarkdown;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusSeoConteudo status;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemSeoConteudo origem;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "aprovado_por")
  private UUID aprovadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "aprovado_em")
  private OffsetDateTime aprovadoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public UUID getSeoUrlId() {
    return seoUrlId;
  }

  public String getChave() {
    return chave;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getCorpoMarkdown() {
    return corpoMarkdown;
  }

  public StatusSeoConteudo getStatus() {
    return status;
  }

  public OrigemSeoConteudo getOrigem() {
    return origem;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public UUID getAprovadoPor() {
    return aprovadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public OffsetDateTime getAprovadoEm() {
    return aprovadoEm;
  }

  public Integer getVersao() {
    return versao;
  }

}
