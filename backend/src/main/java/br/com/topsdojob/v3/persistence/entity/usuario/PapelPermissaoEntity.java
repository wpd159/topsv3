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
@Table(name = "papel_permissao")
@IdClass(PapelPermissaoEntity.PapelPermissaoId.class)
public class PapelPermissaoEntity {
  protected PapelPermissaoEntity() {
  }

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "papel")
  private PapelUsuario papel;

  @Id
  @Column(name = "permissao_id")
  private UUID permissaoId;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public PapelUsuario getPapel() {
    return papel;
  }

  public UUID getPermissaoId() {
    return permissaoId;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public static class PapelPermissaoId implements Serializable {
    private PapelUsuario papel;
    private UUID permissaoId;

    public PapelPermissaoId() {
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof PapelPermissaoId that)) {
        return false;
      }
      return papel == that.papel && Objects.equals(permissaoId, that.permissaoId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(papel, permissaoId);
    }
  }
}
