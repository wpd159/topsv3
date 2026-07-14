package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {
  Optional<UsuarioEntity> findByEmailNormalizado(String emailNormalizado);

  Optional<UsuarioEntity> findByNomeIgnoreCase(String nome);

  Optional<UsuarioEntity> findByTelefoneNormalizado(String telefoneNormalizado);

  Optional<UsuarioEntity> findByCpfNormalizado(String cpfNormalizado);

  boolean existsByEmailNormalizado(String emailNormalizado);

  boolean existsByTelefoneNormalizado(String telefoneNormalizado);

  boolean existsByNomeIgnoreCase(String nome);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select usuario from UsuarioEntity usuario where usuario.id = :id")
  Optional<UsuarioEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      select usuario from UsuarioEntity usuario
      where usuario.tipoConta = :tipoConta
        and (
          lower(coalesce(usuario.nome, '')) like lower(concat('%', :query, '%'))
          or lower(coalesce(usuario.emailNormalizado, '')) like lower(concat('%', :query, '%'))
        )
      order by usuario.nome asc
      """)
  java.util.List<UsuarioEntity> buscarAnunciantes(
      @Param("query") String query,
      @Param("tipoConta") br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario tipoConta,
      org.springframework.data.domain.Pageable pageable);
}
