package br.com.topsdojob.v3.persistence.entity.midia;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio_midia")
public class AnuncioMidiaEntity {
  protected AnuncioMidiaEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "arquivo_midia_id")
  private UUID arquivoMidiaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoAnuncioMidia tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "finalidade")
  private FinalidadeAnuncioMidia finalidade;

  @Column(name = "ordem")
  private Integer ordem;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusAnuncioMidia status;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibilidade_midia")
  private VisibilidadeMidia visibilidadeMidia;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getArquivoMidiaId() {
    return arquivoMidiaId;
  }

  public TipoAnuncioMidia getTipo() {
    return tipo;
  }

  public FinalidadeAnuncioMidia getFinalidade() {
    return finalidade;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public StatusAnuncioMidia getStatus() {
    return status;
  }

  public VisibilidadeMidia getVisibilidadeMidia() {
    return visibilidadeMidia;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public void aplicarDecisao(
      StatusAnuncioMidia status,
      VisibilidadeMidia visibilidadeMidia,
      OffsetDateTime atualizadoEm) {
    this.status = status;
    this.visibilidadeMidia = visibilidadeMidia;
    this.atualizadoEm = atualizadoEm;
  }

  public void reordenar(Integer ordem, OffsetDateTime atualizadoEm) {
    this.ordem = ordem;
    this.atualizadoEm = atualizadoEm;
  }

  public void removerLogicamente(OffsetDateTime atualizadoEm) {
    this.status = StatusAnuncioMidia.REMOVIDA;
    this.atualizadoEm = atualizadoEm;
  }

  public static AnuncioMidiaEntity criarUploadPendente(
      UUID id,
      UUID anuncioId,
      UUID arquivoMidiaId,
      TipoAnuncioMidia tipo,
      Integer ordem,
      OffsetDateTime criadoEm) {
    AnuncioMidiaEntity entity = new AnuncioMidiaEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.arquivoMidiaId = arquivoMidiaId;
    entity.tipo = tipo;
    entity.finalidade = FinalidadeAnuncioMidia.GALERIA;
    entity.ordem = ordem;
    entity.status = StatusAnuncioMidia.PENDENTE;
    entity.visibilidadeMidia = tipo == TipoAnuncioMidia.VIDEO
        ? VisibilidadeMidia.RESTRITA_18
        : null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

  public static AnuncioMidiaEntity criarStoryUploadValidado(
      UUID id,
      UUID anuncioId,
      UUID arquivoMidiaId,
      Integer ordem,
      OffsetDateTime criadoEm) {
    if (id == null || anuncioId == null || arquivoMidiaId == null
        || ordem == null || ordem < 0 || criadoEm == null) {
      throw new IllegalArgumentException("Vinculo de midia do Story invalido");
    }
    AnuncioMidiaEntity entity = new AnuncioMidiaEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.arquivoMidiaId = arquivoMidiaId;
    entity.tipo = TipoAnuncioMidia.STORY;
    entity.finalidade = FinalidadeAnuncioMidia.STORY;
    entity.ordem = ordem;
    entity.status = StatusAnuncioMidia.PUBLICAVEL;
    entity.visibilidadeMidia = VisibilidadeMidia.RESTRITA_18;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

  public static AnuncioMidiaEntity criarFixtureHomologacao(
      UUID id,
      UUID anuncioId,
      UUID arquivoMidiaId,
      TipoAnuncioMidia tipo,
      FinalidadeAnuncioMidia finalidade,
      Integer ordem,
      StatusAnuncioMidia status,
      VisibilidadeMidia visibilidadeMidia,
      OffsetDateTime criadoEm) {
    AnuncioMidiaEntity entity = new AnuncioMidiaEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.arquivoMidiaId = arquivoMidiaId;
    entity.tipo = tipo;
    entity.finalidade = finalidade;
    entity.ordem = ordem;
    entity.status = status;
    entity.visibilidadeMidia = visibilidadeMidia;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

}
