package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StorySelecaoAdministrativaRepository
        extends JpaRepository<StorySelecaoAdministrativaEntity, Short> {

    default Optional<StorySelecaoAdministrativaEntity> atual() {
        return findById(StorySelecaoAdministrativaEntity.SINGLETON_ID);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select selecao from StorySelecaoAdministrativaEntity selecao where selecao.singletonId = 1")
    Optional<StorySelecaoAdministrativaEntity> bloquearSingleton();

    @Query(value = "select pg_advisory_xact_lock(864219)", nativeQuery = true)
    void bloquearOperacao();
}
