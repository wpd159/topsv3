package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArquivoMidiaRepository
        extends JpaRepository<ArquivoMidiaEntity, UUID>, JpaSpecificationExecutor<ArquivoMidiaEntity> {
    long countByStatusArquivo(StatusArquivoMidia statusArquivo);

    List<ArquivoMidiaEntity> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select arquivo
            from ArquivoMidiaEntity arquivo
            where arquivo.id in :ids
            order by arquivo.id
            """)
    List<ArquivoMidiaEntity> findByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select arquivo from ArquivoMidiaEntity arquivo where arquivo.id = :id")
    java.util.Optional<ArquivoMidiaEntity> findByIdForUpdate(@Param("id") UUID id);
}
