package br.com.topsdojob.v3.persistence.entity.anuncio;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio_status_historico")
public class AnuncioStatusHistoricoEntity {

  protected AnuncioStatusHistoricoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_anterior")
  private StatusAnuncio statusAnterior;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_novo")
  private StatusAnuncio statusNovo;

  @Column(name = "motivo")
  private String motivo;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public StatusAnuncio getStatusAnterior() {
    return statusAnterior;
  }

  public StatusAnuncio getStatusNovo() {
    return statusNovo;
  }

  public String getMotivo() {
    return motivo;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static AnuncioStatusHistoricoEntity registrar(
      UUID id,
      UUID anuncioId,
      StatusAnuncio statusAnterior,
      StatusAnuncio statusNovo,
      String motivo,
      UUID atorUsuarioId,
      OffsetDateTime criadoEm) {
    AnuncioStatusHistoricoEntity entity = new AnuncioStatusHistoricoEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.statusAnterior = statusAnterior;
    entity.statusNovo = statusNovo;
    entity.motivo = motivo;
    entity.atorUsuarioId = atorUsuarioId;
    entity.criadoEm = criadoEm;
    return entity;
  }
}
