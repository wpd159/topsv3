package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioLocalizacaoRepository extends JpaRepository<AnuncioLocalizacaoEntity, UUID> {
    Optional<AnuncioLocalizacaoEntity> findByAnuncioId(UUID anuncioId);

    List<AnuncioLocalizacaoEntity> findByCidadeId(UUID cidadeId);

    List<AnuncioLocalizacaoEntity> findByCidadeIdAndBairroId(UUID cidadeId, UUID bairroId);
}
