package br.com.topsdojob.v3.persistence.repository.blog;

import br.com.topsdojob.v3.persistence.entity.blog.BlogPostEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogPostRepository extends JpaRepository<BlogPostEntity, UUID> {

  @Query("""
      select p from BlogPostEntity p
      where (:status is null or p.status = :status)
        and (:termo is null
          or lower(p.titulo) like lower(concat('%', :termo, '%'))
          or lower(p.slug) like lower(concat('%', :termo, '%'))
          or lower(p.autorNome) like lower(concat('%', :termo, '%')))
      order by p.atualizadoEm desc, p.id
      """)
  List<BlogPostEntity> buscarAdmin(@Param("termo") String termo, @Param("status") String status);

  List<BlogPostEntity> findAllByOrderByAtualizadoEmDescIdAsc();

  List<BlogPostEntity> findAllByStatusOrderByAtualizadoEmDescIdAsc(String status);

  List<BlogPostEntity> findAllByStatusOrderByPublicadoEmDescIdAsc(String status);

  List<BlogPostEntity> findAllByStatusAndCategoriaIdOrderByPublicadoEmDescIdAsc(
      String status, UUID categoriaId);

  Optional<BlogPostEntity> findBySlugAndStatus(String slug, String status);
  Optional<BlogPostEntity> findByCriadoPorUsuarioIdAndCriadoRequestId(UUID atorId, String requestId);
  boolean existsBySlugAndIdNot(String slug, UUID id);
  long countByCategoriaIdAndStatus(UUID categoriaId, String status);
  boolean existsByCategoriaId(UUID categoriaId);
  boolean existsByImagemCapaId(UUID imagemId);
  boolean existsByImagemOgId(UUID imagemId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from BlogPostEntity p where p.id = :id")
  Optional<BlogPostEntity> findByIdForUpdate(@Param("id") UUID id);
}
