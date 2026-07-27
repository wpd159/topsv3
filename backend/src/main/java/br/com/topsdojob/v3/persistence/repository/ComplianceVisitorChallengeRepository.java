package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplianceVisitorChallengeRepository
    extends JpaRepository<ComplianceVisitorChallengeEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from ComplianceVisitorChallengeEntity c where c.id = :id")
  Optional<ComplianceVisitorChallengeEntity> findByIdForUpdate(@Param("id") UUID id);

  Optional<ComplianceVisitorChallengeEntity> findBySessionHashAndIdempotenciaHash(
      String sessionHash,
      String idempotenciaHash);

  Optional<ComplianceVisitorChallengeEntity> findTopBySessionHashOrderByCriadoEmDesc(
      String sessionHash);

  long countBySessionHashAndCriadoEmAfter(String sessionHash, OffsetDateTime criadoEm);

  long countBySessionHashAndEscopoAndCriadoEmAfter(
      String sessionHash,
      EscopoConteudoVisitante escopo,
      OffsetDateTime criadoEm);

  long countBySessionHashAndStatusInAndAtualizadoEmAfter(
      String sessionHash,
      Collection<StatusChallengeVisitante> statuses,
      OffsetDateTime atualizadoEm);

  @Query(
      value = """
          select count(distinct anuncio_id)
          from compliance_visitor_challenge
          where session_hash = :sessionHash
            and criado_em >= :desde
            and anuncio_id is not null
          """,
      nativeQuery = true)
  long countDistinctAnunciosRecentes(
      @Param("sessionHash") String sessionHash,
      @Param("desde") OffsetDateTime desde);
}
