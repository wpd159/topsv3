package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {
  Optional<UsuarioEntity> findByEmailNormalizado(String emailNormalizado);

  Optional<UsuarioEntity> findByTelefoneNormalizado(String telefoneNormalizado);

  boolean existsByEmailNormalizado(String emailNormalizado);

  boolean existsByTelefoneNormalizado(String telefoneNormalizado);

  boolean existsByNomeIgnoreCase(String nome);
}
