package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredencialUsuarioRepository extends JpaRepository<CredencialUsuarioEntity, UUID> {
  Optional<CredencialUsuarioEntity> findByUsuarioId(UUID usuarioId);
}
