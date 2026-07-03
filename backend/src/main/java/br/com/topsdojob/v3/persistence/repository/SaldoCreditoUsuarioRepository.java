package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.credito.SaldoCreditoUsuarioEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaldoCreditoUsuarioRepository extends JpaRepository<SaldoCreditoUsuarioEntity, UUID> {
}
