package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaHomeRepository extends JpaRepository<CategoriaHomeEntity, UUID> {
  List<CategoriaHomeEntity> findByAtivoTrueOrderByOrdemAscIdAsc();
}
