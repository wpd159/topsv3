package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficioPremiumRepository extends JpaRepository<BeneficioPremiumEntity, UUID> {
    List<BeneficioPremiumEntity> findByIdIn(Collection<UUID> ids);

    Optional<BeneficioPremiumEntity> findByCodigo(String codigo);

    @Modifying
    @Query(value = """
            INSERT INTO beneficio_premium (
              id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
              ordem_exibicao, criado_em, atualizado_em
            ) VALUES (
              :id, :codigo, :nome, :descricao, :escopo, :afetaRanking, :ativo,
              :ordemExibicao, :agora, :agora
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirCatalogoSeAusente(
            @Param("id") UUID id,
            @Param("codigo") String codigo,
            @Param("nome") String nome,
            @Param("descricao") String descricao,
            @Param("escopo") String escopo,
            @Param("afetaRanking") boolean afetaRanking,
            @Param("ativo") boolean ativo,
            @Param("ordemExibicao") int ordemExibicao,
            @Param("agora") OffsetDateTime agora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select beneficio from BeneficioPremiumEntity beneficio where beneficio.id = :id")
    Optional<BeneficioPremiumEntity> findByIdForUpdate(@Param("id") UUID id);

    List<BeneficioPremiumEntity> findAllByOrderByOrdemExibicaoAscCodigoAsc();
}
