package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentoCreditoRepository extends JpaRepository<MovimentoCreditoEntity, UUID> {
}
