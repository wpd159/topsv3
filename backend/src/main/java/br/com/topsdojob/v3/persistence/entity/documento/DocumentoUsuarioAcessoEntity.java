package br.com.topsdojob.v3.persistence.entity.documento;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAcessoDocumento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ResultadoAcessoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documento_usuario_acesso")
public class DocumentoUsuarioAcessoEntity {
  protected DocumentoUsuarioAcessoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "documento_usuario_id")
  private UUID documentoUsuarioId;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "finalidade")
  private FinalidadeAcessoDocumento finalidade;

  @Enumerated(EnumType.STRING)
  @Column(name = "resultado")
  private ResultadoAcessoDocumento resultado;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "ip_hash")
  private String ipHash;

  @Column(name = "user_agent_hash")
  private String userAgentHash;

  @Column(name = "acessado_em")
  private OffsetDateTime acessadoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getDocumentoUsuarioId() {
    return documentoUsuarioId;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public FinalidadeAcessoDocumento getFinalidade() {
    return finalidade;
  }

  public ResultadoAcessoDocumento getResultado() {
    return resultado;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getIpHash() {
    return ipHash;
  }

  public String getUserAgentHash() {
    return userAgentHash;
  }

  public OffsetDateTime getAcessadoEm() {
    return acessadoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
