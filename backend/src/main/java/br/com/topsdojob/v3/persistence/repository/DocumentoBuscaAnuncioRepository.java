package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.DocumentoBuscaAnuncioEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoBuscaAnuncioRepository extends JpaRepository<DocumentoBuscaAnuncioEntity, UUID> {
}
