package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.persistence.LockModeType;
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
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnuncioRepository extends JpaRepository<AnuncioEntity, UUID>, JpaSpecificationExecutor<AnuncioEntity> {
    boolean existsBySlug(String slug);

    long countByStatusAndRemovidoEmIsNull(StatusAnuncio status);

    long countByStatusInAndRemovidoEmIsNull(Collection<StatusAnuncio> statuses);

    long countByStatusModeracaoAndRemovidoEmIsNull(StatusModeracaoAnuncio statusModeracao);

    @Query(value = """
            select count(*)
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.removido_em is null
              and a.status = 'PENDENTE_REVISAO'
              and a.status_moderacao = 'PENDENTE'
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    long countPendentesModeracaoComProprietarioAtivo();

    long countByWhatsappNormalizadoIsNotNullAndRemovidoEmIsNull();

    List<AnuncioEntity> findByUsuarioIdAndRemovidoEmIsNull(UUID usuarioId);

    List<AnuncioEntity> findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(UUID usuarioId);

    List<AnuncioEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnuncioEntity anuncio
            set anuncio.whatsappNormalizado = :telefone,
                anuncio.atualizadoEm = :agora
            where anuncio.usuarioId = :usuarioId
              and anuncio.removidoEm is null
            """)
    int sincronizarTelefoneDoProprietario(
            @Param("usuarioId") UUID usuarioId,
            @Param("telefone") String telefone,
            @Param("agora") OffsetDateTime agora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select anuncio from AnuncioEntity anuncio
            where anuncio.usuarioId = :usuarioId
            order by anuncio.id
            """)
    List<AnuncioEntity> findByUsuarioIdForLegalBlock(@Param("usuarioId") UUID usuarioId);

    Optional<AnuncioEntity> findBySlugAndRemovidoEmIsNull(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select anuncio from AnuncioEntity anuncio where anuncio.slug = :slug")
    Optional<AnuncioEntity> findBySlugForLifecycle(@Param("slug") String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select anuncio from AnuncioEntity anuncio where anuncio.id = :id")
    Optional<AnuncioEntity> findByIdForModeration(@Param("id") UUID id);

    @Query(
            value = """
                    select a.*
                    from anuncio a
                    left join usuario u on u.id = a.usuario_id
                    left join agregado_visualizacao_inicial vi on vi.anuncio_id = a.id
                    left join (
                      select e.anuncio_id, count(*) as total_eventos
                      from evento_visualizacao e
                      left join agregado_visualizacao_inicial corte on corte.anuncio_id = e.anuncio_id
                      where corte.id is null or e.criado_em > corte.snapshot_corte_em
                      group by e.anuncio_id
                    ) eventos on eventos.anuncio_id = a.id
                    left join (
                      select c.anuncio_id, count(*) as total_cliques
                      from clique_whatsapp c
                      where c.permitido = true
                      group by c.anuncio_id
                    ) cliques on cliques.anuncio_id = a.id
                    left join (
                      select distinct m.entidade_v3_id as anuncio_id
                      from importacao_mapeamento m
                      where m.sistema_origem = 'TOPSDOJOB_PRODUCAO'
                        and m.tabela_origem = 'anuncios'
                        and m.entidade_tipo = 'ANUNCIO'
                        and m.status = 'MAPEADO'
                    ) legado on legado.anuncio_id = a.id
                    where a.removido_em is null
                      and (
                        :situacao = 'TODOS'
                        or (:situacao = 'PENDENTES_MODERACAO'
                            and a.status = 'PENDENTE_REVISAO'
                            and a.status_moderacao = 'PENDENTE'
                            and u.status = 'ATIVO'
                            and u.tipo_conta = 'ANUNCIANTE'
                            and u.desativado_em is null
                            and u.excluido_em is null)
                        or (:situacao = 'APROVADOS'
                            and a.status_moderacao = 'APROVADO'
                            and a.status <> 'BLOQUEADO')
                        or (:situacao = 'PAUSADOS' and a.status = 'PAUSADO')
                        or (:situacao = 'REJEITADOS'
                            and (a.status = 'REJEITADO' or a.status_moderacao = 'REJEITADO'))
                        or (:situacao = 'BLOQUEADOS' and a.status = 'BLOQUEADO')
                      )
                      and (:filtrarLocalizacao = false or a.id in (:anuncioIdsLocalizacao))
                      and (
                        cast(:termo as text) is null
                        or lower(a.slug) like ('%' || lower(:termo) || '%')
                        or lower(a.titulo) like ('%' || lower(:termo) || '%')
                        or cast(a.id as text) like ('%' || lower(:termo) || '%')
                      )
                    order by
                      case
                        when :ordenacao in ('MAIS_VISUALIZACOES', 'MENOS_VISUALIZACOES')
                          and legado.anuncio_id is not null
                          and vi.id is null
                        then 1 else 0
                      end asc,
                      case when :ordenacao = 'MAIS_RECENTES' then a.criado_em end desc nulls last,
                      case when :ordenacao = 'MAIS_ANTIGOS' then a.criado_em end asc nulls last,
                      case when :ordenacao = 'MAIS_VISUALIZACOES'
                        then coalesce(vi.total_visualizacoes, 0) + coalesce(eventos.total_eventos, 0)
                      end desc nulls last,
                      case when :ordenacao = 'MENOS_VISUALIZACOES'
                        then coalesce(vi.total_visualizacoes, 0) + coalesce(eventos.total_eventos, 0)
                      end asc nulls last,
                      case when :ordenacao = 'MAIS_CLIQUES_WHATSAPP'
                        then coalesce(cliques.total_cliques, 0)
                      end desc nulls last,
                      case when :ordenacao = 'MENOS_CLIQUES_WHATSAPP'
                        then coalesce(cliques.total_cliques, 0)
                      end asc nulls last,
                      a.criado_em desc,
                      a.id asc
                    """,
            countQuery = """
                    select count(*)
                    from anuncio a
                    left join usuario u on u.id = a.usuario_id
                    where a.removido_em is null
                      and (
                        :situacao = 'TODOS'
                        or (:situacao = 'PENDENTES_MODERACAO'
                            and a.status = 'PENDENTE_REVISAO'
                            and a.status_moderacao = 'PENDENTE'
                            and u.status = 'ATIVO'
                            and u.tipo_conta = 'ANUNCIANTE'
                            and u.desativado_em is null
                            and u.excluido_em is null)
                        or (:situacao = 'APROVADOS'
                            and a.status_moderacao = 'APROVADO'
                            and a.status <> 'BLOQUEADO')
                        or (:situacao = 'PAUSADOS' and a.status = 'PAUSADO')
                        or (:situacao = 'REJEITADOS'
                            and (a.status = 'REJEITADO' or a.status_moderacao = 'REJEITADO'))
                        or (:situacao = 'BLOQUEADOS' and a.status = 'BLOQUEADO')
                      )
                      and (:filtrarLocalizacao = false or a.id in (:anuncioIdsLocalizacao))
                      and (
                        cast(:termo as text) is null
                        or lower(a.slug) like ('%' || lower(:termo) || '%')
                        or lower(a.titulo) like ('%' || lower(:termo) || '%')
                        or cast(a.id as text) like ('%' || lower(:termo) || '%')
                      )
                    """,
            nativeQuery = true)
    Page<AnuncioEntity> findFilaAdministrativa(
            @Param("situacao") String situacao,
            @Param("filtrarLocalizacao") boolean filtrarLocalizacao,
            @Param("anuncioIdsLocalizacao") Collection<UUID> anuncioIdsLocalizacao,
            @Param("termo") String termo,
            @Param("ordenacao") String ordenacao,
            Pageable pageable);

    @Query(value = """
            select a.*
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.id = :id
              and a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.removido_em is null
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    Optional<AnuncioEntity> findPublicoComProprietarioAtivoPorId(@Param("id") UUID id);

    @Query(value = """
            select a.*
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.slug = :slug
              and a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.removido_em is null
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    Optional<AnuncioEntity> findPublicoComProprietarioAtivoPorSlug(@Param("slug") String slug);

    @Query(value = """
            select a.*
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.slug = :slug
              and a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.publicado_em is not null
              and a.removido_em is null
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    Optional<AnuncioEntity> findPublicoPublicadoComProprietarioAtivoPorSlug(@Param("slug") String slug);

    @Query(value = """
            select a.*
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.removido_em is null
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    List<AnuncioEntity> findPublicosComProprietarioAtivo();

    @Query(value = """
            select a.*
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.id in (:ids)
              and a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.removido_em is null
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    List<AnuncioEntity> findPublicosComProprietarioAtivoPorIds(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            select a.*
            from anuncio a
            join usuario u on u.id = a.usuario_id
            where a.id in (:ids)
              and a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.publicado_em is not null
              and a.removido_em is null
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    List<AnuncioEntity> findPublicosPublicadosComProprietarioAtivoPorIds(@Param("ids") Collection<UUID> ids);

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

    @Query(value = """
            select u.id as "usuarioId", u.nome as "displayUsername"
            from usuario u
            where lower(btrim(u.nome)) = :username
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
            """, nativeQuery = true)
    Optional<UsuarioPublicoProjection> findUsuarioPublicoPorUsername(
            @Param("username") String username);

    @Query(
            value = """
                    select
                      a.id,
                      a.usuario_id,
                      a.slug,
                      a.titulo,
                      a.descricao,
                      a.status,
                      a.status_moderacao,
                      a.categoria,
                      a.atendimento_exclusivamente_virtual,
                      a.preco,
                      a.whatsapp_normalizado,
                      a.link_conteudo,
                      a.publicado_em,
                      a.ultima_publicacao_em,
                      a.criado_em,
                      a.atualizado_em,
                      a.removido_em,
                      a.origem_importacao_id,
                      a.versao
                    from anuncio a
                    join usuario u on u.id = a.usuario_id
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and u.status = 'ATIVO'
                      and u.tipo_conta = 'ANUNCIANTE'
                      and u.desativado_em is null
                      and u.excluido_em is null
                      and (:usuarioId is null or a.usuario_id = :usuarioId)
                      and (
                        :categoria is null
                        or (
                          :categoria = 'VENDA_DE_CONTEUDO'
                          and exists (
                            select 1
                            from anuncio_servicos av
                            where av.anuncio_id = a.id
                              and av.servico = 'VIDEOCHAMADA'
                          )
                        )
                        or (
                          :categoria <> 'VENDA_DE_CONTEUDO'
                          and a.categoria = :categoria
                          and a.atendimento_exclusivamente_virtual = false
                        )
                      )
                      and (
                        :busca is null
                        or exists (
                          select 1
                          from documento_busca_anuncio d
                          where d.anuncio_id = a.id
                            and lower(translate(coalesce(d.texto_busca, ''),
                                  'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
                                  'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'))
                                like ('%' || :busca || '%') escape '\\'
                        )
                      )
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
                    join usuario u on u.id = a.usuario_id
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and u.status = 'ATIVO'
                      and u.tipo_conta = 'ANUNCIANTE'
                      and u.desativado_em is null
                      and u.excluido_em is null
                      and (:usuarioId is null or a.usuario_id = :usuarioId)
                      and (
                        :categoria is null
                        or (
                          :categoria = 'VENDA_DE_CONTEUDO'
                          and exists (
                            select 1
                            from anuncio_servicos av
                            where av.anuncio_id = a.id
                              and av.servico = 'VIDEOCHAMADA'
                          )
                        )
                        or (
                          :categoria <> 'VENDA_DE_CONTEUDO'
                          and a.categoria = :categoria
                          and a.atendimento_exclusivamente_virtual = false
                        )
                      )
                      and (
                        :busca is null
                        or exists (
                          select 1
                          from documento_busca_anuncio d
                          where d.anuncio_id = a.id
                            and lower(translate(coalesce(d.texto_busca, ''),
                                  'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
                                  'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'))
                                like ('%' || :busca || '%') escape '\\'
                        )
                      )
                    """,
            nativeQuery = true)
    Page<AnuncioEntity> findPublicosOrdenados(
            @Param("categoria") String categoria,
            @Param("busca") String busca,
            @Param("usuarioId") UUID usuarioId,
            @Param("agora") OffsetDateTime agora,
            @Param("seed") long seed,
            Pageable pageable);

    @Query(
            value = """
                    select a.*
                    from anuncio a
                    join usuario u on u.id = a.usuario_id
                    join anuncio_localizacao l on l.anuncio_id = a.id
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and u.status = 'ATIVO'
                      and u.tipo_conta = 'ANUNCIANTE'
                      and u.desativado_em is null
                      and u.excluido_em is null
                      and a.atendimento_exclusivamente_virtual = false
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
                    join usuario u on u.id = a.usuario_id
                    join anuncio_localizacao l on l.anuncio_id = a.id
                    where a.status = 'PUBLICADO'
                      and a.status_moderacao = 'APROVADO'
                      and a.removido_em is null
                      and u.status = 'ATIVO'
                      and u.tipo_conta = 'ANUNCIANTE'
                      and u.desativado_em is null
                      and u.excluido_em is null
                      and a.atendimento_exclusivamente_virtual = false
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

    @Query(value = """
            select a.*
            from anuncio a
            join anuncio_localizacao l on l.anuncio_id = a.id
            join usuario u on u.id = a.usuario_id
            where a.id <> :anuncioAtualId
              and a.categoria = :categoria
              and a.status = 'PUBLICADO'
              and a.status_moderacao = 'APROVADO'
              and a.publicado_em is not null
              and a.removido_em is null
              and a.slug is not null
              and btrim(a.slug) <> ''
              and a.slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
              and u.status = 'ATIVO'
              and u.tipo_conta = 'ANUNCIANTE'
              and u.desativado_em is null
              and u.excluido_em is null
              and (
                (:mesmaCidade = true and l.cidade_id = :cidadeId)
                or (:mesmaCidade = false and l.cidade_id <> :cidadeId)
              )
              and not exists (
                select 1
                from anuncio_bloqueio_juridico bloqueio
                where bloqueio.anuncio_id = a.id
                  and bloqueio.anuncio_desbloqueado_em is null
              )
              and exists (
                select 1
                from anuncio_midia am
                join arquivo_midia arquivo on arquivo.id = am.arquivo_midia_id
                where am.anuncio_id = a.id
                  and am.tipo = 'FOTO'
                  and am.finalidade <> 'STORY'
                  and am.status = 'PUBLICAVEL'
                  and am.visibilidade_midia is not null
                  and arquivo.status_arquivo = 'VALIDADO'
              )
              and exists (
                select 1
                from ativacao_beneficio ab
                join beneficio_premium bp on bp.id = ab.beneficio_id
                join grupo_ativacao_beneficio gb on gb.id = ab.grupo_ativacao_id
                where ab.anuncio_id = a.id
                  and ab.usuario_id = a.usuario_id
                  and gb.anuncio_id = a.id
                  and gb.usuario_id = a.usuario_id
                  and ab.origem = gb.origem
                  and bp.ativo = true
                  and bp.escopo = 'ANUNCIO'
                  and ab.status = 'ATIVA'
                  and ab.revogada_em is null
                  and ab.inicio_em <= :agora
                  and ab.fim_em > :agora
                  and gb.status = 'ATIVO'
                  and gb.validade_inicio_em <= :agora
                  and gb.validade_fim_em > :agora
              )
            order by
              case when exists (
                select 1
                from ativacao_beneficio topo
                join beneficio_premium beneficio_topo on beneficio_topo.id = topo.beneficio_id
                join grupo_ativacao_beneficio grupo_topo on grupo_topo.id = topo.grupo_ativacao_id
                where topo.anuncio_id = a.id
                  and beneficio_topo.codigo = 'ANUNCIO_TOPO'
                  and beneficio_topo.ativo = true
                  and beneficio_topo.afeta_ranking = true
                  and topo.status = 'ATIVA'
                  and topo.revogada_em is null
                  and topo.inicio_em <= :agora
                  and topo.fim_em > :agora
                  and grupo_topo.status = 'ATIVO'
                  and grupo_topo.validade_inicio_em <= :agora
                  and grupo_topo.validade_fim_em > :agora
              ) then 0 else 1 end,
              a.publicado_em desc,
              a.id
            """, nativeQuery = true)
    List<AnuncioEntity> findRelacionadosComBeneficioVigente(
            @Param("anuncioAtualId") UUID anuncioAtualId,
            @Param("categoria") String categoria,
            @Param("cidadeId") UUID cidadeId,
            @Param("mesmaCidade") boolean mesmaCidade,
            @Param("agora") OffsetDateTime agora,
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

    interface UsuarioPublicoProjection {
        UUID getUsuarioId();

        String getDisplayUsername();
    }
}
