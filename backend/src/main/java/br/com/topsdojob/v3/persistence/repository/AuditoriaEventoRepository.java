package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEventoEntity, UUID> {
}
