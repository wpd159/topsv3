package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoVisualizacaoRepository extends JpaRepository<EventoVisualizacaoEntity, UUID> {
}
