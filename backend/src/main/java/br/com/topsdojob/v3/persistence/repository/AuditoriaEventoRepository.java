package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEventoEntity, UUID> {
  boolean existsByAcaoAndRecursoIdAndRequestId(String acao, UUID recursoId, String requestId);

  org.springframework.data.domain.Page<AuditoriaEventoEntity> findByAcaoInOrderByCriadoEmDesc(
      java.util.Collection<String> acoes,
      org.springframework.data.domain.Pageable pageable);

  List<AuditoriaEventoEntity> findByRecursoIdInOrderByCriadoEmDesc(
      Collection<UUID> recursoIds,
      Pageable pageable);

  List<AuditoriaEventoEntity> findByRecursoTipoAndRecursoIdOrderByCriadoEmDesc(
      String recursoTipo,
      UUID recursoId,
      Pageable pageable);
}
