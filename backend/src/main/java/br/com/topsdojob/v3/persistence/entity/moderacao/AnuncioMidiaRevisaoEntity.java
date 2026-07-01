package br.com.topsdojob.v3.persistence.entity.moderacao;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.AcaoMidiaRevisao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusMidiaRevisao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio_midia_revisao")
public class AnuncioMidiaRevisaoEntity {
  protected AnuncioMidiaRevisaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "revisao_anuncio_id")
  private UUID revisaoAnuncioId;

  @Column(name = "anuncio_midia_id")
  private UUID anuncioMidiaId;

  @Column(name = "arquivo_midia_id")
  private UUID arquivoMidiaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "acao")
  private AcaoMidiaRevisao acao;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusMidiaRevisao status;

  @Column(name = "ordem")
  private Integer ordem;

  @Column(name = "motivo")
  private String motivo;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getRevisaoAnuncioId() {
    return revisaoAnuncioId;
  }

  public UUID getAnuncioMidiaId() {
    return anuncioMidiaId;
  }

  public UUID getArquivoMidiaId() {
    return arquivoMidiaId;
  }

  public AcaoMidiaRevisao getAcao() {
    return acao;
  }

  public StatusMidiaRevisao getStatus() {
    return status;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public String getMotivo() {
    return motivo;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
