package br.com.topsdojob.v3.persistence.repository.blog;

import br.com.topsdojob.v3.persistence.entity.blog.BlogCategoriaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogCategoriaRepository extends JpaRepository<BlogCategoriaEntity, UUID> {
  List<BlogCategoriaEntity> findAllByOrderByOrdemAscNomeAsc();
  List<BlogCategoriaEntity> findAllByAtivaTrueOrderByOrdemAscNomeAsc();
  Optional<BlogCategoriaEntity> findBySlug(String slug);
  boolean existsBySlugAndIdNot(String slug, UUID id);
  boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
