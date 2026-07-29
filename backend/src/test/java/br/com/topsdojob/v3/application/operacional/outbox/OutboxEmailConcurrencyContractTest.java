package br.com.topsdojob.v3.application.operacional.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class OutboxEmailConcurrencyContractTest {
  @Test
  void claimUsaJsonbELockConcorrenteNoPostgresql() throws Exception {
    Method method = OutboxEventoRepository.class.getMethod(
        "lockNextEmail",
        String.class,
        List.class,
        OffsetDateTime.class);

    Query query = method.getAnnotation(Query.class);

    assertThat(query.nativeQuery()).isTrue();
    assertThat(query.value())
        .contains("evento.status = :status")
        .contains("evento.tipo_evento in (:types)")
        .contains("evento.proxima_tentativa_em")
        .contains("evento.payload_json ->> 'communicationVersion' = '1'")
        .contains("order by evento.criado_em asc")
        .contains("for update skip locked");
  }

  @Test
  void schemaPreservaIdempotenciaUnicaSemNovaMigration() throws Exception {
    String migration = Files.readString(Path.of(
        "src/main/resources/db/migration/V013__auditoria_outbox.sql"));

    assertThat(migration)
        .contains("CREATE UNIQUE INDEX")
        .contains("outbox_evento")
        .contains("idempotency_key")
        .contains("proxima_tentativa_em")
        .contains("tentativas");
  }
}
