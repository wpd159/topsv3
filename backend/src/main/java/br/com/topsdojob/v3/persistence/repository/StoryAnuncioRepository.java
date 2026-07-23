package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import jakarta.persistence.LockModeType;
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
}
