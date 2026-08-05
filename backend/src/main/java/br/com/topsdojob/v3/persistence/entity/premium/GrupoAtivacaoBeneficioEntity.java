package br.com.topsdojob.v3.persistence.entity.premium;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "grupo_ativacao_beneficio")
public class GrupoAtivacaoBeneficioEntity {
  protected GrupoAtivacaoBeneficioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoGrupoAtivacaoBeneficio tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemBeneficio origem;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "campanha_codigo")
  private String campanhaCodigo;

  @Column(name = "validade_inicio_em")
  private OffsetDateTime validadeInicioEm;

  @Column(name = "validade_fim_em")
  private OffsetDateTime validadeFimEm;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusGrupoAtivacaoBeneficio status;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "observacao")
  private String observacao;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public TipoGrupoAtivacaoBeneficio getTipo() {
    return tipo;
  }

  public OrigemBeneficio getOrigem() {
    return origem;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getCampanhaCodigo() {
    return campanhaCodigo;
  }

  public OffsetDateTime getValidadeInicioEm() {
    return validadeInicioEm;
  }

  public OffsetDateTime getValidadeFimEm() {
    return validadeFimEm;
  }

  public StatusGrupoAtivacaoBeneficio getStatus() {
    return status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getObservacao() {
    return observacao;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public static GrupoAtivacaoBeneficioEntity criarCompraComCreditos(
      UUID id,
      UUID usuarioId,
      UUID anuncioId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      String idempotencyKey,
      OffsetDateTime agora) {
    GrupoAtivacaoBeneficioEntity entity = new GrupoAtivacaoBeneficioEntity();
    entity.id = id;
    entity.tipo = TipoGrupoAtivacaoBeneficio.PACOTE;
    entity.origem = OrigemBeneficio.CREDITO;
    entity.usuarioId = usuarioId;
    entity.anuncioId = anuncioId;
    entity.atorUsuarioId = usuarioId;
    entity.campanhaCodigo = null;
    entity.validadeInicioEm = inicioEm;
    entity.validadeFimEm = fimEm;
    entity.status = StatusGrupoAtivacaoBeneficio.ATIVO;
    entity.idempotencyKey = idempotencyKey;
    entity.observacao = "Ativacao por creditos";
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public static GrupoAtivacaoBeneficioEntity criarAdministrativa(
      UUID id,
      UUID usuarioId,
      UUID anuncioId,
      UUID atorUsuarioId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      String idempotencyKey,
      String observacao,
      OffsetDateTime agora) {
    GrupoAtivacaoBeneficioEntity entity = new GrupoAtivacaoBeneficioEntity();
    entity.id = id;
    entity.tipo = TipoGrupoAtivacaoBeneficio.ADMIN;
    entity.origem = OrigemBeneficio.ADMIN;
    entity.usuarioId = usuarioId;
    entity.anuncioId = anuncioId;
    entity.atorUsuarioId = atorUsuarioId;
    entity.campanhaCodigo = null;
    entity.validadeInicioEm = inicioEm;
    entity.validadeFimEm = fimEm;
    entity.status = StatusGrupoAtivacaoBeneficio.ATIVO;
    entity.idempotencyKey = idempotencyKey;
    entity.observacao = observacao;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public static GrupoAtivacaoBeneficioEntity criarFixtureHomologacao(
      UUID id,
      TipoGrupoAtivacaoBeneficio tipo,
      OrigemBeneficio origem,
      UUID usuarioId,
      UUID anuncioId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      StatusGrupoAtivacaoBeneficio status,
      String idempotencyKey,
      OffsetDateTime criadoEm) {
    GrupoAtivacaoBeneficioEntity entity = new GrupoAtivacaoBeneficioEntity();
    entity.id = id;
    entity.sincronizarFixtureHomologacao(
        tipo, origem, usuarioId, anuncioId, inicioEm, fimEm, status, idempotencyKey, criadoEm);
    entity.criadoEm = criadoEm;
    return entity;
  }

  public void estenderValidadeAte(OffsetDateTime fimEm, OffsetDateTime agora) {
    if (fimEm != null && (validadeFimEm == null || fimEm.isAfter(validadeFimEm))) {
      validadeFimEm = fimEm;
    }
    if (status == StatusGrupoAtivacaoBeneficio.EXPIRADO
        || status == StatusGrupoAtivacaoBeneficio.PLANEJADO) {
      status = StatusGrupoAtivacaoBeneficio.ATIVO;
    }
    atualizadoEm = agora;
  }

  public void desvincularAnuncioAposFalhaTecnica(OffsetDateTime agora) {
    this.anuncioId = null;
    this.atualizadoEm = agora;
  }

  public boolean sincronizarFixtureHomologacao(
      TipoGrupoAtivacaoBeneficio tipo,
      OrigemBeneficio origem,
      UUID usuarioId,
      UUID anuncioId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      StatusGrupoAtivacaoBeneficio status,
      String idempotencyKey,
      OffsetDateTime atualizadoEm) {
    boolean alterado = this.tipo != tipo
        || this.origem != origem
        || !Objects.equals(this.usuarioId, usuarioId)
        || !Objects.equals(this.anuncioId, anuncioId)
        || !Objects.equals(this.validadeInicioEm, inicioEm)
        || !Objects.equals(this.validadeFimEm, fimEm)
        || this.status != status
        || !Objects.equals(this.idempotencyKey, idempotencyKey);
    this.tipo = tipo;
    this.origem = origem;
    this.usuarioId = usuarioId;
    this.anuncioId = anuncioId;
    this.atorUsuarioId = null;
    this.campanhaCodigo = null;
    this.validadeInicioEm = inicioEm;
    this.validadeFimEm = fimEm;
    this.status = status;
    this.idempotencyKey = idempotencyKey;
    this.observacao = "Cenario ficticio de homologacao";
    this.atualizadoEm = atualizadoEm;
    return alterado;
  }

}
