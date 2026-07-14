package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoWebhookRepository extends JpaRepository<PagamentoWebhookEntity, UUID> {
  List<PagamentoWebhookEntity> findByProvedorAndTxidIn(ProvedorPagamento provedor, Collection<String> txids);

  Optional<PagamentoWebhookEntity> findByProvedorAndEventoId(ProvedorPagamento provedor, String eventoId);
}
