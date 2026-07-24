package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.moderacao.DecisaoModeracaoEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisaoModeracaoRepository extends JpaRepository<DecisaoModeracaoEntity, UUID> {
    boolean existsByRevisaoAnuncioId(UUID revisaoAnuncioId);

    long countByRevisaoAnuncioId(UUID revisaoAnuncioId);

    @Query(value = """
            select
                revisao.anuncio_id as anuncioId,
                decisao.motivo as motivo,
                decisao.criado_em as decididoEm
            from decisao_moderacao decisao
            join revisao_anuncio revisao on revisao.id = decisao.revisao_anuncio_id
            where revisao.anuncio_id in (:anuncioIds)
              and decisao.decisao = 'REJEITAR'
            order by decisao.criado_em desc
            """, nativeQuery = true)
    List<ReprovacaoPorAnuncioProjection> findReprovacoesByAnuncioIdIn(
            @Param("anuncioIds") Collection<UUID> anuncioIds);

    interface ReprovacaoPorAnuncioProjection {
        UUID getAnuncioId();

        String getMotivo();

        OffsetDateTime getDecididoEm();
    }
}
