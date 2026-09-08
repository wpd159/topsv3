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

    @Query("""
            select new br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura(
                a.id, a.sha256, a.statusArquivo, a.storageProvider, a.bucket,
                a.chaveObjeto, a.mimeType, a.largura, a.altura)
            from ArquivoMidiaEntity a where array_contains(:ids, a.id)
            """)
    List<br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura> findLeiturasPublicasArray(
            @Param("ids") UUID[] ids);

    default List<br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura> findLeiturasPublicas(
            Collection<UUID> ids) {
        return findLeiturasPublicasArray(ids.toArray(UUID[]::new));
    }

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select arquivo
            from ArquivoMidiaEntity arquivo
            where arquivo.storageProvider = :storageProvider
              and arquivo.bucket = :bucket
              and arquivo.chaveObjeto = :chaveObjeto
            """)
    java.util.Optional<ArquivoMidiaEntity> findByStorageIdentityForUpdate(
            @Param("storageProvider") String storageProvider,
            @Param("bucket") String bucket,
            @Param("chaveObjeto") String chaveObjeto);
}
