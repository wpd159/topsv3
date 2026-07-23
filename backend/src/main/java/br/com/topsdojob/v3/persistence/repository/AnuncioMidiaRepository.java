package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;

public interface AnuncioMidiaRepository
        extends JpaRepository<AnuncioMidiaEntity, UUID>, JpaSpecificationExecutor<AnuncioMidiaEntity> {
    long countByStatus(StatusAnuncioMidia status);

    long countByVisibilidadeMidia(VisibilidadeMidia visibilidadeMidia);

    long countByStatusAndTipoNot(StatusAnuncioMidia status, TipoAnuncioMidia tipo);

    long countByVisibilidadeMidiaAndTipoNot(VisibilidadeMidia visibilidadeMidia, TipoAnuncioMidia tipo);

    long countByAnuncioId(UUID anuncioId);

    long countByAnuncioIdAndTipoNot(UUID anuncioId, TipoAnuncioMidia tipo);

    @Query("""
            select m.anuncioId as anuncioId, count(m) as totalMidias
            from AnuncioMidiaEntity m
            where m.anuncioId in :anuncioIds
              and m.tipo <> :tipoExcluido
            group by m.anuncioId
            """)
    List<ContagemPorAnuncioProjection> countByAnuncioIdInAndTipoNot(
            @Param("anuncioIds") Collection<UUID> anuncioIds,
            @Param("tipoExcluido") TipoAnuncioMidia tipoExcluido);

    List<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId);

    List<AnuncioMidiaEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select midia
            from AnuncioMidiaEntity midia
            where midia.anuncioId = :anuncioId
            order by midia.id
            """)
    List<AnuncioMidiaEntity> findByAnuncioIdForUpdate(@Param("anuncioId") UUID anuncioId);

    List<AnuncioMidiaEntity> findByIdIn(Collection<UUID> ids);

    List<AnuncioMidiaEntity> findByArquivoMidiaId(UUID arquivoMidiaId);

    Page<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId, Pageable pageable);

    Page<AnuncioMidiaEntity> findByAnuncioIdAndTipoNot(
            UUID anuncioId,
            TipoAnuncioMidia tipo,
            Pageable pageable);

    Page<AnuncioMidiaEntity> findByAnuncioIdAndTipoNotAndStatusNot(
            UUID anuncioId,
            TipoAnuncioMidia tipo,
            StatusAnuncioMidia status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select midia from AnuncioMidiaEntity midia where midia.id = :id")
    java.util.Optional<AnuncioMidiaEntity> findByIdForUpdate(@Param("id") UUID id);

    long count(Specification<AnuncioMidiaEntity> spec);

    interface ContagemPorAnuncioProjection {
        UUID getAnuncioId();

        long getTotalMidias();
    }
}
