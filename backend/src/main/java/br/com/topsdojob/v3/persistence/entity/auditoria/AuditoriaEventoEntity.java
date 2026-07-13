package br.com.topsdojob.v3.persistence.entity.auditoria;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemAuditoria;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ResultadoAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria_evento")
public class AuditoriaEventoEntity {
  protected AuditoriaEventoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "acao")
  private String acao;

  @Column(name = "recurso_tipo")
  private String recursoTipo;

  @Column(name = "recurso_id")
  private UUID recursoId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "antes_json", columnDefinition = "jsonb")
  private String antesJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "depois_json", columnDefinition = "jsonb")
  private String depoisJson;

  @Column(name = "antes_hash")
  private String antesHash;

  @Column(name = "depois_hash")
  private String depoisHash;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "ip_hash")
  private String ipHash;

  @Column(name = "user_agent_hash")
  private String userAgentHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemAuditoria origem;

  @Enumerated(EnumType.STRING)
  @Column(name = "resultado")
  private ResultadoAuditoria resultado;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getAcao() {
    return acao;
  }

  public String getRecursoTipo() {
    return recursoTipo;
  }

  public UUID getRecursoId() {
    return recursoId;
  }

  public String getAntesJson() {
    return antesJson;
  }

  public String getDepoisJson() {
    return depoisJson;
  }

  public String getAntesHash() {
    return antesHash;
  }

  public String getDepoisHash() {
    return depoisHash;
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

  public OrigemAuditoria getOrigem() {
    return origem;
  }

  public ResultadoAuditoria getResultado() {
    return resultado;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static AuditoriaEventoEntity registrar(
      UUID id,
      UUID atorUsuarioId,
      String acao,
      String recursoTipo,
      UUID recursoId,
      String antesJson,
      String depoisJson,
      String requestId,
      OffsetDateTime criadoEm) {
    AuditoriaEventoEntity entity = new AuditoriaEventoEntity();
    entity.id = id;
    entity.atorUsuarioId = atorUsuarioId;
    entity.acao = acao;
    entity.recursoTipo = recursoTipo;
    entity.recursoId = recursoId;
    entity.antesJson = antesJson;
    entity.depoisJson = depoisJson;
    entity.antesHash = null;
    entity.depoisHash = null;
    entity.requestId = requestId;
    entity.ipHash = null;
    entity.userAgentHash = null;
    entity.origem = OrigemAuditoria.ADMIN;
    entity.resultado = ResultadoAuditoria.SUCESSO;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public static AuditoriaEventoEntity registrarSistema(
      UUID id,
      UUID atorUsuarioId,
      String acao,
      String recursoTipo,
      UUID recursoId,
      String antesJson,
      String depoisJson,
      String requestId,
      OffsetDateTime criadoEm) {
    AuditoriaEventoEntity entity = registrar(
        id,
        atorUsuarioId,
        acao,
        recursoTipo,
        recursoId,
        antesJson,
        depoisJson,
        requestId,
        criadoEm);
    entity.origem = OrigemAuditoria.SISTEMA;
    return entity;
  }

}
