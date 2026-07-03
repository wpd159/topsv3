package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoDiariaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgregadoVisualizacaoDiariaRepository extends JpaRepository<AgregadoVisualizacaoDiariaEntity, UUID> {
    List<AgregadoVisualizacaoDiariaEntity> findByAnuncioId(UUID anuncioId);

    List<AgregadoVisualizacaoDiariaEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);
}
