package br.com.topsdojob.v3.persistence.entity.moderacao;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DecisaoModeracao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "decisao_moderacao")
public class DecisaoModeracaoEntity {
  protected DecisaoModeracaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "revisao_anuncio_id")
  private UUID revisaoAnuncioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "decisao")
  private DecisaoModeracao decisao;

  @Column(name = "motivo")
  private String motivo;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "ip_hash")
  private String ipHash;

  @Column(name = "user_agent_hash")
  private String userAgentHash;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getRevisaoAnuncioId() {
    return revisaoAnuncioId;
  }

  public DecisaoModeracao getDecisao() {
    return decisao;
  }

  public String getMotivo() {
    return motivo;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getIpHash() {
    return ipHash;
  }

  public String getUserAgentHash() {
    return userAgentHash;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static DecisaoModeracaoEntity registrar(
      UUID id,
      UUID revisaoAnuncioId,
      DecisaoModeracao decisao,
      String motivo,
      UUID atorUsuarioId,
      OffsetDateTime criadoEm) {
    DecisaoModeracaoEntity entity = new DecisaoModeracaoEntity();
    entity.id = id;
    entity.revisaoAnuncioId = revisaoAnuncioId;
    entity.decisao = decisao;
    entity.motivo = motivo;
    entity.atorUsuarioId = atorUsuarioId;
    entity.ipHash = null;
    entity.userAgentHash = null;
    entity.criadoEm = criadoEm;
    return entity;
  }

}
