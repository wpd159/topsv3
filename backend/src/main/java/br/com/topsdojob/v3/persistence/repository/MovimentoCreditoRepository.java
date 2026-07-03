package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentoCreditoRepository extends JpaRepository<MovimentoCreditoEntity, UUID> {
  List<MovimentoCreditoEntity> findByUsuarioIdOrderByCriadoEmAsc(UUID usuarioId);

  Page<MovimentoCreditoEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId, Pageable pageable);

  List<MovimentoCreditoEntity> findByReferenciaTipoAndReferenciaIdIn(String referenciaTipo, Collection<UUID> referenciaIds);
}
