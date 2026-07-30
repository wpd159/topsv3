package br.com.topsdojob.v3.persistence.entity.anuncio;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusPublicacaoBusca;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documento_busca_anuncio")
public class DocumentoBuscaAnuncioEntity {
  protected DocumentoBuscaAnuncioEntity() {
  }

  @Id
  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "texto_busca")
  private String textoBusca;

  @Column(name = "estado_id")
  private UUID estadoId;

  @Column(name = "cidade_id")
  private UUID cidadeId;

  @Column(name = "bairro_id")
  private UUID bairroId;

  @Column(name = "categoria")
  private String categoria;

  @Column(name = "preco", precision = 12, scale = 2)
  private BigDecimal preco;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_publicacao")
  private StatusPublicacaoBusca statusPublicacao;

  @Column(name = "tem_midia_valida")
  private Boolean temMidiaValida;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "beneficios_ranking_json", columnDefinition = "jsonb")
  private String beneficiosRankingJson;

  @Column(name = "ranking_base", precision = 10, scale = 4)
  private BigDecimal rankingBase;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public String getTextoBusca() {
    return textoBusca;
  }

  public UUID getEstadoId() {
    return estadoId;
  }

  public UUID getCidadeId() {
    return cidadeId;
  }

  public UUID getBairroId() {
    return bairroId;
  }

  public String getCategoria() {
    return categoria;
  }

  public BigDecimal getPreco() {
    return preco;
  }

  public StatusPublicacaoBusca getStatusPublicacao() {
    return statusPublicacao;
  }

  public Boolean getTemMidiaValida() {
    return temMidiaValida;
  }

  public String getBeneficiosRankingJson() {
    return beneficiosRankingJson;
  }

  public BigDecimal getRankingBase() {
    return rankingBase;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public static DocumentoBuscaAnuncioEntity criarSolicitacaoLocal(
      UUID anuncioId,
      String textoBusca,
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      String categoria,
      BigDecimal preco,
      OffsetDateTime atualizadoEm) {
    DocumentoBuscaAnuncioEntity entity = new DocumentoBuscaAnuncioEntity();
    entity.anuncioId = anuncioId;
    entity.textoBusca = textoBusca;
    entity.estadoId = estadoId;
    entity.cidadeId = cidadeId;
    entity.bairroId = bairroId;
    entity.categoria = categoria;
    entity.preco = preco;
    entity.statusPublicacao = StatusPublicacaoBusca.NAO_PUBLICAVEL;
    entity.temMidiaValida = false;
    entity.beneficiosRankingJson = "{}";
    entity.rankingBase = BigDecimal.ZERO;
    entity.atualizadoEm = atualizadoEm;
    return entity;
  }

  public void atualizarAposEdicao(
      String textoBusca,
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      String categoria,
      BigDecimal preco,
      OffsetDateTime atualizadoEm) {
    this.textoBusca = textoBusca;
    this.estadoId = estadoId;
    this.cidadeId = cidadeId;
    this.bairroId = bairroId;
    this.categoria = categoria;
    this.preco = preco;
    this.statusPublicacao = StatusPublicacaoBusca.NAO_PUBLICAVEL;
    this.atualizadoEm = atualizadoEm;
  }

  public void removerDaBusca(OffsetDateTime atualizadoEm) {
    this.textoBusca = "";
    this.preco = null;
    this.statusPublicacao = StatusPublicacaoBusca.REMOVIDO;
    this.temMidiaValida = false;
    this.beneficiosRankingJson = "{}";
    this.rankingBase = BigDecimal.ZERO;
    this.atualizadoEm = atualizadoEm;
  }

}
