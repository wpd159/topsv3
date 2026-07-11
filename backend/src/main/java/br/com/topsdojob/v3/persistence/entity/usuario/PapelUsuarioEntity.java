package br.com.topsdojob.v3.persistence.entity.usuario;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "papel_usuario")
@IdClass(PapelUsuarioEntity.PapelUsuarioId.class)
public class PapelUsuarioEntity {
  protected PapelUsuarioEntity() {
  }

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "papel")
  private PapelUsuario papel;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public PapelUsuario getPapel() {
    return papel;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static PapelUsuarioEntity criarUsuarioPublico(UUID usuarioId, OffsetDateTime criadoEm) {
    PapelUsuarioEntity entity = new PapelUsuarioEntity();
    entity.usuarioId = usuarioId;
    entity.papel = PapelUsuario.USUARIO;
    entity.criadoPor = null;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public static PapelUsuarioEntity criarAdminHomologacao(UUID usuarioId, OffsetDateTime criadoEm) {
    PapelUsuarioEntity entity = new PapelUsuarioEntity();
    entity.usuarioId = usuarioId;
    entity.papel = PapelUsuario.ADMIN;
    entity.criadoPor = null;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public static class PapelUsuarioId implements Serializable {
    private UUID usuarioId;
    private PapelUsuario papel;

    public PapelUsuarioId() {
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof PapelUsuarioId that)) {
        return false;
      }
      return Objects.equals(usuarioId, that.usuarioId) && papel == that.papel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(usuarioId, papel);
    }
  }
}
