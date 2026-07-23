package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CliqueWhatsappRepository extends JpaRepository<CliqueWhatsappEntity, UUID> {
    long countByPermitidoTrue();

    long countByPermitidoFalse();

    long countByAnuncioIdAndPermitidoTrue(UUID anuncioId);

    @Query("""
            select c.anuncioId as anuncioId, count(c) as totalCliques
            from CliqueWhatsappEntity c
            where c.anuncioId in :anuncioIds
              and c.permitido = true
            group by c.anuncioId
            """)
    List<ContagemPorAnuncioProjection> countPermitidosPorAnuncioIdIn(
            @Param("anuncioIds") java.util.Collection<UUID> anuncioIds);

    List<CliqueWhatsappEntity> findByAnuncioId(UUID anuncioId);

    @Query(value = """
            select c.anuncio_id as "anuncioId", count(*) as "totalCliques"
            from clique_whatsapp c
            join anuncio a on a.id = c.anuncio_id
            where a.usuario_id = :usuarioId
              and a.removido_em is null
              and c.permitido = true
            group by c.anuncio_id
            """, nativeQuery = true)
    List<ContagemPorAnuncioProjection> countPermitidosPorUsuario(
            @Param("usuarioId") UUID usuarioId);

    @Query(value = """
            select (c.criado_em at time zone 'America/Sao_Paulo')::date as "dataReferencia",
                   count(*) as "totalCliques"
            from clique_whatsapp c
            join anuncio a on a.id = c.anuncio_id
            where a.usuario_id = :usuarioId
              and a.removido_em is null
              and c.permitido = true
              and c.criado_em >= :inicio
              and c.criado_em < :fimExclusivo
            group by (c.criado_em at time zone 'America/Sao_Paulo')::date
            order by (c.criado_em at time zone 'America/Sao_Paulo')::date
            """, nativeQuery = true)
    List<ContagemDiariaProjection> countPermitidosDiariosPorUsuario(
            @Param("usuarioId") UUID usuarioId,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fimExclusivo") OffsetDateTime fimExclusivo);

    interface ContagemPorAnuncioProjection {
        UUID getAnuncioId();

        long getTotalCliques();
    }

    interface ContagemDiariaProjection {
        LocalDate getDataReferencia();

        long getTotalCliques();
    }
}
