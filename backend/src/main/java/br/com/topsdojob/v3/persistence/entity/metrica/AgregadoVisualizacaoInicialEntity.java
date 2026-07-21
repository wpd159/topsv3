package br.com.topsdojob.v3.persistence.entity.metrica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agregado_visualizacao_inicial")
public class AgregadoVisualizacaoInicialEntity {
  protected AgregadoVisualizacaoInicialEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id", nullable = false)
  private UUID anuncioId;

  @Column(name = "execucao_id", nullable = false)
  private UUID execucaoId;

  @Column(name = "total_visualizacoes", nullable = false)
  private Long totalVisualizacoes;

  @Column(name = "snapshot_fingerprint", nullable = false)
  private String snapshotFingerprint;

  @Column(name = "origem_hash", nullable = false)
  private String origemHash;

  @Column(name = "snapshot_corte_em", nullable = false)
  private OffsetDateTime snapshotCorteEm;

  @Column(name = "criado_em", nullable = false)
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getExecucaoId() {
    return execucaoId;
  }

  public Long getTotalVisualizacoes() {
    return totalVisualizacoes;
  }

  public String getSnapshotFingerprint() {
    return snapshotFingerprint;
  }

  public String getOrigemHash() {
    return origemHash;
  }

  public OffsetDateTime getSnapshotCorteEm() {
    return snapshotCorteEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }
}
