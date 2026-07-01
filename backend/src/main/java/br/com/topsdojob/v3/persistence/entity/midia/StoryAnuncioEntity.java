package br.com.topsdojob.v3.persistence.entity.midia;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "story_anuncio")
public class StoryAnuncioEntity {
  protected StoryAnuncioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_midia_id")
  private UUID anuncioMidiaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusStoryAnuncio status;

  @Column(name = "inicio_em")
  private OffsetDateTime inicioEm;

  @Column(name = "fim_em")
  private OffsetDateTime fimEm;

  @Column(name = "ordem")
  private Integer ordem;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioMidiaId() {
    return anuncioMidiaId;
  }

  public StatusStoryAnuncio getStatus() {
    return status;
  }

  public OffsetDateTime getInicioEm() {
    return inicioEm;
  }

  public OffsetDateTime getFimEm() {
    return fimEm;
  }

  public Integer getOrdem() {
    return ordem;
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
