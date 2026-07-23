package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioStatusHistoricoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioStatusHistoricoRepository
    extends JpaRepository<AnuncioStatusHistoricoEntity, UUID> {
}
