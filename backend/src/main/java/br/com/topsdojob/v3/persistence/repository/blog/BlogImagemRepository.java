package br.com.topsdojob.v3.persistence.repository.blog;

import br.com.topsdojob.v3.persistence.entity.blog.BlogImagemEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogImagemRepository extends JpaRepository<BlogImagemEntity, UUID> {
}
