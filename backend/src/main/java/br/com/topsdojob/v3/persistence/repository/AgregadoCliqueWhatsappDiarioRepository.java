package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoCliqueWhatsappDiarioEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgregadoCliqueWhatsappDiarioRepository extends JpaRepository<AgregadoCliqueWhatsappDiarioEntity, UUID> {
    List<AgregadoCliqueWhatsappDiarioEntity> findByAnuncioId(UUID anuncioId);

    List<AgregadoCliqueWhatsappDiarioEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    @Query("""
            select coalesce(sum(a.totalCliques), 0)
            from AgregadoCliqueWhatsappDiarioEntity a
            where a.dataReferencia = :dataReferencia
            """)
    long somarPorData(@Param("dataReferencia") LocalDate dataReferencia);
}
