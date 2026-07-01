package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryAnuncioRepository extends JpaRepository<StoryAnuncioEntity, UUID> {
    List<StoryAnuncioEntity> findByAnuncioMidiaIdIn(Collection<UUID> anuncioMidiaIds);
}
