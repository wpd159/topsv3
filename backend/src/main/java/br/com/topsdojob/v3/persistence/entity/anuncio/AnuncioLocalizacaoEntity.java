package br.com.topsdojob.v3.persistence.entity.anuncio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
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

  public static AnuncioLocalizacaoEntity criarSolicitacaoLocal(
      UUID anuncioId,
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      OffsetDateTime criadoEm) {
    AnuncioLocalizacaoEntity entity = new AnuncioLocalizacaoEntity();
    entity.anuncioId = anuncioId;
    entity.estadoId = estadoId;
    entity.cidadeId = cidadeId;
    entity.bairroId = bairroId;
    entity.enderecoResumido = "Endereco sintetico local";
    entity.latitude = null;
    entity.longitude = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

  public static AnuncioLocalizacaoEntity criarFixtureHomologacao(
      UUID anuncioId,
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      OffsetDateTime criadoEm) {
    AnuncioLocalizacaoEntity entity = new AnuncioLocalizacaoEntity();
    entity.anuncioId = anuncioId;
    entity.estadoId = estadoId;
    entity.cidadeId = cidadeId;
    entity.bairroId = bairroId;
    entity.enderecoResumido = null;
    entity.latitude = null;
    entity.longitude = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

  public static AnuncioLocalizacaoEntity criarEdicaoProprietario(
      UUID anuncioId,
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      OffsetDateTime criadoEm) {
    AnuncioLocalizacaoEntity entity = new AnuncioLocalizacaoEntity();
    entity.anuncioId = anuncioId;
    entity.estadoId = estadoId;
    entity.cidadeId = cidadeId;
    entity.bairroId = bairroId;
    entity.enderecoResumido = null;
    entity.latitude = null;
    entity.longitude = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

  public static AnuncioLocalizacaoEntity criarEdicaoAdministrativa(
      UUID anuncioId,
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      String enderecoResumido,
      OffsetDateTime criadoEm) {
    AnuncioLocalizacaoEntity entity = criarEdicaoProprietario(
        anuncioId, estadoId, cidadeId, bairroId, criadoEm);
    entity.enderecoResumido = enderecoResumido;
    return entity;
  }

  public void atualizarLocalidade(
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      OffsetDateTime atualizadoEm) {
    boolean mudouLocalidade = !Objects.equals(this.estadoId, estadoId)
        || !Objects.equals(this.cidadeId, cidadeId)
        || !Objects.equals(this.bairroId, bairroId);
    this.estadoId = estadoId;
    this.cidadeId = cidadeId;
    this.bairroId = bairroId;
    if (mudouLocalidade) {
      this.enderecoResumido = null;
      this.latitude = null;
      this.longitude = null;
    }
    this.atualizadoEm = atualizadoEm;
  }

  public void atualizarLocalidadeAdministrativa(
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      String enderecoResumido,
      OffsetDateTime atualizadoEm) {
    atualizarLocalidade(estadoId, cidadeId, bairroId, atualizadoEm);
    this.enderecoResumido = enderecoResumido;
  }

  public void sincronizarFixtureHomologacao(
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      OffsetDateTime atualizadoEm) {
    this.estadoId = estadoId;
    this.cidadeId = cidadeId;
    this.bairroId = bairroId;
    this.enderecoResumido = null;
    this.latitude = null;
    this.longitude = null;
    this.atualizadoEm = atualizadoEm;
  }

  public void removerDadosPrecisos(OffsetDateTime atualizadoEm) {
    this.enderecoResumido = null;
    this.latitude = null;
    this.longitude = null;
    this.atualizadoEm = atualizadoEm;
  }

}
