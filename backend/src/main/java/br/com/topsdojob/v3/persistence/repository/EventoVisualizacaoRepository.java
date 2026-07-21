package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoVisualizacaoRepository extends JpaRepository<EventoVisualizacaoEntity, UUID> {
    long countByAnuncioId(UUID anuncioId);

    long countByAnuncioIdAndCriadoEmAfter(UUID anuncioId, OffsetDateTime corte);

    List<EventoVisualizacaoEntity> findByAnuncioId(UUID anuncioId);

    @Query(value = """
            select e.anuncio_id as "anuncioId", count(*) as "totalEventos"
            from evento_visualizacao e
            left join agregado_visualizacao_inicial i on i.anuncio_id = e.anuncio_id
            where e.anuncio_id in (:anuncioIds)
              and (i.id is null or e.criado_em > i.snapshot_corte_em)
            group by e.anuncio_id
            """, nativeQuery = true)
    List<ContagemCanonicaProjection> countCanonicosByAnuncioIdIn(
            @Param("anuncioIds") List<UUID> anuncioIds);

    interface ContagemCanonicaProjection {
        UUID getAnuncioId();

        long getTotalEventos();
    }
}
