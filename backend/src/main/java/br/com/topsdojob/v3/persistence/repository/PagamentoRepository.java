package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRepository extends JpaRepository<PagamentoEntity, UUID> {
  List<PagamentoEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

  List<PagamentoEntity> findByStatusInterno(StatusInternoPagamento statusInterno);

  List<PagamentoEntity> findByIdIn(Collection<UUID> ids);
}
