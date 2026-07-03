package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoCliqueWhatsappDiarioEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgregadoCliqueWhatsappDiarioRepository extends JpaRepository<AgregadoCliqueWhatsappDiarioEntity, UUID> {
    List<AgregadoCliqueWhatsappDiarioEntity> findByAnuncioId(UUID anuncioId);

    List<AgregadoCliqueWhatsappDiarioEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);
}
