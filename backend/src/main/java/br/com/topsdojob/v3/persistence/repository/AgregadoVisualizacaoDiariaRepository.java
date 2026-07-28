package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoDiariaEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgregadoVisualizacaoDiariaRepository extends JpaRepository<AgregadoVisualizacaoDiariaEntity, UUID> {
    List<AgregadoVisualizacaoDiariaEntity> findByAnuncioId(UUID anuncioId);

    List<AgregadoVisualizacaoDiariaEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    @Query("""
            select coalesce(sum(a.totalVisualizacoes), 0)
            from AgregadoVisualizacaoDiariaEntity a
            where a.dataReferencia = :dataReferencia
            """)
    long somarPorData(@Param("dataReferencia") LocalDate dataReferencia);
}
