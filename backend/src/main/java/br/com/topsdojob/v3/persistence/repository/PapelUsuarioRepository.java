package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PapelUsuarioRepository extends JpaRepository<PapelUsuarioEntity, PapelUsuarioEntity.PapelUsuarioId> {
  List<PapelUsuarioEntity> findByUsuarioId(UUID usuarioId);
}
