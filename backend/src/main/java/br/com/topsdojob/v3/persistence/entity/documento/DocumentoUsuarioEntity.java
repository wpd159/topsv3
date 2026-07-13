package br.com.topsdojob.v3.persistence.entity.documento;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PoliticaRetencaoDocumento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ParteDocumentoUsuario;
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

  @Column(name = "envio_id")
  private UUID envioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "parte")
  private ParteDocumentoUsuario parte;

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

  @Column(name = "motivo_moderacao")
  private String motivoModeracao;

  @Column(name = "revisado_por")
  private UUID revisadoPor;

  @Column(name = "revisado_em")
  private OffsetDateTime revisadoEm;

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

  public UUID getEnvioId() {
    return envioId;
  }

  public ParteDocumentoUsuario getParte() {
    return parte;
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

  public String getMotivoModeracao() {
    return motivoModeracao;
  }

  public UUID getRevisadoPor() {
    return revisadoPor;
  }

  public OffsetDateTime getRevisadoEm() {
    return revisadoEm;
  }

  public OffsetDateTime getRemovidoEm() {
    return removidoEm;
  }

  public OffsetDateTime getExpurgadoEm() {
    return expurgadoEm;
  }

  public static DocumentoUsuarioEntity criarPendente(
      UUID id,
      UUID usuarioId,
      UUID arquivoMidiaId,
      UUID envioId,
      ParteDocumentoUsuario parte,
      OffsetDateTime criadoEm) {
    DocumentoUsuarioEntity entity = new DocumentoUsuarioEntity();
    entity.id = id;
    entity.usuarioId = usuarioId;
    entity.arquivoMidiaId = arquivoMidiaId;
    entity.envioId = envioId;
    entity.parte = parte;
    entity.tipo = TipoDocumentoUsuario.IDENTIDADE;
    entity.status = StatusDocumentoUsuario.PENDENTE;
    entity.politicaRetencao = PoliticaRetencaoDocumento.ENQUANTO_HOUVER_ANUNCIO;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

  public void marcarEmAnalise(OffsetDateTime agora) {
    if (status == StatusDocumentoUsuario.PENDENTE) {
      status = StatusDocumentoUsuario.EM_ANALISE;
      atualizadoEm = agora;
    }
  }

  public void aplicarDecisao(
      StatusDocumentoUsuario novoStatus,
      UUID atorUsuarioId,
      String motivo,
      OffsetDateTime agora) {
    if (novoStatus != StatusDocumentoUsuario.VALIDADO
        && novoStatus != StatusDocumentoUsuario.REJEITADO
        && novoStatus != StatusDocumentoUsuario.AJUSTE_SOLICITADO) {
      throw new IllegalArgumentException("status de decisao documental invalido");
    }
    status = novoStatus;
    motivoModeracao = motivo;
    revisadoPor = atorUsuarioId;
    revisadoEm = agora;
    atualizadoEm = agora;
    if (novoStatus == StatusDocumentoUsuario.VALIDADO) {
      validadoPor = atorUsuarioId;
      validadoEm = agora;
    } else {
      validadoPor = null;
      validadoEm = null;
    }
  }

}
