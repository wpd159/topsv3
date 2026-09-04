package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;

public interface AnuncioMidiaRepository
        extends JpaRepository<AnuncioMidiaEntity, UUID>, JpaSpecificationExecutor<AnuncioMidiaEntity> {
    long countByStatus(StatusAnuncioMidia status);

    long countByVisibilidadeMidia(VisibilidadeMidia visibilidadeMidia);

    long countByStatusAndTipoNot(StatusAnuncioMidia status, TipoAnuncioMidia tipo);

    long countByVisibilidadeMidiaAndTipoNot(VisibilidadeMidia visibilidadeMidia, TipoAnuncioMidia tipo);

    long countByAnuncioId(UUID anuncioId);

    long countByAnuncioIdAndTipoNot(UUID anuncioId, TipoAnuncioMidia tipo);

    long countByAnuncioIdAndTipoAndStatus(
            UUID anuncioId,
            TipoAnuncioMidia tipo,
            StatusAnuncioMidia status);

    @Query("""
            select m.anuncioId as anuncioId, count(m) as totalMidias
            from AnuncioMidiaEntity m
            where m.anuncioId in :anuncioIds
              and m.tipo <> :tipoExcluido
              and m.status <> :statusExcluido
            group by m.anuncioId
            """)
    List<ContagemPorAnuncioProjection> countByAnuncioIdInAndTipoNotAndStatusNot(
            @Param("anuncioIds") Collection<UUID> anuncioIds,
            @Param("tipoExcluido") TipoAnuncioMidia tipoExcluido,
            @Param("statusExcluido") StatusAnuncioMidia statusExcluido);

    List<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId);

    List<AnuncioMidiaEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    @Query("""
            select midia.anuncioId as anuncioId, midia.arquivoMidiaId as arquivoMidiaId
            from AnuncioMidiaEntity midia
            where midia.id = :id
            """)
    Optional<ReferenciaMidiaProjection> findReferenciaById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select midia
            from AnuncioMidiaEntity midia
            where midia.anuncioId = :anuncioId
            order by midia.id
            """)
    List<AnuncioMidiaEntity> findByAnuncioIdForUpdate(@Param("anuncioId") UUID anuncioId);

    List<AnuncioMidiaEntity> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select midia
            from AnuncioMidiaEntity midia
            where midia.id in :ids
            order by midia.id
            """)
    List<AnuncioMidiaEntity> findByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    List<AnuncioMidiaEntity> findByArquivoMidiaId(UUID arquivoMidiaId);

    List<AnuncioMidiaEntity> findByArquivoMidiaIdIn(Collection<UUID> arquivoMidiaIds);

    @Query(value = """
            select am.id
            from anuncio_midia am
            join arquivo_midia arquivo on arquivo.id = am.arquivo_midia_id
            where am.anuncio_id = :anuncioId
              and am.tipo = 'FOTO'
              and am.finalidade in ('CAPA', 'GALERIA')
              and am.status = 'PUBLICAVEL'
              and am.visibilidade_midia is not null
              and arquivo.status_arquivo = 'VALIDADO'
              and not exists (
                select 1
                from documento_usuario documento
                where documento.arquivo_midia_id = am.arquivo_midia_id
              )
            order by am.id
            """, nativeQuery = true)
    List<UUID> findFotosAprovadasElegiveisIds(@Param("anuncioId") UUID anuncioId);

    @Query(value = """
            select am.id
            from anuncio_midia am
            where am.anuncio_id = :anuncioId
              and am.tipo = 'FOTO'
              and am.finalidade in ('CAPA', 'GALERIA')
              and am.status in ('PENDENTE', 'AJUSTE_SOLICITADO')
            order by am.id
            """, nativeQuery = true)
    List<UUID> findFotosAguardandoDecisaoIds(@Param("anuncioId") UUID anuncioId);

    @Query(value = """
            select exists (
              select 1
              from documento_usuario documento
              where documento.arquivo_midia_id = :arquivoMidiaId
            )
            """, nativeQuery = true)
    boolean existsDocumentoUsuarioHistoricoPorArquivoId(
            @Param("arquivoMidiaId") UUID arquivoMidiaId);

    @Query("""
            select midia
            from AnuncioMidiaEntity midia
            where midia.tipo = :tipo
              and midia.status = :status
              and midia.visibilidadeMidia = :visibilidade
            order by midia.id
            """)
    List<AnuncioMidiaEntity> findFotosRestritasPublicaveis(
            @Param("tipo") TipoAnuncioMidia tipo,
            @Param("status") StatusAnuncioMidia status,
            @Param("visibilidade") VisibilidadeMidia visibilidade);

    Page<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId, Pageable pageable);

    Page<AnuncioMidiaEntity> findByAnuncioIdAndTipoNot(
            UUID anuncioId,
            TipoAnuncioMidia tipo,
            Pageable pageable);

    Page<AnuncioMidiaEntity> findByAnuncioIdAndTipoNotAndStatusNot(
            UUID anuncioId,
            TipoAnuncioMidia tipo,
            StatusAnuncioMidia status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select midia from AnuncioMidiaEntity midia where midia.id = :id")
    java.util.Optional<AnuncioMidiaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            select exists (
              select 1
              from stg_midia staging
              where staging.entidade_v3_id = :midiaId
                and staging.status = 'PROCESSADO'
                and staging.pendencia_codigo is null
                and staging.processado_em is not null
                and staging.payload_normalizado_json ->> 'tipo' = 'FOTO'
                and staging.payload_normalizado_json ->> 'principal' = 'true'
                and staging.payload_normalizado_json ->> 'privada' = 'true'
                and lower(staging.payload_normalizado_json ->> 'sha256') = lower(:sha256)
            )
            """, nativeQuery = true)
    boolean existsFotoImportadaProcessadaComChecksum(
            @Param("midiaId") UUID midiaId,
            @Param("sha256") String sha256);

    long count(Specification<AnuncioMidiaEntity> spec);

    interface ContagemPorAnuncioProjection {
        UUID getAnuncioId();

        long getTotalMidias();
    }

    interface ReferenciaMidiaProjection {
        UUID getAnuncioId();

        UUID getArquivoMidiaId();
    }
}
