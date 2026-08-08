package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryAnuncioRepository extends JpaRepository<StoryAnuncioEntity, UUID> {
    long countByStatus(StatusStoryAnuncio status);

    @Query("""
        select count(story)
        from StoryAnuncioEntity story
        where story.status = :status
          and story.inicioEm <= :agora
          and story.fimEm > :agora
        """)
    long countAtivos(
        @Param("status") StatusStoryAnuncio status,
        @Param("agora") OffsetDateTime agora);

    List<StoryAnuncioEntity> findByAnuncioMidiaIdIn(Collection<UUID> anuncioMidiaIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select story from StoryAnuncioEntity story
        where story.anuncioMidiaId in :anuncioMidiaIds
        order by story.id
        """)
    List<StoryAnuncioEntity> findByAnuncioMidiaIdInForUpdate(
        @Param("anuncioMidiaIds") Collection<UUID> anuncioMidiaIds);

    List<StoryAnuncioEntity> findByStatusOrderByOrdemAscCriadoEmAscIdAsc(StatusStoryAnuncio status);

    Optional<StoryAnuncioEntity> findByIdAndStatus(UUID id, StatusStoryAnuncio status);

    Optional<StoryAnuncioEntity> findByCriadoPorAndModoConteudoAndAnuncioIdAndIdempotencyKey(
        UUID criadoPor,
        ModoConteudoStory modoConteudo,
        UUID anuncioId,
        String idempotencyKey);

    Optional<StoryAnuncioEntity>
        findByCriadoPorAndModoConteudoAndAnuncioIdIsNullAndIdempotencyKey(
        UUID criadoPor,
        ModoConteudoStory modoConteudo,
        String idempotencyKey);

    Page<StoryAnuncioEntity> findByCriadoPorOrderByCriadoEmDescIdDesc(
        UUID criadoPor,
        Pageable pageable);

    Page<StoryAnuncioEntity> findAllByOrderByCriadoEmDescIdDesc(Pageable pageable);

    @Query(
        value = """
            select distinct story.*
            from story_anuncio story
            left join anuncio_midia midia on midia.id = story.anuncio_midia_id
            left join anuncio anuncio on anuncio.id = coalesce(story.anuncio_id, midia.anuncio_id)
            left join usuario usuario on usuario.id = story.criado_por
            where (:usuarioId is null or story.criado_por = :usuarioId)
              and (
                :busca is null
                or lower(coalesce(anuncio.titulo, '')) like concat('%', :busca, '%')
                or lower(coalesce(story.modo_conteudo, 'MIDIA_UPLOAD')) like concat('%', :busca, '%')
                or lower(cast(story.id as text)) like concat('%', :busca, '%')
                or lower(coalesce(usuario.nome, '')) like concat('%', :busca, '%')
              )
            order by story.criado_em desc, story.id desc
            """,
        countQuery = """
            select count(distinct story.id)
            from story_anuncio story
            left join anuncio_midia midia on midia.id = story.anuncio_midia_id
            left join anuncio anuncio on anuncio.id = coalesce(story.anuncio_id, midia.anuncio_id)
            left join usuario usuario on usuario.id = story.criado_por
            where (:usuarioId is null or story.criado_por = :usuarioId)
              and (
                :busca is null
                or lower(coalesce(anuncio.titulo, '')) like concat('%', :busca, '%')
                or lower(coalesce(story.modo_conteudo, 'MIDIA_UPLOAD')) like concat('%', :busca, '%')
                or lower(cast(story.id as text)) like concat('%', :busca, '%')
                or lower(coalesce(usuario.nome, '')) like concat('%', :busca, '%')
              )
            """,
        nativeQuery = true)
    Page<StoryAnuncioEntity> findGestaoAdministrativa(
        @Param("usuarioId") UUID usuarioId,
        @Param("busca") String busca,
        Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select story from StoryAnuncioEntity story where story.id = :id")
    Optional<StoryAnuncioEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByArquivoMidiaId(UUID arquivoMidiaId);

    long countByAnuncioMidiaId(UUID anuncioMidiaId);

    boolean existsByAtivacaoBeneficioIdAndDireitoPreservadoFalse(UUID ativacaoBeneficioId);

    boolean existsByAtivacaoBeneficioIdAndDireitoPreservadoTrueAndEncerradoEmIsNotNull(
        UUID ativacaoBeneficioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct story
        from StoryAnuncioEntity story
        where story.anuncioId = :anuncioId
           or story.anuncioMidiaId in (
             select midia.id
             from AnuncioMidiaEntity midia
             where midia.anuncioId = :anuncioId
           )
        order by story.id
        """)
    List<StoryAnuncioEntity> findByAnuncioIdForUpdate(@Param("anuncioId") UUID anuncioId);

    @Query("""
        select distinct story
        from StoryAnuncioEntity story
        where story.anuncioId in :anuncioIds
           or story.anuncioMidiaId in (
             select midia.id
             from AnuncioMidiaEntity midia
             where midia.anuncioId in :anuncioIds
           )
        """)
    List<StoryAnuncioEntity> findByAnuncioIds(@Param("anuncioIds") Collection<UUID> anuncioIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select distinct story
        from StoryAnuncioEntity story
        where story.anuncioId in :anuncioIds
           or story.anuncioMidiaId in (
             select midia.id
             from AnuncioMidiaEntity midia
             where midia.anuncioId in :anuncioIds
           )
        order by story.id
        """)
    List<StoryAnuncioEntity> findByAnuncioIdsForUpdate(
        @Param("anuncioIds") Collection<UUID> anuncioIds);
}
