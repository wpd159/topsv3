package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryAnuncioRepository extends JpaRepository<StoryAnuncioEntity, UUID> {
    long countByStatus(StatusStoryAnuncio status);

    @Query("""
        select count(story)
        from StoryAnuncioEntity story
        where story.status = :status
          and story.inicioEm <= :agora
          and story.fimEm > :agora
        """)
    long countAtivos(
        @Param("status") StatusStoryAnuncio status,
        @Param("agora") OffsetDateTime agora);

    List<StoryAnuncioEntity> findByAnuncioMidiaIdIn(Collection<UUID> anuncioMidiaIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select story from StoryAnuncioEntity story
        where story.anuncioMidiaId in :anuncioMidiaIds
        order by story.id
        """)
    List<StoryAnuncioEntity> findByAnuncioMidiaIdInForUpdate(
        @Param("anuncioMidiaIds") Collection<UUID> anuncioMidiaIds);

    List<StoryAnuncioEntity> findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio status);

    Optional<StoryAnuncioEntity> findByIdAndStatus(UUID id, StatusStoryAnuncio status);

    Optional<StoryAnuncioEntity> findByAnuncioIdAndCriadoPorAndIdempotencyKey(
        UUID anuncioId,
        UUID criadoPor,
        String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct story
        from StoryAnuncioEntity story
        where story.anuncioId = :anuncioId
           or story.anuncioMidiaId in (
             select midia.id
             from AnuncioMidiaEntity midia
             where midia.anuncioId = :anuncioId
           )
        order by story.id
        """)
    List<StoryAnuncioEntity> findByAnuncioIdForUpdate(@Param("anuncioId") UUID anuncioId);

    @Query("""
        select distinct story
        from StoryAnuncioEntity story
        where story.anuncioId in :anuncioIds
           or story.anuncioMidiaId in (
             select midia.id
             from AnuncioMidiaEntity midia
             where midia.anuncioId in :anuncioIds
           )
        """)
    List<StoryAnuncioEntity> findByAnuncioIds(@Param("anuncioIds") Collection<UUID> anuncioIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct story
        from StoryAnuncioEntity story
        where story.anuncioId in :anuncioIds
           or story.anuncioMidiaId in (
             select midia.id
             from AnuncioMidiaEntity midia
             where midia.anuncioId in :anuncioIds
           )
        order by story.id
        """)
    List<StoryAnuncioEntity> findByAnuncioIdsForUpdate(
        @Param("anuncioIds") Collection<UUID> anuncioIds);
}
