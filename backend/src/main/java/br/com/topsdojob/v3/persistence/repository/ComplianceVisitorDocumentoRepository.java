package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorDocumentoEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplianceVisitorDocumentoRepository
    extends JpaRepository<ComplianceVisitorDocumentoEntity, UUID> {

  Optional<ComplianceVisitorDocumentoEntity> findBySessionHashAndIdempotenciaHash(
      String sessionHash,
      String idempotenciaHash);

  Optional<ComplianceVisitorDocumentoEntity> findTopByChallengeIdOrderByCriadoEmDesc(
      UUID challengeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from ComplianceVisitorDocumentoEntity d where d.id = :id")
  Optional<ComplianceVisitorDocumentoEntity> findByIdForUpdate(@Param("id") UUID id);

  Page<ComplianceVisitorDocumentoEntity> findAllByOrderByCriadoEmDesc(Pageable pageable);
}
