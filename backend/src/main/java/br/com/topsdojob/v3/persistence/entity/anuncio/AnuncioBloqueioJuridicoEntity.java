package br.com.topsdojob.v3.persistence.entity.anuncio;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.CategoriaBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio_bloqueio_juridico")
public class AnuncioBloqueioJuridicoEntity {

  protected AnuncioBloqueioJuridicoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "escopo")
  private EscopoBloqueioJuridico escopo;

  @Enumerated(EnumType.STRING)
  @Column(name = "categoria")
  private CategoriaBloqueioJuridico categoria;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_usuario_anterior")
  private StatusUsuario statusUsuarioAnterior;

  @Column(name = "motivo")
  private String motivo;

  @Column(name = "observacao_interna")
  private String observacaoInterna;

  @Column(name = "bloqueado_por_id")
  private UUID bloqueadoPorId;

  @Column(name = "bloqueado_em")
  private OffsetDateTime bloqueadoEm;

  @Column(name = "bloqueio_request_id")
  private String bloqueioRequestId;

  @Column(name = "anuncio_desbloqueado_por_id")
  private UUID anuncioDesbloqueadoPorId;

  @Column(name = "anuncio_desbloqueado_em")
  private OffsetDateTime anuncioDesbloqueadoEm;

  @Column(name = "anuncio_desbloqueio_request_id")
  private String anuncioDesbloqueioRequestId;

  @Column(name = "usuario_desbloqueado_por_id")
  private UUID usuarioDesbloqueadoPorId;

  @Column(name = "usuario_desbloqueado_em")
  private OffsetDateTime usuarioDesbloqueadoEm;

  @Column(name = "usuario_desbloqueio_request_id")
  private String usuarioDesbloqueioRequestId;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public EscopoBloqueioJuridico getEscopo() {
    return escopo;
  }

  public CategoriaBloqueioJuridico getCategoria() {
    return categoria;
  }

  public StatusUsuario getStatusUsuarioAnterior() {
    return statusUsuarioAnterior;
  }

  public String getMotivo() {
    return motivo;
  }

  public String getObservacaoInterna() {
    return observacaoInterna;
  }

  public UUID getBloqueadoPorId() {
    return bloqueadoPorId;
  }

  public OffsetDateTime getBloqueadoEm() {
    return bloqueadoEm;
  }

  public String getBloqueioRequestId() {
    return bloqueioRequestId;
  }

  public UUID getAnuncioDesbloqueadoPorId() {
    return anuncioDesbloqueadoPorId;
  }

  public OffsetDateTime getAnuncioDesbloqueadoEm() {
    return anuncioDesbloqueadoEm;
  }

  public String getAnuncioDesbloqueioRequestId() {
    return anuncioDesbloqueioRequestId;
  }

  public UUID getUsuarioDesbloqueadoPorId() {
    return usuarioDesbloqueadoPorId;
  }

  public OffsetDateTime getUsuarioDesbloqueadoEm() {
    return usuarioDesbloqueadoEm;
  }

  public String getUsuarioDesbloqueioRequestId() {
    return usuarioDesbloqueioRequestId;
  }

  public boolean anuncioBloqueado() {
    return anuncioDesbloqueadoEm == null;
  }

  public boolean usuarioBloqueado() {
    return escopo == EscopoBloqueioJuridico.ANUNCIO_E_USUARIO
        && usuarioDesbloqueadoEm == null;
  }

  public static AnuncioBloqueioJuridicoEntity registrar(
      UUID id,
      UUID anuncioId,
      UUID usuarioId,
      EscopoBloqueioJuridico escopo,
      CategoriaBloqueioJuridico categoria,
      StatusUsuario statusUsuarioAnterior,
      String motivo,
      String observacaoInterna,
      UUID atorId,
      String requestId,
      OffsetDateTime agora) {
    AnuncioBloqueioJuridicoEntity entity = new AnuncioBloqueioJuridicoEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.usuarioId = usuarioId;
    entity.escopo = escopo;
    entity.categoria = categoria;
    entity.statusUsuarioAnterior = statusUsuarioAnterior;
    entity.motivo = motivo;
    entity.observacaoInterna = observacaoInterna;
    entity.bloqueadoPorId = atorId;
    entity.bloqueadoEm = agora;
    entity.bloqueioRequestId = requestId;
    entity.versao = 0;
    return entity;
  }

  public void desbloquearAnuncio(UUID atorId, String requestId, OffsetDateTime agora) {
    if (!anuncioBloqueado()) {
      throw new IllegalStateException("anuncio ja desbloqueado");
    }
    anuncioDesbloqueadoPorId = atorId;
    anuncioDesbloqueadoEm = agora;
    anuncioDesbloqueioRequestId = requestId;
  }

  public void desbloquearUsuario(UUID atorId, String requestId, OffsetDateTime agora) {
    if (!usuarioBloqueado()) {
      throw new IllegalStateException("usuario nao possui bloqueio ativo");
    }
    usuarioDesbloqueadoPorId = atorId;
    usuarioDesbloqueadoEm = agora;
    usuarioDesbloqueioRequestId = requestId;
  }
}
