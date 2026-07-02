package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CliqueWhatsappRepository extends JpaRepository<CliqueWhatsappEntity, UUID> {
    long countByPermitidoTrue();

    long countByPermitidoFalse();
}
