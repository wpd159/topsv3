package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnuncioMidiaRepository
        extends JpaRepository<AnuncioMidiaEntity, UUID>, JpaSpecificationExecutor<AnuncioMidiaEntity> {
    long countByStatus(StatusAnuncioMidia status);

    long countByClassificacaoConteudo(ClassificacaoConteudo classificacaoConteudo);

    long countByAnuncioId(UUID anuncioId);

    List<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId);

    Page<AnuncioMidiaEntity> findByAnuncioId(UUID anuncioId, Pageable pageable);

    long count(Specification<AnuncioMidiaEntity> spec);
}
