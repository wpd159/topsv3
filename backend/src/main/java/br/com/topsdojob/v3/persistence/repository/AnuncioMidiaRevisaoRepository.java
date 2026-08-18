package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.moderacao.AnuncioMidiaRevisaoEntity;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioMidiaRevisaoRepository
    extends JpaRepository<AnuncioMidiaRevisaoEntity, UUID> {

  boolean existsByArquivoMidiaIdIn(Collection<UUID> arquivoMidiaIds);

  boolean existsByAnuncioMidiaIdIn(Collection<UUID> anuncioMidiaIds);
}
