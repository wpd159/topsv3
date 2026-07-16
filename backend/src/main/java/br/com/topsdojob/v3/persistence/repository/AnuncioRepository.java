package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnuncioRepository extends JpaRepository<AnuncioEntity, UUID>, JpaSpecificationExecutor<AnuncioEntity> {
    boolean existsBySlug(String slug);

    long countByStatusAndRemovidoEmIsNull(StatusAnuncio status);

    long countByStatusInAndRemovidoEmIsNull(Collection<StatusAnuncio> statuses);

    long countByStatusModeracaoAndRemovidoEmIsNull(StatusModeracaoAnuncio statusModeracao);

    long countByWhatsappNormalizadoIsNotNullAndRemovidoEmIsNull();

    List<AnuncioEntity> findByUsuarioIdAndRemovidoEmIsNull(UUID usuarioId);

    List<AnuncioEntity> findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(UUID usuarioId);

    Optional<AnuncioEntity> findBySlugAndRemovidoEmIsNull(String slug);

    Optional<AnuncioEntity> findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
            String slug,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    Optional<AnuncioEntity> findBySlugAndStatusAndStatusModeracaoAndPublicadoEmIsNotNullAndRemovidoEmIsNull(
            String slug,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    Page<AnuncioEntity> findByIdInAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
            Collection<UUID> ids,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            Pageable pageable);

    List<AnuncioEntity> findByStatusAndStatusModeracaoAndRemovidoEmIsNull(
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    List<AnuncioEntity> findByCategoriaAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
            String categoria,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    List<AnuncioEntity> findByIdInAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
            Collection<UUID> ids,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    List<AnuncioEntity> findByIdInAndStatusAndStatusModeracaoAndPublicadoEmIsNotNullAndRemovidoEmIsNull(
            Collection<UUID> ids,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    @Query("""
            select a.usuarioId as usuarioId, min(a.publicadoEm) as primeiraPublicacaoEm
            from AnuncioEntity a
            where a.usuarioId in :usuarioIds
              and a.publicadoEm is not null
            group by a.usuarioId
            """)
    List<PrimeiraPublicacaoAnuncianteProjection> findPrimeiraPublicacaoByUsuarioIdIn(
            @Param("usuarioIds") Collection<UUID> usuarioIds);

    long count(Specification<AnuncioEntity> spec);

    interface PrimeiraPublicacaoAnuncianteProjection {
        UUID getUsuarioId();

        OffsetDateTime getPrimeiraPublicacaoEm();
    }
}
