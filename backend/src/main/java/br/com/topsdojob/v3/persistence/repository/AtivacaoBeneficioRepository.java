package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtivacaoBeneficioRepository extends JpaRepository<AtivacaoBeneficioEntity, UUID> {
    List<AtivacaoBeneficioEntity> findByAnuncioId(UUID anuncioId);

    List<AtivacaoBeneficioEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    List<AtivacaoBeneficioEntity> findByGrupoAtivacaoIdIn(Collection<UUID> grupoAtivacaoIds);

    List<AtivacaoBeneficioEntity> findByFimEmBetween(OffsetDateTime inicio, OffsetDateTime fim);
}
