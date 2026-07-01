package br.com.topsdojob.v3.persistence.entity.metrica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agregado_clique_whatsapp_diario")
public class AgregadoCliqueWhatsappDiarioEntity {
  protected AgregadoCliqueWhatsappDiarioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "data_referencia")
  private LocalDate dataReferencia;

  @Column(name = "origem_uf", columnDefinition = "char(2)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String origemUf;

  @Column(name = "origem_cidade")
  private String origemCidade;

  @Column(name = "origem_uf_chave")
  private String origemUfChave;

  @Column(name = "origem_cidade_chave")
  private String origemCidadeChave;

  @Column(name = "total_cliques")
  private Long totalCliques;

  @Column(name = "visitantes_estimados")
  private Long visitantesEstimados;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public LocalDate getDataReferencia() {
    return dataReferencia;
  }

  public String getOrigemUf() {
    return origemUf;
  }

  public String getOrigemCidade() {
    return origemCidade;
  }

  public String getOrigemUfChave() {
    return origemUfChave;
  }

  public String getOrigemCidadeChave() {
    return origemCidadeChave;
  }

  public Long getTotalCliques() {
    return totalCliques;
  }

  public Long getVisitantesEstimados() {
    return visitantesEstimados;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
