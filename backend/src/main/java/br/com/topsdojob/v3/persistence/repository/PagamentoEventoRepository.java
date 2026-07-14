package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoEventoRepository extends JpaRepository<PagamentoEventoEntity, UUID> {
  List<PagamentoEventoEntity> findByPagamentoId(UUID pagamentoId);

  List<PagamentoEventoEntity> findByPagamentoIdIn(Collection<UUID> pagamentoIds);

  Optional<PagamentoEventoEntity> findByProvedorAndProvedorEventoId(
      br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento provedor,
      String provedorEventoId);
}
