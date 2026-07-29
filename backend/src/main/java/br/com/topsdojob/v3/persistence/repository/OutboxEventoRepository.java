package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OutboxEventoRepository
        extends JpaRepository<OutboxEventoEntity, UUID>, JpaSpecificationExecutor<OutboxEventoEntity> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByTipoEventoAndIdempotencyKeyAndStatus(
            String tipoEvento,
            String idempotencyKey,
            StatusOutbox status);

    @Query(value = """
        select evento.*
        from outbox_evento evento
        where evento.status = :status
          and evento.tipo_evento in (:types)
          and (evento.proxima_tentativa_em is null or evento.proxima_tentativa_em <= :now)
          and evento.payload_json ->> 'communicationVersion' = '1'
        order by evento.criado_em asc, evento.id asc
        limit 1
        for update skip locked
        """, nativeQuery = true)
    List<OutboxEventoEntity> lockNextEmail(
        @Param("status") String status,
        @Param("types") List<String> types,
        @Param("now") OffsetDateTime now);
}
