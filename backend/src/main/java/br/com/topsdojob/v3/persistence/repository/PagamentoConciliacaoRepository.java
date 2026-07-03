package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoConciliacaoRepository extends JpaRepository<PagamentoConciliacaoEntity, UUID> {
  List<PagamentoConciliacaoEntity> findByPagamentoIdIn(Collection<UUID> pagamentoIds);

  List<PagamentoConciliacaoEntity> findByMovimentoCreditoIdIn(Collection<UUID> movimentoCreditoIds);
}
