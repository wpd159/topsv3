package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredencialUsuarioRepository extends JpaRepository<CredencialUsuarioEntity, UUID> {
  Optional<CredencialUsuarioEntity> findByUsuarioId(UUID usuarioId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CredencialUsuarioEntity c where c.usuarioId = :usuarioId")
  Optional<CredencialUsuarioEntity> findByUsuarioIdForUpdate(@Param("usuarioId") UUID usuarioId);
}
