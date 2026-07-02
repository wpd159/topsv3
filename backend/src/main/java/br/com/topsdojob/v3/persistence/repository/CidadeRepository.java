package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CidadeRepository extends JpaRepository<CidadeEntity, UUID> {
    Optional<CidadeEntity> findByEstadoIdAndSlug(UUID estadoId, String slug);

    List<CidadeEntity> findBySlug(String slug);
}
