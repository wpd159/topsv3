package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorRiskProfileEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplianceVisitorRiskProfileRepository
    extends JpaRepository<ComplianceVisitorRiskProfileEntity, UUID> {

  Optional<ComplianceVisitorRiskProfileEntity> findBySessionHash(String sessionHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select p
      from ComplianceVisitorRiskProfileEntity p
      where p.sessionHash = :sessionHash
      """)
  Optional<ComplianceVisitorRiskProfileEntity> findBySessionHashForUpdate(
      @Param("sessionHash") String sessionHash);

  Page<ComplianceVisitorRiskProfileEntity> findAllByOrderByAtualizadoEmDesc(Pageable pageable);
}
