package br.com.topsdojob.v3.persistence.entity.usuario;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class UsuarioEntity {
  protected UsuarioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "nome")
  private String nome;

  @Column(name = "email_normalizado")
  private String emailNormalizado;

  @Column(name = "telefone_normalizado")
  private String telefoneNormalizado;

  @Column(name = "data_nascimento")
  private LocalDate dataNascimento;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusUsuario status;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_conta")
  private TipoContaUsuario tipoConta;

  @Column(name = "email_verificado_em")
  private OffsetDateTime emailVerificadoEm;

  @Column(name = "telefone_verificado_em")
  private OffsetDateTime telefoneVerificadoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "desativado_em")
  private OffsetDateTime desativadoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public String getNome() {
    return nome;
  }

  public String getEmailNormalizado() {
    return emailNormalizado;
  }

  public String getTelefoneNormalizado() {
    return telefoneNormalizado;
  }

  public LocalDate getDataNascimento() {
    return dataNascimento;
  }

  public StatusUsuario getStatus() {
    return status;
  }

  public TipoContaUsuario getTipoConta() {
    return tipoConta;
  }

  public OffsetDateTime getEmailVerificadoEm() {
    return emailVerificadoEm;
  }

  public OffsetDateTime getTelefoneVerificadoEm() {
    return telefoneVerificadoEm;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public OffsetDateTime getDesativadoEm() {
    return desativadoEm;
  }

  public Integer getVersao() {
    return versao;
  }

  public void atualizarPerfilPublico(
      String nome,
      String telefoneNormalizado,
      OffsetDateTime atualizadoEm) {
    this.nome = nome;
    this.telefoneNormalizado = telefoneNormalizado;
    this.atualizadoEm = atualizadoEm;
  }

  public static UsuarioEntity criarSolicitacaoLocal(
      UUID id,
      String nome,
      String emailNormalizado,
      String telefoneNormalizado,
      OffsetDateTime criadoEm) {
    UsuarioEntity entity = new UsuarioEntity();
    entity.id = id;
    entity.nome = nome;
    entity.emailNormalizado = emailNormalizado;
    entity.telefoneNormalizado = telefoneNormalizado;
    entity.status = StatusUsuario.PENDENTE;
    entity.tipoConta = TipoContaUsuario.ANUNCIANTE;
    entity.emailVerificadoEm = null;
    entity.telefoneVerificadoEm = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    entity.desativadoEm = null;
    entity.versao = 0;
    return entity;
  }

  public static UsuarioEntity criarCadastroPublico(
      UUID id,
      String nome,
      String emailNormalizado,
      String telefoneNormalizado,
      LocalDate dataNascimento,
      OffsetDateTime criadoEm) {
    UsuarioEntity entity = new UsuarioEntity();
    entity.id = id;
    entity.nome = nome;
    entity.emailNormalizado = emailNormalizado;
    entity.telefoneNormalizado = telefoneNormalizado;
    entity.dataNascimento = dataNascimento;
    entity.status = StatusUsuario.ATIVO;
    entity.tipoConta = TipoContaUsuario.ANUNCIANTE;
    entity.emailVerificadoEm = null;
    entity.telefoneVerificadoEm = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    entity.desativadoEm = null;
    entity.versao = 0;
    return entity;
  }

  public void sincronizarDataNascimentoHomologacao(
      LocalDate dataNascimento,
      OffsetDateTime atualizadoEm) {
    this.dataNascimento = dataNascimento;
    this.atualizadoEm = atualizadoEm;
  }

  public static UsuarioEntity criarStaffHomologacao(
      UUID id,
      String nome,
      String emailNormalizado,
      OffsetDateTime criadoEm) {
    UsuarioEntity entity = new UsuarioEntity();
    entity.id = id;
    entity.nome = nome;
    entity.emailNormalizado = emailNormalizado;
    entity.telefoneNormalizado = null;
    entity.status = StatusUsuario.ATIVO;
    entity.tipoConta = TipoContaUsuario.STAFF;
    entity.emailVerificadoEm = criadoEm;
    entity.telefoneVerificadoEm = null;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    entity.desativadoEm = null;
    entity.versao = 0;
    return entity;
  }

}
