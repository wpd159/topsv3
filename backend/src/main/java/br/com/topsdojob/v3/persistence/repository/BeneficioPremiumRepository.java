package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficioPremiumRepository extends JpaRepository<BeneficioPremiumEntity, UUID> {
    List<BeneficioPremiumEntity> findByIdIn(Collection<UUID> ids);

    java.util.Optional<BeneficioPremiumEntity> findByCodigo(String codigo);

    List<BeneficioPremiumEntity> findAllByOrderByOrdemExibicaoAscCodigoAsc();
}
