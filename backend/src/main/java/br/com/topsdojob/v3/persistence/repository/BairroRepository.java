package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BairroRepository extends JpaRepository<BairroEntity, UUID> {
    Optional<BairroEntity> findByCidadeIdAndSlug(UUID cidadeId, String slug);

    List<BairroEntity> findBySlug(String slug);

    List<BairroEntity> findByCidadeId(UUID cidadeId);
}
