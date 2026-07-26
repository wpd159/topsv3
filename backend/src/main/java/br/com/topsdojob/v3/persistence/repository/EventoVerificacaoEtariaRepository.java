package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoVerificacaoEtariaRepository
    extends JpaRepository<EventoVerificacaoEtariaEntity, UUID> {

  Page<EventoVerificacaoEtariaEntity> findAllByOrderByCriadoEmDesc(Pageable pageable);
}
