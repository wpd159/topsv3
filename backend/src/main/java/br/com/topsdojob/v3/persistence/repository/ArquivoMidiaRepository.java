package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoMidiaRepository extends JpaRepository<ArquivoMidiaEntity, UUID> {
    List<ArquivoMidiaEntity> findByIdIn(Collection<UUID> ids);
}
