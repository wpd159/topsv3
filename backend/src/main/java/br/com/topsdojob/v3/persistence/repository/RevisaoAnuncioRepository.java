package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RevisaoAnuncioRepository
        extends JpaRepository<RevisaoAnuncioEntity, UUID>, JpaSpecificationExecutor<RevisaoAnuncioEntity> {
    long countByStatus(StatusRevisaoAnuncio status);

    long countByAnuncioId(UUID anuncioId);

    boolean existsByAnuncioIdAndStatusIn(UUID anuncioId, Collection<StatusRevisaoAnuncio> statuses);

    List<RevisaoAnuncioEntity> findByAnuncioId(UUID anuncioId);

    Optional<RevisaoAnuncioEntity> findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
            UUID anuncioId,
            StatusRevisaoAnuncio status);

    Page<RevisaoAnuncioEntity> findByAnuncioId(UUID anuncioId, Pageable pageable);

    long count(Specification<RevisaoAnuncioEntity> spec);
}
