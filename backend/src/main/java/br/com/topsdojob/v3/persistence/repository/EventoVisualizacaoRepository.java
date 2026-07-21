package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoVisualizacaoRepository extends JpaRepository<EventoVisualizacaoEntity, UUID> {
    long countByAnuncioId(UUID anuncioId);

    long countByAnuncioIdAndCriadoEmAfter(UUID anuncioId, OffsetDateTime corte);

    List<EventoVisualizacaoEntity> findByAnuncioId(UUID anuncioId);
}
