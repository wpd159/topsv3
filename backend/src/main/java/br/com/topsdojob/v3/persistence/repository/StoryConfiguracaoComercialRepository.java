package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StoryConfiguracaoComercialRepository
    extends JpaRepository<StoryConfiguracaoComercialEntity, Short> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select configuracao from StoryConfiguracaoComercialEntity configuracao where configuracao.id = 1")
  Optional<StoryConfiguracaoComercialEntity> findForUpdate();
}
