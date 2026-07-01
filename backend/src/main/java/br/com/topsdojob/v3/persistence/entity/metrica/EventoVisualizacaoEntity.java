package br.com.topsdojob.v3.persistence.entity.metrica;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DispositivoMetrica;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evento_visualizacao")
public class EventoVisualizacaoEntity {
  protected EventoVisualizacaoEntity() {
  }

  private EventoVisualizacaoEntity(
      UUID id,
      UUID anuncioId,
      String visitanteHash,
      String ipHash,
      String userAgentHash,
      String refererHash,
      String origemPais,
      String origemUf,
      String origemCidade,
      DispositivoMetrica dispositivo,
      String requestId,
      OffsetDateTime criadoEm) {
    this.id = id;
    this.anuncioId = anuncioId;
    this.visitanteHash = visitanteHash;
    this.ipHash = ipHash;
    this.userAgentHash = userAgentHash;
    this.refererHash = refererHash;
    this.origemPais = origemPais;
    this.origemUf = origemUf;
    this.origemCidade = origemCidade;
    this.dispositivo = dispositivo;
    this.requestId = requestId;
    this.criadoEm = criadoEm;
  }

  public static EventoVisualizacaoEntity registrar(
      UUID id,
      UUID anuncioId,
      String visitanteHash,
      String ipHash,
      String userAgentHash,
      String refererHash,
      String origemPais,
      String origemUf,
      String origemCidade,
      DispositivoMetrica dispositivo,
      String requestId,
      OffsetDateTime criadoEm) {
    return new EventoVisualizacaoEntity(
        id,
        anuncioId,
        visitanteHash,
        ipHash,
        userAgentHash,
        refererHash,
        origemPais,
        origemUf,
        origemCidade,
        dispositivo,
        requestId,
        criadoEm);
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "visitante_hash")
  private String visitanteHash;

  @Column(name = "ip_hash")
  private String ipHash;

  @Column(name = "user_agent_hash")
  private String userAgentHash;

  @Column(name = "referer_hash")
  private String refererHash;

  @Column(name = "origem_pais", columnDefinition = "char(2)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String origemPais;

  @Column(name = "origem_uf", columnDefinition = "char(2)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String origemUf;

  @Column(name = "origem_cidade")
  private String origemCidade;

  @Enumerated(EnumType.STRING)
  @Column(name = "dispositivo")
  private DispositivoMetrica dispositivo;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public String getVisitanteHash() {
    return visitanteHash;
  }

  public String getIpHash() {
    return ipHash;
  }

  public String getUserAgentHash() {
    return userAgentHash;
  }

  public String getRefererHash() {
    return refererHash;
  }

  public String getOrigemPais() {
    return origemPais;
  }

  public String getOrigemUf() {
    return origemUf;
  }

  public String getOrigemCidade() {
    return origemCidade;
  }

  public DispositivoMetrica getDispositivo() {
    return dispositivo;
  }

  public String getRequestId() {
    return requestId;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }
}
