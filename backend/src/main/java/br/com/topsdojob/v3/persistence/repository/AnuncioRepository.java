package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioRepository extends JpaRepository<AnuncioEntity, UUID> {
    Optional<AnuncioEntity> findBySlugAndStatusAndStatusModeracaoAndClassificacaoConteudoAndRemovidoEmIsNull(
            String slug,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            ClassificacaoConteudo classificacaoConteudo);

    Optional<AnuncioEntity> findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
            String slug,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao);

    Page<AnuncioEntity> findByIdInAndStatusAndStatusModeracaoAndClassificacaoConteudoAndRemovidoEmIsNull(
            Collection<UUID> ids,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            ClassificacaoConteudo classificacaoConteudo,
            Pageable pageable);
}
