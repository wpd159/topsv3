package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnuncioLocalizacaoRepository extends JpaRepository<AnuncioLocalizacaoEntity, UUID> {
    Optional<AnuncioLocalizacaoEntity> findByAnuncioId(UUID anuncioId);

    List<AnuncioLocalizacaoEntity> findByEstadoId(UUID estadoId);

    List<AnuncioLocalizacaoEntity> findByCidadeId(UUID cidadeId);

    List<AnuncioLocalizacaoEntity> findByCidadeIdAndBairroId(UUID cidadeId, UUID bairroId);

    List<AnuncioLocalizacaoEntity> findByBairroId(UUID bairroId);

    List<AnuncioLocalizacaoEntity> findByAnuncioIdIn(Collection<UUID> anuncioIds);

    @Query(value = """
            select distinct
              trim(e.uf) as uf,
              e.nome as estado,
              c.nome as cidade,
              c.slug as "cidadeSlug",
              b.nome as bairro,
              b.slug as "bairroSlug"
            from anuncio_localizacao al
            join anuncio a on a.id = al.anuncio_id and a.removido_em is null
            join estado e on e.id = al.estado_id
            join cidade c on c.id = al.cidade_id
            left join bairro b on b.id = al.bairro_id
            order by e.nome, c.nome, b.nome nulls first
            """, nativeQuery = true)
    List<LocalidadeFiltroProjection> findLocalidadesDaFilaAdministrativa();

    interface LocalidadeFiltroProjection {
        String getUf();

        String getEstado();

        String getCidade();

        String getCidadeSlug();

        String getBairro();

        String getBairroSlug();
    }
}
