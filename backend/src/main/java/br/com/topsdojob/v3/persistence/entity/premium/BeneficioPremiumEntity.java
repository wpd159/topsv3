package br.com.topsdojob.v3.persistence.entity.premium;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "beneficio_premium")
public class BeneficioPremiumEntity {
  protected BeneficioPremiumEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "codigo")
  private String codigo;

  @Column(name = "nome")
  private String nome;

  @Column(name = "descricao")
  private String descricao;

  @Enumerated(EnumType.STRING)
  @Column(name = "escopo")
  private EscopoBeneficioPremium escopo;

  @Column(name = "afeta_ranking")
  private Boolean afetaRanking;

  @Column(name = "ativo")
  private Boolean ativo;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getNome() {
    return nome;
  }

  public String getDescricao() {
    return descricao;
  }

  public EscopoBeneficioPremium getEscopo() {
    return escopo;
  }

  public Boolean getAfetaRanking() {
    return afetaRanking;
  }

  public Boolean getAtivo() {
    return ativo;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static BeneficioPremiumEntity criarFixtureHomologacao(
      UUID id,
      String codigo,
      String nome,
      String descricao,
      EscopoBeneficioPremium escopo,
      boolean afetaRanking,
      boolean ativo,
      OffsetDateTime criadoEm) {
    BeneficioPremiumEntity entity = new BeneficioPremiumEntity();
    entity.id = id;
    entity.codigo = codigo;
    entity.nome = nome;
    entity.descricao = descricao;
    entity.escopo = escopo;
    entity.afetaRanking = afetaRanking;
    entity.ativo = ativo;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public boolean sincronizarFixtureHomologacao(
      String codigo,
      String nome,
      String descricao,
      EscopoBeneficioPremium escopo,
      boolean afetaRanking,
      boolean ativo) {
    boolean alterado = !Objects.equals(this.codigo, codigo)
        || !Objects.equals(this.nome, nome)
        || !Objects.equals(this.descricao, descricao)
        || this.escopo != escopo
        || !Objects.equals(this.afetaRanking, afetaRanking)
        || !Objects.equals(this.ativo, ativo);
    this.codigo = codigo;
    this.nome = nome;
    this.descricao = descricao;
    this.escopo = escopo;
    this.afetaRanking = afetaRanking;
    this.ativo = ativo;
    return alterado;
  }

}
