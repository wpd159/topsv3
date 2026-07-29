package br.com.topsdojob.v3.persistence.repository.admin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminPlanoCreditoConsultaRepository {

    private final JdbcClient jdbc;

    public AdminPlanoCreditoConsultaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, Long> comprasConfirmadas(Collection<UUID> planoIds) {
        if (planoIds == null || planoIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                SELECT plano_credito_id, COUNT(*) AS total
                FROM pagamento
                WHERE plano_credito_id IN (:planoIds)
                  AND status_interno = 'APROVADO'
                GROUP BY plano_credito_id
                """)
                .param("planoIds", planoIds)
                .query((resultSet, rowNum) -> new CompraConfirmada(
                        resultSet.getObject("plano_credito_id", UUID.class),
                        resultSet.getLong("total")))
                .list()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        CompraConfirmada::planoId,
                        CompraConfirmada::total));
    }

    private record CompraConfirmada(UUID planoId, long total) {
    }
}
