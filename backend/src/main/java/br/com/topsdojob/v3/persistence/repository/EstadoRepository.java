package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoRepository extends JpaRepository<EstadoEntity, UUID> {
    Optional<EstadoEntity> findByUfIgnoreCase(String uf);
}
