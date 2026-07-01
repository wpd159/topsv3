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

}
