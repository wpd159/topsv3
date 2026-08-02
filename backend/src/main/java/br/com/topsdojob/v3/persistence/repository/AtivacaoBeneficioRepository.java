package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtivacaoBeneficioRepository extends JpaRepository<AtivacaoBeneficioEntity, UUID> {
    List<AtivacaoBeneficioEntity> findByAnuncioId(UUID anuncioId);

    List<AtivacaoBeneficioEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    List<AtivacaoBeneficioEntity> findByGrupoAtivacaoIdIn(Collection<UUID> grupoAtivacaoIds);

    List<AtivacaoBeneficioEntity> findByFimEmBetween(OffsetDateTime inicio, OffsetDateTime fim);

    List<AtivacaoBeneficioEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

    List<AtivacaoBeneficioEntity> findByGrupoAtivacaoId(UUID grupoAtivacaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ativacao from AtivacaoBeneficioEntity ativacao where ativacao.id = :id")
    java.util.Optional<AtivacaoBeneficioEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ativacao
            from AtivacaoBeneficioEntity ativacao
            where ativacao.anuncioId = :anuncioId
              and ativacao.usuarioId = :usuarioId
              and ativacao.status = :status
              and ativacao.revogadaEm is null
              and ativacao.inicioEm <= :agora
              and ativacao.fimEm > :agora
              and ativacao.beneficioId in (
                select beneficio.id
                from BeneficioPremiumEntity beneficio
                where beneficio.codigo = :codigo
              )
              and not exists (
                select story.id
                from StoryAnuncioEntity story
                where story.ativacaoBeneficioId = ativacao.id
              )
            order by ativacao.fimEm, ativacao.id
            """)
    List<AtivacaoBeneficioEntity> findVigentesByCodigoForUpdate(
            @Param("anuncioId") UUID anuncioId,
            @Param("usuarioId") UUID usuarioId,
            @Param("codigo") String codigo,
            @Param("status") StatusAtivacaoBeneficio status,
            @Param("agora") OffsetDateTime agora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ativacao
            from AtivacaoBeneficioEntity ativacao
            where ativacao.anuncioId = :anuncioId
              and ativacao.usuarioId = :usuarioId
              and ativacao.status = :status
              and ativacao.revogadaEm is null
              and ativacao.inicioEm is null
              and ativacao.fimEm is null
              and ativacao.beneficioId in (
                select beneficio.id
                from BeneficioPremiumEntity beneficio
                where beneficio.codigo = :codigo
              )
              and not exists (
                select story.id
                from StoryAnuncioEntity story
                where story.ativacaoBeneficioId = ativacao.id
              )
            order by ativacao.criadoEm, ativacao.id
            """)
    List<AtivacaoBeneficioEntity> findAguardandoUsoByCodigoForUpdate(
            @Param("anuncioId") UUID anuncioId,
            @Param("usuarioId") UUID usuarioId,
            @Param("codigo") String codigo,
            @Param("status") StatusAtivacaoBeneficio status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ativacao
            from AtivacaoBeneficioEntity ativacao
            where ativacao.anuncioId = :anuncioId
              and ativacao.beneficioId = :beneficioId
              and ativacao.status = :status
            order by ativacao.criadoEm, ativacao.id
            """)
    List<AtivacaoBeneficioEntity> findAguardandoModeracaoForUpdate(
            @Param("anuncioId") UUID anuncioId,
            @Param("beneficioId") UUID beneficioId,
            @Param("status") StatusAtivacaoBeneficio status);

    @Query(value = """
            select ab.anuncio_id as "anuncioId", count(*) as "totalBeneficios"
            from ativacao_beneficio ab
            join anuncio a on a.id = ab.anuncio_id
            join beneficio_premium bp on bp.id = ab.beneficio_id
            join grupo_ativacao_beneficio gb on gb.id = ab.grupo_ativacao_id
            where a.usuario_id = :usuarioId
              and a.removido_em is null
              and ab.usuario_id = a.usuario_id
              and gb.usuario_id = a.usuario_id
              and gb.anuncio_id = a.id
              and gb.origem = ab.origem
              and bp.escopo = 'ANUNCIO'
              and bp.ativo = true
              and ab.status = 'ATIVA'
              and ab.revogada_em is null
              and ab.inicio_em <= :agora
              and ab.fim_em > :agora
              and ab.inicio_em >= gb.validade_inicio_em
              and ab.fim_em <= gb.validade_fim_em
              and gb.status = 'ATIVO'
              and gb.validade_inicio_em <= :agora
              and gb.validade_fim_em > :agora
            group by ab.anuncio_id
            """, nativeQuery = true)
    List<ContagemPremiumVigenteProjection> countVigentesPorUsuario(
            @Param("usuarioId") UUID usuarioId,
            @Param("agora") OffsetDateTime agora);

    @Query(value = """
            select count(*)
            from ativacao_beneficio ab
            join anuncio a on a.id = ab.anuncio_id
            join beneficio_premium bp on bp.id = ab.beneficio_id
            join grupo_ativacao_beneficio gb on gb.id = ab.grupo_ativacao_id
            where a.removido_em is null
              and ab.usuario_id = a.usuario_id
              and gb.usuario_id = a.usuario_id
              and gb.anuncio_id = a.id
              and gb.origem = ab.origem
              and bp.escopo = 'ANUNCIO'
              and bp.ativo = true
              and ab.status = 'ATIVA'
              and ab.revogada_em is null
              and ab.inicio_em <= :agora
              and ab.fim_em > :agora
              and ab.inicio_em >= gb.validade_inicio_em
              and ab.fim_em <= gb.validade_fim_em
              and gb.status = 'ATIVO'
              and gb.validade_inicio_em <= :agora
              and gb.validade_fim_em > :agora
            """, nativeQuery = true)
    long countVigentes(@Param("agora") OffsetDateTime agora);

    interface ContagemPremiumVigenteProjection {
        UUID getAnuncioId();

        long getTotalBeneficios();
    }
}
