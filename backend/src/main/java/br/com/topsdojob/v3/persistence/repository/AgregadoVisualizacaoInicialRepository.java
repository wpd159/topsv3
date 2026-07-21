package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoInicialEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgregadoVisualizacaoInicialRepository
    extends JpaRepository<AgregadoVisualizacaoInicialEntity, UUID> {
  Optional<AgregadoVisualizacaoInicialEntity> findByAnuncioId(UUID anuncioId);

  List<AgregadoVisualizacaoInicialEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);
}
