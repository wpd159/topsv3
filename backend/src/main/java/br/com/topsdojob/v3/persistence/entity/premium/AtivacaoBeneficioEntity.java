package br.com.topsdojob.v3.persistence.entity.premium;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ativacao_beneficio")
public class AtivacaoBeneficioEntity {
  protected AtivacaoBeneficioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "beneficio_id")
  private UUID beneficioId;

  @Column(name = "opcao_id")
  private UUID opcaoId;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "grupo_ativacao_id")
  private UUID grupoAtivacaoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemBeneficio origem;

  @Column(name = "ator_usuario_id")
  private UUID atorUsuarioId;

  @Column(name = "campanha_codigo")
  private String campanhaCodigo;

  @Column(name = "inicio_em")
  private OffsetDateTime inicioEm;

  @Column(name = "fim_em")
  private OffsetDateTime fimEm;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusAtivacaoBeneficio status;

  @Column(name = "custo_creditos_snapshot")
  private Integer custoCreditosSnapshot;

  @Column(name = "preco_snapshot", precision = 12, scale = 2)
  private BigDecimal precoSnapshot;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "revogada_em")
  private OffsetDateTime revogadaEm;

  @Column(name = "motivo_revogacao")
  private String motivoRevogacao;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getBeneficioId() {
    return beneficioId;
  }

  public UUID getOpcaoId() {
    return opcaoId;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getGrupoAtivacaoId() {
    return grupoAtivacaoId;
  }

  public OrigemBeneficio getOrigem() {
    return origem;
  }

  public UUID getAtorUsuarioId() {
    return atorUsuarioId;
  }

  public String getCampanhaCodigo() {
    return campanhaCodigo;
  }

  public OffsetDateTime getInicioEm() {
    return inicioEm;
  }

  public OffsetDateTime getFimEm() {
    return fimEm;
  }

  public StatusAtivacaoBeneficio getStatus() {
    return status;
  }

  public Integer getCustoCreditosSnapshot() {
    return custoCreditosSnapshot;
  }

  public BigDecimal getPrecoSnapshot() {
    return precoSnapshot;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public OffsetDateTime getRevogadaEm() {
    return revogadaEm;
  }

  public String getMotivoRevogacao() {
    return motivoRevogacao;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static AtivacaoBeneficioEntity criarCompraComCreditos(
      UUID id,
      UUID beneficioId,
      UUID opcaoId,
      UUID usuarioId,
      UUID anuncioId,
      UUID grupoAtivacaoId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      int custoCreditos,
      String idempotencyKey,
      OffsetDateTime criadoEm) {
    AtivacaoBeneficioEntity entity = new AtivacaoBeneficioEntity();
    entity.id = id;
    entity.beneficioId = beneficioId;
    entity.opcaoId = opcaoId;
    entity.usuarioId = usuarioId;
    entity.anuncioId = anuncioId;
    entity.grupoAtivacaoId = grupoAtivacaoId;
    entity.origem = OrigemBeneficio.CREDITO;
    entity.atorUsuarioId = usuarioId;
    entity.campanhaCodigo = null;
    entity.inicioEm = inicioEm;
    entity.fimEm = fimEm;
    entity.status = StatusAtivacaoBeneficio.ATIVA;
    entity.custoCreditosSnapshot = custoCreditos;
    entity.precoSnapshot = null;
    entity.idempotencyKey = idempotencyKey;
    entity.revogadaEm = null;
    entity.motivoRevogacao = null;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public static AtivacaoBeneficioEntity criarAdministrativa(
      UUID id,
      UUID beneficioId,
      UUID opcaoId,
      UUID usuarioId,
      UUID anuncioId,
      UUID grupoAtivacaoId,
      UUID atorUsuarioId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      String idempotencyKey,
      OffsetDateTime criadoEm) {
    AtivacaoBeneficioEntity entity = new AtivacaoBeneficioEntity();
    entity.id = id;
    entity.beneficioId = beneficioId;
    entity.opcaoId = opcaoId;
    entity.usuarioId = usuarioId;
    entity.anuncioId = anuncioId;
    entity.grupoAtivacaoId = grupoAtivacaoId;
    entity.origem = OrigemBeneficio.ADMIN;
    entity.atorUsuarioId = atorUsuarioId;
    entity.campanhaCodigo = null;
    entity.inicioEm = inicioEm;
    entity.fimEm = fimEm;
    entity.status = StatusAtivacaoBeneficio.ATIVA;
    entity.custoCreditosSnapshot = 0;
    entity.precoSnapshot = null;
    entity.idempotencyKey = idempotencyKey;
    entity.revogadaEm = null;
    entity.motivoRevogacao = null;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public void revogar(String motivo, OffsetDateTime agora) {
    this.status = StatusAtivacaoBeneficio.REVOGADA;
    this.revogadaEm = agora;
    this.motivoRevogacao = motivo;
  }

  public static AtivacaoBeneficioEntity criarFixtureHomologacao(
      UUID id,
      UUID beneficioId,
      UUID usuarioId,
      UUID anuncioId,
      UUID grupoAtivacaoId,
      OrigemBeneficio origem,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      StatusAtivacaoBeneficio status,
      int custoCreditos,
      BigDecimal preco,
      String idempotencyKey,
      OffsetDateTime criadoEm) {
    AtivacaoBeneficioEntity entity = new AtivacaoBeneficioEntity();
    entity.id = id;
    entity.sincronizarFixtureHomologacao(
        beneficioId,
        usuarioId,
        anuncioId,
        grupoAtivacaoId,
        origem,
        inicioEm,
        fimEm,
        status,
        custoCreditos,
        preco,
        idempotencyKey);
    entity.criadoEm = criadoEm;
    return entity;
  }

  public boolean sincronizarFixtureHomologacao(
      UUID beneficioId,
      UUID usuarioId,
      UUID anuncioId,
      UUID grupoAtivacaoId,
      OrigemBeneficio origem,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      StatusAtivacaoBeneficio status,
      int custoCreditos,
      BigDecimal preco,
      String idempotencyKey) {
    boolean alterado = !Objects.equals(this.beneficioId, beneficioId)
        || !Objects.equals(this.usuarioId, usuarioId)
        || !Objects.equals(this.anuncioId, anuncioId)
        || !Objects.equals(this.grupoAtivacaoId, grupoAtivacaoId)
        || this.origem != origem
        || !Objects.equals(this.inicioEm, inicioEm)
        || !Objects.equals(this.fimEm, fimEm)
        || this.status != status
        || !Objects.equals(this.custoCreditosSnapshot, custoCreditos)
        || !Objects.equals(this.precoSnapshot, preco)
        || !Objects.equals(this.idempotencyKey, idempotencyKey);
    this.beneficioId = beneficioId;
    this.opcaoId = null;
    this.usuarioId = usuarioId;
    this.anuncioId = anuncioId;
    this.grupoAtivacaoId = grupoAtivacaoId;
    this.origem = origem;
    this.atorUsuarioId = null;
    this.campanhaCodigo = null;
    this.inicioEm = inicioEm;
    this.fimEm = fimEm;
    this.status = status;
    this.custoCreditosSnapshot = custoCreditos;
    this.precoSnapshot = preco;
    this.idempotencyKey = idempotencyKey;
    this.revogadaEm = null;
    this.motivoRevogacao = null;
    return alterado;
  }

}
