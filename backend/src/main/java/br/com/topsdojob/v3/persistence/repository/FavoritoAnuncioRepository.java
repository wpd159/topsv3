package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.FavoritoAnuncioEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoritoAnuncioRepository extends JpaRepository<FavoritoAnuncioEntity, UUID> {

    List<FavoritoAnuncioEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

    boolean existsByUsuarioIdAndAnuncioId(UUID usuarioId, UUID anuncioId);

    Optional<FavoritoAnuncioEntity> findByUsuarioIdAndAnuncioId(UUID usuarioId, UUID anuncioId);
}
