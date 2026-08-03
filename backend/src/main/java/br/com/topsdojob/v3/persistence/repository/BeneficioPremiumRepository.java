package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficioPremiumRepository extends JpaRepository<BeneficioPremiumEntity, UUID> {
    List<BeneficioPremiumEntity> findByIdIn(Collection<UUID> ids);

    Optional<BeneficioPremiumEntity> findByCodigo(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select beneficio from BeneficioPremiumEntity beneficio where beneficio.id = :id")
    Optional<BeneficioPremiumEntity> findByIdForUpdate(@Param("id") UUID id);

    List<BeneficioPremiumEntity> findAllByOrderByOrdemExibicaoAscCodigoAsc();
}
