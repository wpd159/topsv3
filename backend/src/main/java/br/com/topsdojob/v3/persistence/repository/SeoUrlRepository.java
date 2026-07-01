package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.seo.SeoUrlEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeoUrlRepository extends JpaRepository<SeoUrlEntity, UUID> {
    Optional<SeoUrlEntity> findByCaminhoPublico(String caminhoPublico);
}
