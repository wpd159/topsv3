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

    @Query(
            value = """
                    select a.*
                    from anuncio a
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and (:categoria is null or a.categoria = :categoria)
                      and (:busca is null or lower(translate(coalesce(a.titulo, '') || ' ' || coalesce(a.descricao, ''),
                            'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
                            'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn')) like ('%' || :busca || '%'))
                    order by
                      case when exists (
                        select 1
                        from ativacao_beneficio ab
                        join beneficio_premium bp on bp.id = ab.beneficio_id
                        join grupo_ativacao_beneficio gb on gb.id = ab.grupo_ativacao_id
                        where ab.anuncio_id = a.id
                          and bp.codigo = 'ANUNCIO_TOPO'
                          and bp.ativo = true
                          and bp.afeta_ranking = true
                          and ab.status = 'ATIVA'
                          and ab.revogada_em is null
                          and ab.inicio_em <= :agora
                          and ab.fim_em > :agora
                          and gb.status = 'ATIVO'
                          and gb.validade_inicio_em <= :agora
                          and gb.validade_fim_em > :agora
                      ) then 0 else 1 end,
                      hashtextextended(a.id::text, :seed),
                      a.id
                    """,
            countQuery = """
                    select count(*)
                    from anuncio a
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and (:categoria is null or a.categoria = :categoria)
                      and (:busca is null or lower(translate(coalesce(a.titulo, '') || ' ' || coalesce(a.descricao, ''),
                            'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
                            'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn')) like ('%' || :busca || '%'))
                    """,
            nativeQuery = true)
    Page<AnuncioEntity> findPublicosOrdenados(
            @Param("categoria") String categoria,
            @Param("busca") String busca,
            @Param("agora") OffsetDateTime agora,
            @Param("seed") long seed,
            Pageable pageable);

    @Query(
            value = """
                    select a.*
                    from anuncio a
                    join anuncio_localizacao l on l.anuncio_id = a.id
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and l.estado_id = :estadoId
                      and (:cidadeId is null or l.cidade_id = :cidadeId)
                      and (:bairroId is null or l.bairro_id = :bairroId)
                    order by
                      case when exists (
                        select 1
                        from ativacao_beneficio ab
                        join beneficio_premium bp on bp.id = ab.beneficio_id
                        join grupo_ativacao_beneficio gb on gb.id = ab.grupo_ativacao_id
                        where ab.anuncio_id = a.id
                          and bp.codigo = 'ANUNCIO_TOPO'
                          and bp.ativo = true
                          and bp.afeta_ranking = true
                          and ab.status = 'ATIVA'
                          and ab.revogada_em is null
                          and ab.inicio_em <= :agora
                          and ab.fim_em > :agora
                          and gb.status = 'ATIVO'
                          and gb.validade_inicio_em <= :agora
                          and gb.validade_fim_em > :agora
                      ) then 0 else 1 end,
                      hashtextextended(a.id::text, :seed),
                      a.id
                    """,
            countQuery = """
                    select count(*)
                    from anuncio a
                    join anuncio_localizacao l on l.anuncio_id = a.id
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and l.estado_id = :estadoId
                      and (:cidadeId is null or l.cidade_id = :cidadeId)
                      and (:bairroId is null or l.bairro_id = :bairroId)
                    """,
            nativeQuery = true)
    Page<AnuncioEntity> findPublicosPorLocalidadeOrdenados(
            @Param("estadoId") UUID estadoId,
            @Param("cidadeId") UUID cidadeId,
            @Param("bairroId") UUID bairroId,
            @Param("agora") OffsetDateTime agora,
            @Param("seed") long seed,
            Pageable pageable);

    @Query("""
            select a.usuarioId as usuarioId, min(a.publicadoEm) as primeiraPublicacaoEm
            from AnuncioEntity a
            where a.usuarioId in :usuarioIds
              and a.publicadoEm is not null
            group by a.usuarioId
            """)
    List<PrimeiraPublicacaoAnuncianteProjection> findPrimeiraPublicacaoByUsuarioIdIn(
            @Param("usuarioIds") Collection<UUID> usuarioIds);

    @Query(value = """
            select distinct m.entidade_v3_id
            from importacao_mapeamento m
            where m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
              and m.tabela_origem = 'anuncios'
              and m.entidade_tipo = 'ANUNCIO'
              and m.status = 'MAPEADO'
              and m.entidade_v3_id in (:anuncioIds)
            """, nativeQuery = true)
    List<UUID> findIdsComMapeamentoLegado(@Param("anuncioIds") Collection<UUID> anuncioIds);

    long count(Specification<AnuncioEntity> spec);

    interface PrimeiraPublicacaoAnuncianteProjection {
        UUID getUsuarioId();

        OffsetDateTime getPrimeiraPublicacaoEm();
    }
}
