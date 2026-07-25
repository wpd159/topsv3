package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.seo.SeoConteudoPaginaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusSeoConteudo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeoConteudoPaginaRepository extends JpaRepository<SeoConteudoPaginaEntity, UUID> {

  List<SeoConteudoPaginaEntity> findAllByStatusIn(Collection<StatusSeoConteudo> statuses);

  Optional<SeoConteudoPaginaEntity> findTopBySeoUrlIdAndChaveOrderByVersaoDesc(
      UUID seoUrlId,
      String chave);
}
