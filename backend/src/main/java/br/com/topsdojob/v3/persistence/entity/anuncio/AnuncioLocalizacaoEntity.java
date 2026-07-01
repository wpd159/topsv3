package br.com.topsdojob.v3.persistence.entity.anuncio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio_localizacao")
public class AnuncioLocalizacaoEntity {
  protected AnuncioLocalizacaoEntity() {
  }

  @Id
  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "estado_id")
  private UUID estadoId;

  @Column(name = "cidade_id")
  private UUID cidadeId;

  @Column(name = "bairro_id")
  private UUID bairroId;

  @Column(name = "endereco_resumido")
  private String enderecoResumido;

  @Column(name = "latitude", precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(name = "longitude", precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getAnuncioId() {
    return anuncioId;
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

  public String getEnderecoResumido() {
    return enderecoResumido;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
