package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusTokenVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorTokenEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplianceVisitorTokenRepository
    extends JpaRepository<ComplianceVisitorTokenEntity, UUID> {

  Optional<ComplianceVisitorTokenEntity> findByTokenHash(String tokenHash);

  List<ComplianceVisitorTokenEntity> findByChallengeIdAndStatus(
      UUID challengeId,
      StatusTokenVisitante status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select t
      from ComplianceVisitorTokenEntity t
      where t.sessionHash = :sessionHash and t.status = :status
      """)
  List<ComplianceVisitorTokenEntity> findBySessionHashAndStatusForUpdate(
      @Param("sessionHash") String sessionHash,
      @Param("status") StatusTokenVisitante status);

  long countBySessionHashAndStatusAndExpiraEmAfter(
      String sessionHash,
      StatusTokenVisitante status,
      OffsetDateTime expiraEm);

  long countBySessionHashAndEmitidoEmAfter(
      String sessionHash,
      OffsetDateTime emitidoEm);
}
