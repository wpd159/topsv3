package br.com.topsdojob.v3.persistence.entity.midia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "story_configuracao_comercial")
public class StoryConfiguracaoComercialEntity {

  public static final short SINGLETON_ID = 1;

  protected StoryConfiguracaoComercialEntity() {
  }

  @Id
  @Column(name = "id")
  private Short id;

  @Column(name = "ativo")
  private Boolean ativo;

  @Column(name = "custo_creditos")
  private Integer custoCreditos;

  @Version
  @Column(name = "versao")
  private Long versao;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "atualizado_por")
  private UUID atualizadoPor;

  public static StoryConfiguracaoComercialEntity criar(
      boolean ativo,
      int custoCreditos,
      UUID atualizadoPor,
      OffsetDateTime atualizadoEm) {
    StoryConfiguracaoComercialEntity entity = new StoryConfiguracaoComercialEntity();
    entity.id = SINGLETON_ID;
    entity.ativo = ativo;
    entity.custoCreditos = custoCreditos;
    entity.versao = 0L;
    entity.atualizadoPor = atualizadoPor;
    entity.atualizadoEm = atualizadoEm;
    return entity;
  }

  public void atualizar(
      boolean ativo,
      int custoCreditos,
      UUID atualizadoPor,
      OffsetDateTime atualizadoEm) {
    this.ativo = ativo;
    this.custoCreditos = custoCreditos;
    this.atualizadoPor = atualizadoPor;
    this.atualizadoEm = atualizadoEm;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public Integer getCustoCreditos() {
    return custoCreditos;
  }

  public Long getVersao() {
    return versao;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public UUID getAtualizadoPor() {
    return atualizadoPor;
  }
}
