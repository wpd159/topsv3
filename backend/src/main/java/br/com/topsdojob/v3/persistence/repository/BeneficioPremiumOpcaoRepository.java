package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficioPremiumOpcaoRepository extends JpaRepository<BeneficioPremiumOpcaoEntity, UUID> {

    List<BeneficioPremiumOpcaoEntity> findByBeneficioIdInOrderByOrdemExibicaoAscDuracaoDiasAsc(
            Collection<UUID> beneficioIds);

    List<BeneficioPremiumOpcaoEntity> findByBeneficioIdOrderByOrdemExibicaoAscDuracaoDiasAsc(UUID beneficioId);

    Optional<BeneficioPremiumOpcaoEntity> findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
            UUID beneficioId,
            Integer duracaoDias);
}
