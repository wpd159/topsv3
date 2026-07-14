package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoCreditoRepository extends JpaRepository<PlanoCreditoEntity, UUID> {
    List<PlanoCreditoEntity> findAllByOrderByOrdemExibicaoAscCodigoAsc();

    List<PlanoCreditoEntity> findByAtivoTrueOrderByOrdemExibicaoAscCodigoAsc();
}
