package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StorySelecaoAdministrativaRepository
        extends JpaRepository<StorySelecaoAdministrativaEntity, Long> {

    List<StorySelecaoAdministrativaEntity> findByAtivaTrueOrderByAtivadoEmAscIdAsc();

    Optional<StorySelecaoAdministrativaEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select selecao
            from StorySelecaoAdministrativaEntity selecao
            where selecao.anuncioId = :anuncioId
              and selecao.ativa = true
            order by selecao.ativadoEm desc, selecao.id desc
            """)
    List<StorySelecaoAdministrativaEntity> bloquearAtivasDoAnuncio(UUID anuncioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select selecao
            from StorySelecaoAdministrativaEntity selecao
            where selecao.ativa = true
              and selecao.anuncioId in :anuncioIds
            order by selecao.id
            """)
    List<StorySelecaoAdministrativaEntity> bloquearAtivasDosAnuncios(Collection<UUID> anuncioIds);

    @Query(value = "select pg_advisory_xact_lock(864219)", nativeQuery = true)
    void bloquearOperacao();
}
