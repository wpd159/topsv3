package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanoCreditoRepository extends JpaRepository<PlanoCreditoEntity, UUID> {
    List<PlanoCreditoEntity> findAllByOrderByOrdemExibicaoAscCodigoAsc();

    List<PlanoCreditoEntity> findByAtivoTrueOrderByOrdemExibicaoAscCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plano from PlanoCreditoEntity plano where plano.id = :id")
    Optional<PlanoCreditoEntity> findByIdForUpdate(@Param("id") UUID id);
}
