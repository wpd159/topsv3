package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrupoAtivacaoBeneficioRepository extends JpaRepository<GrupoAtivacaoBeneficioEntity, UUID> {
    List<GrupoAtivacaoBeneficioEntity> findByIdIn(Collection<UUID> ids);

    List<GrupoAtivacaoBeneficioEntity> findByAnuncioId(UUID anuncioId);

    Optional<GrupoAtivacaoBeneficioEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grupo from GrupoAtivacaoBeneficioEntity grupo where grupo.id = :id")
    Optional<GrupoAtivacaoBeneficioEntity> findByIdForUpdate(@Param("id") UUID id);
}
