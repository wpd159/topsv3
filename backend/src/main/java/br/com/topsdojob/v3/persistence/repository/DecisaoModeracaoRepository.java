package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.moderacao.DecisaoModeracaoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisaoModeracaoRepository extends JpaRepository<DecisaoModeracaoEntity, UUID> {
    boolean existsByRevisaoAnuncioId(UUID revisaoAnuncioId);

    long countByRevisaoAnuncioId(UUID revisaoAnuncioId);
}
