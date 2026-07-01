package br.com.topsdojob.v3.persistence.entity.comercial;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemContatoComercial;
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
@Table(name = "comercial_contato")
public class ComercialContatoEntity {
  protected ComercialContatoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "status_id")
  private UUID statusId;

  @Column(name = "nome_contato")
  private String nomeContato;

  @Column(name = "email_normalizado")
  private String emailNormalizado;

  @Column(name = "telefone_normalizado")
  private String telefoneNormalizado;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem")
  private OrigemContatoComercial origem;

  @Column(name = "campanha_codigo")
  private String campanhaCodigo;

  @Column(name = "responsavel_usuario_id")
  private UUID responsavelUsuarioId;

  @Column(name = "proxima_acao_em")
  private OffsetDateTime proximaAcaoEm;

  @Column(name = "cortesia_concedida")
  private Boolean cortesiaConcedida;

  @Column(name = "observacao_resumida")
  private String observacaoResumida;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getStatusId() {
    return statusId;
  }

  public String getNomeContato() {
    return nomeContato;
  }

  public String getEmailNormalizado() {
    return emailNormalizado;
  }

  public String getTelefoneNormalizado() {
    return telefoneNormalizado;
  }

  public OrigemContatoComercial getOrigem() {
    return origem;
  }

  public String getCampanhaCodigo() {
    return campanhaCodigo;
  }

  public UUID getResponsavelUsuarioId() {
    return responsavelUsuarioId;
  }

  public OffsetDateTime getProximaAcaoEm() {
    return proximaAcaoEm;
  }

  public Boolean getCortesiaConcedida() {
    return cortesiaConcedida;
  }

  public String getObservacaoResumida() {
    return observacaoResumida;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public Integer getVersao() {
    return versao;
  }

}
