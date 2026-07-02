package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OutboxEventoRepository
        extends JpaRepository<OutboxEventoEntity, UUID>, JpaSpecificationExecutor<OutboxEventoEntity> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByTipoEventoAndIdempotencyKeyAndStatus(
            String tipoEvento,
            String idempotencyKey,
            StatusOutbox status);
}
