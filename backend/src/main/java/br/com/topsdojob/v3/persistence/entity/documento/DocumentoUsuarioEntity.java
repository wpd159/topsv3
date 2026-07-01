package br.com.topsdojob.v3.persistence.entity.documento;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PoliticaRetencaoDocumento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoDocumentoUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documento_usuario")
public class DocumentoUsuarioEntity {
  protected DocumentoUsuarioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "arquivo_midia_id")
  private UUID arquivoMidiaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoDocumentoUsuario tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusDocumentoUsuario status;

  @Enumerated(EnumType.STRING)
  @Column(name = "politica_retencao")
  private PoliticaRetencaoDocumento politicaRetencao;

  @Column(name = "retencao_ate")
  private OffsetDateTime retencaoAte;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "validado_por")
  private UUID validadoPor;

  @Column(name = "validado_em")
  private OffsetDateTime validadoEm;

  @Column(name = "removido_em")
  private OffsetDateTime removidoEm;

  @Column(name = "expurgado_em")
  private OffsetDateTime expurgadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getArquivoMidiaId() {
    return arquivoMidiaId;
  }

  public TipoDocumentoUsuario getTipo() {
    return tipo;
  }

  public StatusDocumentoUsuario getStatus() {
    return status;
  }

  public PoliticaRetencaoDocumento getPoliticaRetencao() {
    return politicaRetencao;
  }

  public OffsetDateTime getRetencaoAte() {
    return retencaoAte;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public UUID getValidadoPor() {
    return validadoPor;
  }

  public OffsetDateTime getValidadoEm() {
    return validadoEm;
  }

  public OffsetDateTime getRemovidoEm() {
    return removidoEm;
  }

  public OffsetDateTime getExpurgadoEm() {
    return expurgadoEm;
  }

}
