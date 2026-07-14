package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrupoAtivacaoBeneficioRepository extends JpaRepository<GrupoAtivacaoBeneficioEntity, UUID> {
    List<GrupoAtivacaoBeneficioEntity> findByIdIn(Collection<UUID> ids);

    List<GrupoAtivacaoBeneficioEntity> findByAnuncioId(UUID anuncioId);

    Optional<GrupoAtivacaoBeneficioEntity> findByIdempotencyKey(String idempotencyKey);
}
