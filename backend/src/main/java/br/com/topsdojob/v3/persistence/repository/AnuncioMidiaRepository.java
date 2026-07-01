package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioMidiaRepository extends JpaRepository<AnuncioMidiaEntity, UUID> {
    List<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId);
}
