package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRepository extends JpaRepository<PagamentoEntity, UUID> {
  List<PagamentoEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

  List<PagamentoEntity> findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
      UUID usuarioId,
      ProvedorPagamento provedor,
      MetodoPagamento metodo,
      Pageable pageable);

  List<PagamentoEntity> findByStatusInterno(StatusInternoPagamento statusInterno);

  List<PagamentoEntity> findByIdIn(Collection<UUID> ids);

  Optional<PagamentoEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<PagamentoEntity> findByIdAndUsuarioId(UUID id, UUID usuarioId);

  Optional<PagamentoEntity> findByTxid(String txid);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select pagamento from PagamentoEntity pagamento where pagamento.txid = :txid")
  Optional<PagamentoEntity> findByTxidForUpdate(@Param("txid") String txid);
}
