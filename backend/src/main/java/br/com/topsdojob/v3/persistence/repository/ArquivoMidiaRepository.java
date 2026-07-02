package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ArquivoMidiaRepository
        extends JpaRepository<ArquivoMidiaEntity, UUID>, JpaSpecificationExecutor<ArquivoMidiaEntity> {
    long countByStatusArquivo(StatusArquivoMidia statusArquivo);

    List<ArquivoMidiaEntity> findByIdIn(Collection<UUID> ids);
}
