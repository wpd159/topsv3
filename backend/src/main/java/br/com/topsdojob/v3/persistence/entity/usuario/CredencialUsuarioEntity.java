package br.com.topsdojob.v3.persistence.entity.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credencial_usuario")
public class CredencialUsuarioEntity {
  protected CredencialUsuarioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "senha_hash")
  private String senhaHash;

  @Column(name = "algoritmo")
  private String algoritmo;

  @Column(name = "alterada_em")
  private OffsetDateTime alteradaEm;

  @Column(name = "precisa_redefinir")
  private Boolean precisaRedefinir;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getSenhaHash() {
    return senhaHash;
  }

  public String getAlgoritmo() {
    return algoritmo;
  }

  public OffsetDateTime getAlteradaEm() {
    return alteradaEm;
  }

  public Boolean getPrecisaRedefinir() {
    return precisaRedefinir;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
