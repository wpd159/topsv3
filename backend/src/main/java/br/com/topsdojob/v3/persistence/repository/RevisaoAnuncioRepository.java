package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevisaoAnuncioRepository
        extends JpaRepository<RevisaoAnuncioEntity, UUID>, JpaSpecificationExecutor<RevisaoAnuncioEntity> {
    long countByStatus(StatusRevisaoAnuncio status);

    long countByAnuncioId(UUID anuncioId);

    @Query("""
            select r.anuncioId as anuncioId, count(r) as totalRevisoes
            from RevisaoAnuncioEntity r
            where r.anuncioId in :anuncioIds
            group by r.anuncioId
            """)
    List<ContagemPorAnuncioProjection> countByAnuncioIdIn(@Param("anuncioIds") Collection<UUID> anuncioIds);

    boolean existsByAnuncioIdAndStatusIn(UUID anuncioId, Collection<StatusRevisaoAnuncio> statuses);

    List<RevisaoAnuncioEntity> findByAnuncioId(UUID anuncioId);

    List<RevisaoAnuncioEntity> findByAnuncioIdInAndStatusInOrderByCriadoEmDesc(
            Collection<UUID> anuncioIds,
            Collection<StatusRevisaoAnuncio> statuses);

    Optional<RevisaoAnuncioEntity> findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(
            UUID anuncioId,
            Collection<StatusRevisaoAnuncio> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select revisao from RevisaoAnuncioEntity revisao where revisao.id = :id")
    Optional<RevisaoAnuncioEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<RevisaoAnuncioEntity> findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
            UUID anuncioId,
            StatusRevisaoAnuncio status);

    Page<RevisaoAnuncioEntity> findByAnuncioId(UUID anuncioId, Pageable pageable);

    long count(Specification<RevisaoAnuncioEntity> spec);

    interface ContagemPorAnuncioProjection {
        UUID getAnuncioId();

        long getTotalRevisoes();
    }
}
