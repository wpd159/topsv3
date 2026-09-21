package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoUsuarioRepository extends JpaRepository<DocumentoUsuarioEntity, UUID> {
    long countByStatus(StatusDocumentoUsuario status);

    long countByUsuarioIdAndStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
            UUID usuarioId,
            Collection<StatusDocumentoUsuario> statuses);

    boolean existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(UUID arquivoMidiaId);

    boolean existsByArquivoMidiaIdInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
            Collection<UUID> arquivoMidiaIds);

    List<DocumentoUsuarioEntity> findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(
            UUID usuarioId);

    List<DocumentoUsuarioEntity> findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(
            UUID envioId);

    @Query("""
            select d.envioId from DocumentoUsuarioEntity d
            where d.id = :documentoId and d.removidoEm is null and d.expurgadoEm is null
            """)
    Optional<UUID> findEnvioIdAtivoByDocumentoId(@Param("documentoId") UUID documentoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from DocumentoUsuarioEntity d
            where d.envioId = :envioId and d.removidoEm is null and d.expurgadoEm is null
            order by d.parte, d.id
            """)
    List<DocumentoUsuarioEntity> findAtivosDoEnvioForUpdate(@Param("envioId") UUID envioId);

    List<DocumentoUsuarioEntity> findByStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmAsc(
            Collection<StatusDocumentoUsuario> statuses);

    @Query("""
            select d.usuarioId as usuarioId, count(d) as totalDocumentos
            from DocumentoUsuarioEntity d
            where d.usuarioId in :usuarioIds
              and d.status in :statuses
              and d.removidoEm is null
              and d.expurgadoEm is null
            group by d.usuarioId
            """)
    List<ContagemPorUsuarioProjection> countByUsuarioIdInAndStatusIn(
            @Param("usuarioIds") Collection<UUID> usuarioIds,
            @Param("statuses") Collection<StatusDocumentoUsuario> statuses);

    interface ContagemPorUsuarioProjection {
        UUID getUsuarioId();

        long getTotalDocumentos();
    }
}
