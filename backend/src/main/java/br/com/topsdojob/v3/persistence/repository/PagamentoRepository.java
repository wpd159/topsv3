package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.time.OffsetDateTime;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select pagamento from PagamentoEntity pagamento where pagamento.id = :id and pagamento.usuarioId = :usuarioId")
  Optional<PagamentoEntity> findByIdAndUsuarioIdForUpdate(
      @Param("id") UUID id,
      @Param("usuarioId") UUID usuarioId);

  Optional<PagamentoEntity> findByTxid(String txid);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select pagamento from PagamentoEntity pagamento where pagamento.txid = :txid")
  Optional<PagamentoEntity> findByTxidForUpdate(@Param("txid") String txid);

  @Query("""
      select pagamento.txid
      from PagamentoEntity pagamento
      where pagamento.provedor = :provedor
        and pagamento.metodo = :metodo
        and pagamento.ambiente = :ambiente
        and pagamento.txid is not null
        and pagamento.atualizadoEm <= :elegivelAntesDe
        and (
          pagamento.statusInterno in :statusPendentes
          or (
            pagamento.statusInterno = :statusErro
            and pagamento.statusProvedor = :statusErroTransitorio
          )
        )
      order by pagamento.atualizadoEm asc, pagamento.id asc
      """)
  List<String> findTxidsConciliaveisEfi(
      @Param("provedor") ProvedorPagamento provedor,
      @Param("metodo") MetodoPagamento metodo,
      @Param("ambiente") AmbientePagamento ambiente,
      @Param("statusPendentes") Collection<StatusInternoPagamento> statusPendentes,
      @Param("statusErro") StatusInternoPagamento statusErro,
      @Param("statusErroTransitorio") String statusErroTransitorio,
      @Param("elegivelAntesDe") OffsetDateTime elegivelAntesDe,
      Pageable pageable);
}
