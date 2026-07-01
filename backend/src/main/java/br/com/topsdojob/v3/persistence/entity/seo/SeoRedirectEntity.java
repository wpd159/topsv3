package br.com.topsdojob.v3.persistence.entity.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seo_redirect")
public class SeoRedirectEntity {
  protected SeoRedirectEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "origem_caminho")
  private String origemCaminho;

  @Column(name = "destino_caminho")
  private String destinoCaminho;

  @Column(name = "status_code")
  private Integer statusCode;

  @Column(name = "ativo")
  private Boolean ativo;

  @Column(name = "motivo")
  private String motivo;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public String getOrigemCaminho() {
    return origemCaminho;
  }

  public String getDestinoCaminho() {
    return destinoCaminho;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public String getMotivo() {
    return motivo;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
