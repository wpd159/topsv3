package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
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
}
